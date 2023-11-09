// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.rabbitmq.session.type;

import com.google.common.flogger.FluentLogger;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Exchange;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Message;
import com.googlesource.gerrit.plugins.rabbitmq.session.SubscriberSession;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public final class AMQPSubscriberSession extends AMQPSession implements SubscriberSession {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private volatile List<Channel> channels = Collections.synchronizedList(new ArrayList<>());

  public AMQPSubscriberSession(Properties properties) {
    super(properties);
  }

  protected Channel createChannel() {
    Channel channel = super.createChannel();
    if (channel != null && channel.isOpen()) {
      channels.add(channel);
    }
    return channel;
  }

  @Override
  public void disconnect() {
    logger.atInfo().log("Disconnecting subscriber session...");
    synchronized (channels) {
      Iterator<Channel> it = channels.iterator();
      while (it.hasNext()) {
        Channel channel = it.next();
        synchronized (channel) {
          try {
            logger.atInfo().log("Closing Channel #%d...", channel.getChannelNumber());
            channel.close();
          } catch (IOException | TimeoutException ex) {
            logger.atSevere().withCause(ex).log(
                "Error when closing channel %d.", channel.getChannelNumber());
          } finally {
            it.remove();
          }
        }
      }
    }
    super.disconnect();
  }

  @Override
  public boolean subscribe(String topic, Consumer<String> messageBodyConsumer) {
    Channel channel = createChannel();
    if (channel != null) {
      synchronized (channel) {
        if (channel.isOpen()) {
          String exchangeName = properties.getSection(Exchange.class).name;
          try {
            String queueName;
            Message message = properties.getSection(Message.class);
            if (!message.queuePrefix.isEmpty()) {
              queueName = message.queuePrefix + "." + topic;
              channel.queueDeclare(
                  queueName, message.durable, message.exclusive, message.autoDelete, null);
            } else {
              queueName = channel.queueDeclare().getQueue();
            }
            channel.queueBind(queueName, exchangeName, topic);

            channel.basicConsume(
                queueName,
                true,
                (consumerTag, delivery) -> {
                  messageBodyConsumer.accept(new String(delivery.getBody(), "UTF-8"));
                },
                (consumerTag, sig) -> {
                  if (sig.isInitiatedByApplication()) {
                    logger.atInfo().withCause(sig).log(
                        "Channel used by consumer on queue %s got shutdown signal due to an explicit application action. Will not try to subscribe on topic %s again",
                        queueName, topic);
                  } else if (!sig.isHardError()) {
                    logger.atWarning().withCause(sig).log(
                        "Channel used by consumer on queue %s got shutdown signal due to a channel error. Will try to subscribe on topic %s again",
                        queueName, topic);
                    if (!subscribe(topic, messageBodyConsumer)) {
                      logger.atSevere().log("Failed to resubscribe on topic %s", topic);
                    } else {
                      channels.remove(channel);
                    }
                  } else {
                    logger.atWarning().withCause(sig).log(
                        "Channel used by consumer on queue %s got shutdown signal due to a connection error. Will not try to subscribe on topic %s again because the client rabbitmq library should be able to recover from this by itself",
                        queueName, topic);
                  }
                });
            logger.atInfo().log("Subscribed to queue with name %s", queueName);
            return true;
          } catch (IOException ex) {
            logger.atSevere().withCause(ex).log("Error when subscribing to topic.");
            return false;
          }
        }
      }
    }
    logger.atSevere().log("Cannot open channel for subscribing.");
    return false;
  }
}
