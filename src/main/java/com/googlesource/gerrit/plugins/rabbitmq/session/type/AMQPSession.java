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

import com.google.common.flogger.FluentLogger;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.AMQP;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Gerrit;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Monitor;
import com.googlesource.gerrit.plugins.rabbitmq.session.Session;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownSignalException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.StringUtils;

public class AMQPSession implements Session {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private volatile Connection connection;
  private final AtomicInteger failureCount = new AtomicInteger(0);

  protected final Properties properties;

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

  protected Channel createChannel() {
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
        String password = gerrit.getAMQPUserPassword(amqp.username);
        if (StringUtils.isNotEmpty(password)) {
          factory.setPassword(password);
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
  public void disconnect() {
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
}
