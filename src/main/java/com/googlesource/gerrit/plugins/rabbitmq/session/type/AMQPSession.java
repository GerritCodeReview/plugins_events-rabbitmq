// Copyright (C) 2013 The Android Open Source Project
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

import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.AMQP;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Exchange;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Gerrit;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Monitor;
import com.googlesource.gerrit.plugins.rabbitmq.session.Session;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmCallback;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownSignalException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.commons.codec.CharEncoding;
import org.apache.commons.lang3.StringUtils;

public final class AMQPSession implements Session {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Properties properties;
  private volatile Connection connection;
  private volatile Channel channel;
  private ConfirmCallback ackConsumer;
  private ConfirmCallback nackConsumer;

  private final AtomicInteger failureCount = new AtomicInteger(0);

  public AMQPSession(Properties properties) {
    this.properties = properties;
  }

  @Override
  public boolean isOpen() {
    if (connection != null && connection.isOpen()) {
      return true;
    }
    return false;
  }

  private boolean makeSureChannelIsOpened() {
    if (channelIsOpen()) {
      return true;
    }
    channel = createChannel();
    return channelIsOpen();
  }

  private boolean channelIsOpen() {
    return channel != null && channel.isOpen();
  }

  private Channel createChannel() {
    if (!isOpen()) {
      connect();
    }
    if (isOpen()) {
      AMQP amqp = properties.getSection(AMQP.class);
      try {
        Channel ch = connection.createChannel();
        int channelId = ch.getChannelNumber();
        ch.addShutdownListener(
            cause -> {
              if (cause.isInitiatedByApplication()) {
                logger.atInfo().log("Channel #%d closed by application.", channelId);
              } else {
                logger.atWarning().log(
                    "Channel #%d closed. Cause: %s", channelId, cause.getMessage());
              }
            });
        failureCount.set(0);
        logger.atInfo().log("Channel #%d opened for %s.", channelId, amqp.uri);
        if (ackConsumer != null && nackConsumer != null) {
          ch.confirmSelect();
          logger.atInfo().log("Enabled publishConfirms on channel %d", channelId);
          ch.addConfirmListener(ackConsumer, nackConsumer);
        }
        return ch;
      } catch (IOException | AlreadyClosedException ex) {
        logger.atSevere().withCause(ex).log("Failed to open channel for %s.", amqp.uri);
        failureCount.incrementAndGet();
      }
      if (failureCount.get() > properties.getSection(Monitor.class).failureCount) {
        logger.atWarning().log(
            "Creating channel failed %d times, closing connection %s.",
            failureCount.get(), amqp.uri);
        disconnect();
      }
    }
    return null;
  }

  @Override
  public synchronized boolean connect() {
    AMQP amqp = properties.getSection(AMQP.class);
    if (isOpen()) {
      logger.atInfo().log("Already connected to %s.", amqp.uri);
      return true;
    }
    logger.atInfo().log("Connect to %s...", amqp.uri);
    ConnectionFactory factory = new ConnectionFactory();
    try {
      if (StringUtils.isNotEmpty(amqp.uri)) {
        factory.setUri(amqp.uri);
        if (StringUtils.isNotEmpty(amqp.username)) {
          factory.setUsername(amqp.username);
        }
        Gerrit gerrit = properties.getSection(Gerrit.class);
        String securePassword = gerrit.getAMQPUserPassword(amqp.username);
        if (StringUtils.isNotEmpty(securePassword)) {
          factory.setPassword(securePassword);
        } else if (StringUtils.isNotEmpty(amqp.password)) {
          factory.setPassword(amqp.password);
        }
        connection = factory.newConnection();
        connection.addShutdownListener(
            cause -> {
              if (cause.isInitiatedByApplication()) {
                logger.atInfo().log("Connection closed by application.");
              } else {
                logger.atWarning().log("Connection closed. Cause: %s", cause.getMessage());
              }
            });
        logger.atInfo().log("Connection established to %s.", amqp.uri);
        return true;
      }
    } catch (URISyntaxException ex) {
      logger.atSevere().log("URI syntax error: %s", amqp.uri);
    } catch (IOException | TimeoutException ex) {
      logger.atSevere().withCause(ex).log("Connection to %s cannot be opened.", amqp.uri);
    } catch (KeyManagementException | NoSuchAlgorithmException ex) {
      logger.atSevere().withCause(ex).log(
          "Security error when opening connection to %s.", amqp.uri);
    }
    return false;
  }

