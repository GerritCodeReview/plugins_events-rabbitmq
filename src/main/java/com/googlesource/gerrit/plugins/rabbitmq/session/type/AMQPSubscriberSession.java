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
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.AMQP;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Exchange;
import com.googlesource.gerrit.plugins.rabbitmq.session.SubscriberSession;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public final class AMQPSubscriberSession extends AMQPSession implements SubscriberSession {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    SubscriberSession create(Properties properties);
  }

  private volatile Map<String, Channel> channels = new ConcurrentHashMap<>();

  @Inject
  public AMQPSubscriberSession(@Assisted Properties properties) {
    super(properties);
  }

  @Override
  public void disconnect() {
    logger.atInfo().log("Disconnecting subscriber session...");
    synchronized (channels) {
      Iterator<Entry<String, Channel>> it = channels.entrySet().iterator();
      while (it.hasNext()) {
        closeChannel(it.next().getValue());
        it.remove();
      }
    }
    super.disconnect();
  }

  @Override
  public String addSubscriber(String topic, Consumer<String> messageBodyConsumer) {
    Channel channel = createChannel();
    if (channel != null && channel.isOpen()) {
      String exchangeName = properties.getSection(Exchange.class).name;
      try {
        String queueName;
        AMQP amqp = properties.getSection(AMQP.class);
        if (!amqp.queuePrefix.isEmpty()) {
          queueName = amqp.queuePrefix + "." + topic;
          channel.queueDeclare(queueName, amqp.durable, amqp.exclusive, amqp.autoDelete, null);
        } else {
          queueName = channel.queueDeclare().getQueue();
        }
        channel.queueBind(queueName, exchangeName, topic);

        String consumerTag =
            channel.basicConsume(
                queueName,
                true,
                new MessageConsumer(channel, queueName, topic, messageBodyConsumer));
        logger.atInfo().log("Subscribed to queue with name %s", queueName);
        if (consumerTag != null) {
          channels.put(consumerTag, channel);
        } else {
          closeChannel(channel);
        }
        return consumerTag;
      } catch (IOException ex) {
        logger.atSevere().withCause(ex).log("Error when subscribing to topic.");
        return null;
      }
    }
    logger.atSevere().log("Cannot open channel for subscribing.");
    return null;
  }

  @Override
  public boolean removeSubscriber(String consumerTag) {
    Channel channel = channels.remove(consumerTag);
    if (channel == null) {
      return false;
    }
    closeChannel(channel);
    return true;
  }

  private void closeChannel(Channel channel) {
    synchronized (channel) {
      try {
        logger.atInfo().log("Closing Channel #%d...", channel.getChannelNumber());
        channel.close();
      } catch (IOException | TimeoutException ex) {
        logger.atSevere().withCause(ex).log(
            "Error when closing channel %d.", channel.getChannelNumber());
      }
    }
  }

  private class MessageConsumer extends DefaultConsumer {
    private String queueName;
    private String topic;
    private Consumer<String> messageBodyConsumer;

    MessageConsumer(
        Channel channel, String queueName, String topic, Consumer<String> messageBodyConsumer) {
      super(channel);
      this.queueName = queueName;
      this.topic = topic;
      this.messageBodyConsumer = messageBodyConsumer;
    }

    @Override
    public void handleDelivery(
        String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
        throws UnsupportedEncodingException {
      messageBodyConsumer.accept(new String(body, "UTF-8"));
    }

    @Override
    public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
      if (sig.isInitiatedByApplication()) {
        logger.atInfo().withCause(sig).log(
            "Channel used by consumer on queue %s got shutdown signal due to an explicit application action. Will not try to subscribe on topic %s again",
            queueName, topic);
      } else if (!sig.isHardError()) {
        logger.atWarning().withCause(sig).log(
            "Channel used by consumer on queue %s got shutdown signal due to a channel error. Will try to subscribe on topic %s again",
            queueName, topic);
        if (addSubscriber(topic, messageBodyConsumer) == null) {
          logger.atSevere().log("Failed to resubscribe on topic %s", topic);
        } else {
          channels.values().remove(getChannel());
        }
      } else {
        logger.atWarning().withCause(sig).log(
            "Channel used by consumer on queue %s got shutdown signal due to a connection error. Will not try to subscribe on topic %s again because the client rabbitmq library should be able to recover from this by itself",
            queueName, topic);
      }
    }
  }
}
