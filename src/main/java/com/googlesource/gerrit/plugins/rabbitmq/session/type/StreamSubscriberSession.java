// Copyright (C) 2025 The Android Open Source Project
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
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Exchange;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Stream;
import com.googlesource.gerrit.plugins.rabbitmq.session.SubscriberSession;
import com.rabbitmq.client.Channel;
import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.MessageHandler;
import com.rabbitmq.stream.NoOffsetException;
import com.rabbitmq.stream.OffsetSpecification;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class StreamSubscriberSession extends StreamSession implements SubscriberSession {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private volatile Map<String, com.rabbitmq.stream.Consumer> consumers = new ConcurrentHashMap<>();

  @Inject
  public StreamSubscriberSession(@Assisted Properties properties) {
    super(properties);
  }

  @Override
  public void disconnect() {
    logger.atInfo().log("Disconnecting subscriber session...");
    synchronized (consumers) {
      Iterator<Entry<String, com.rabbitmq.stream.Consumer>> it = consumers.entrySet().iterator();
      while (it.hasNext()) {
        it.next().getValue().close();
        it.remove();
      }
    }
    super.disconnect();
  }

  @Override
  public String addSubscriber(String topic, Consumer<String> messageBodyConsumer) {
    Stream prop = properties.getSection(Stream.class);

    if (prop.streamPrefix.isEmpty()) {
      logger.atSevere().log("streamPrefix need to be set to use streams");
      return null;
    }
    String streamName = prop.streamPrefix + "." + topic;

    if (prop.consumerPrefix.isEmpty()) {
      logger.atSevere().log("consumerPrefix need to be set to use streams");
      return null;
    }
    String consumerName = prop.consumerPrefix + "." + topic;

    if (environment == null) {
      if (!connect()) {
        logger.atSevere().log("Failed to connect to rabbitMQ with environment");
        return null;
      }
    }
    environment.streamCreator().stream(streamName).create();

    String exchangeName = properties.getSection(Exchange.class).name;
    AMQPSession session = new AMQPSession(properties);
    Channel channel = session.createChannel();
    try {
      channel.queueBind(streamName, exchangeName, topic);
      channel.close();
    } catch (IOException | TimeoutException ex) {
      logger.atSevere().withCause(ex).log("Failed to bind stream to exchange or close channel");
    }
    session.disconnect();

    com.rabbitmq.stream.Consumer consumer =
        environment.consumerBuilder().stream(streamName)
            .offset(OffsetSpecification.first())
            .name(consumerName)
            .manualTrackingStrategy()
            .builder()
            .messageHandler(new Handler(topic, messageBodyConsumer))
            .build();
    try {
      logger.atInfo().log(
          "Consumer added for topic %s, consuming from stream-offset %d",
          topic, consumer.storedOffset());
    } catch (NoOffsetException ex) {
      logger.atInfo().withCause(ex).log(
          "No offset found for consumer that listens on topic %s, consuming from start of stream.",
          topic);
    }

    String consumerId = UUID.randomUUID().toString();
    consumers.put(consumerId, consumer);
    return consumerId;
  }

  @Override
  public boolean removeSubscriber(String consumerId) {
    com.rabbitmq.stream.Consumer consumer = consumers.remove(consumerId);
    if (consumer == null) {
      return false;
    }
    consumer.close();
    return true;
  }

  private class Handler implements MessageHandler {
    private AtomicInteger messageConsumed;
    private String topic;
    private Consumer<String> messageBodyConsumer;

    Handler(String topic, Consumer<String> messageBodyConsumer) {
      messageConsumed = new AtomicInteger(0);
      this.topic = topic;
      this.messageBodyConsumer = messageBodyConsumer;
    }

    @Override
    public void handle(MessageHandler.Context context, Message message) {
      Stream prop = properties.getSection(Stream.class);
      try {
        logger.atFiner().log(
            "Consume message from topic %s with offset %d", topic, context.offset());
        messageBodyConsumer.accept(new String(message.getBodyAsBinary(), "UTF-8"));
        if (messageConsumed.incrementAndGet() % prop.windowSize == 0) {
          com.rabbitmq.stream.Consumer consumer = context.consumer();
          long newOffset = Math.max(context.offset() - prop.windowSize, 0);
          logger.atFine().log("Store new offset %d for stream with topic %s", newOffset, topic);
          consumer.store(newOffset);
        }
      } catch (IOException ex) {
        logger.atSevere().withCause(ex).log(
            "Error handling stream message with id %d", message.getPublishingId());
      }
    }
  }
}