  @Override
  public synchronized void disconnect() {
    logger.atInfo().log("Disconnecting...");
    try {
      if (channel != null) {
        logger.atInfo().log("Closing Channel #%d...", channel.getChannelNumber());
        channel.close();
      }
    } catch (IOException | TimeoutException ex) {
      logger.atSevere().withCause(ex).log("Error when closing channel.");
    } finally {
      channel = null;
    }

    try {
      if (connection != null) {
        logger.atInfo().log("Closing Connection...");
        connection.close();
      }
    } catch (IOException | ShutdownSignalException ex) {
      logger.atWarning().withCause(ex).log("Error when closing connection.");
    } finally {
      connection = null;
    }
  }

  @Override
  public synchronized boolean publish(String messageBody, String routingKey) {
    if (makeSureChannelIsOpened()) {
      String exchangeName = properties.getSection(Exchange.class).name;
      try {
        channel.basicPublish(
            exchangeName,
            routingKey,
            properties.getAMQProperties().getBasicProperties(),
            messageBody.getBytes(CharEncoding.UTF_8));
        return true;
      } catch (IOException ex) {
        logger.atSevere().withCause(ex).log("Error when sending message.");
        return false;
      }
    }
    logger.atSevere().log("Cannot open channel for publishing.");
    return false;
  }

  @Override
  public synchronized String addSubscriber(String topic, Consumer<String> messageBodyConsumer) {
    if (makeSureChannelIsOpened()) {
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

        String consumerTag =
            channel.basicConsume(
                queueName,
                true,
                (ct, delivery) -> {
                  messageBodyConsumer.accept(new String(delivery.getBody(), "UTF-8"));
                },
                (ct, sig) -> {
                  if (sig.isInitiatedByApplication()) {
                    logger.atInfo().withCause(sig).log(
                        "Queue: %s, Topic: %s Channel closed by application", queueName, topic);
                  } else if (!sig.isHardError()) {
                    logger.atWarning().withCause(sig).log(
                        "Queue: %s, Topic: %s Channel shutdown due to channel error, reconnecting!",
                        queueName, topic);
                    if (addSubscriber(topic, messageBodyConsumer) == null) {
                      logger.atSevere().log("Failed to resubscribe on topic %s", topic);
                    }
                  } else {
                    logger.atInfo().withCause(sig).log(
                        "Queue: %s, Topic: %s Channel closed due to connection error",
                        queueName, topic);
                  }
                });
        logger.atInfo().log("Subscribed to queue with name %s", queueName);
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
  public synchronized boolean removeSubscriber(String consumerTag) {
    try {
      channel.basicCancel(consumerTag);
      return true;
    } catch (IOException ex) {
      logger.atSevere().withCause(ex).log("Error when removing subscriber");
      return false;
    }
  }

  @Override
  public void setConfirmListener(ConfirmCallback ackConsumer, ConfirmCallback nackConsumer) {
    requireNonNull(ackConsumer, "Confirm callback for ack is not allowed to be null");
    requireNonNull(nackConsumer, "Confirm callback for nack is not allowed to be null");
    this.ackConsumer = ackConsumer;
    this.nackConsumer = nackConsumer;
  }

  @Override
  public synchronized Long getNextPublishSeqNo() {
    if (makeSureChannelIsOpened()) {
      return channel.getNextPublishSeqNo();
    }
    logger.atSevere().log("Cannot open channel for getting sequence number.");
    return null;
  }
}
