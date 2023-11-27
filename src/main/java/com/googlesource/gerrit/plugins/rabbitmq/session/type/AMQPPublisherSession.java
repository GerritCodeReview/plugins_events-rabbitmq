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

import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Exchange;
import com.googlesource.gerrit.plugins.rabbitmq.session.PublisherSession;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.apache.commons.codec.CharEncoding;

public final class AMQPPublisherSession extends AMQPSession implements PublisherSession {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private volatile Channel channel;
  private ConfirmListener confirmListener;

  public AMQPPublisherSession(Properties properties) {
    super(properties);
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

  protected Channel createChannel() {
    Channel ch = super.createChannel();

    if (confirmListener != null) {
      int channelId = ch.getChannelNumber();
      try {
        ch.confirmSelect();
        ch.addConfirmListener(confirmListener);
        logger.atInfo().log("Enabled publishConfirms on channel %d", channelId);
      } catch (IOException ex) {
        logger.atSevere().withCause(ex).log(
            "Failed to enable publishConfirms on channel %d.", channelId);
      }
    }
    return ch;
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
  public void setConfirmListener(ConfirmListener confirmListener) {
    requireNonNull(confirmListener, "Confirm Listener is not allowed to be null");
    this.confirmListener = confirmListener;
  }

  @Override
  public synchronized Long getNextPublishSeqNo() {
    if (makeSureChannelIsOpened()) {
      return channel.getNextPublishSeqNo();
    }
    logger.atSevere().log("Cannot open channel for getting sequence number.");
    return null;
  }

  @Override
  public synchronized void disconnect() {
    logger.atInfo().log("Disconnecting publisher session...");
    try {
      if (channel != null) {
        logger.atInfo().log("Closing Channel #%d...", channel.getChannelNumber());
        channel.close();
      }
    } catch (IOException | TimeoutException ex) {
      logger.atSevere().withCause(ex).log("Error when closing channel.");
    }
    super.disconnect();
  }
}
