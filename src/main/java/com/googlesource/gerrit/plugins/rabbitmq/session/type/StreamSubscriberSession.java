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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class StreamSubscriberSession extends StreamSession implements SubscriberSession {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private volatile Consumers consumers = new Consumers();

  @Inject
  public StreamSubscriberSession(@Assisted Properties properties) {
    super(properties);
  }

  @Override
  public void disconnect() {
    logger.atInfo().log("Disconnecting subscriber session...");
    consumers.close();
    super.disconnect();
  }

  @Override
  public String addSubscriber(String topic, Consumer<String> messageBodyConsumer) {
    if (environment == null) {
      if (!connect()) {
        logger.atSevere().log("Failed to connect to rabbitMQ with environment");
        return null;
      }
    }
    Stream streamProp = properties.getSection(Stream.class);

    String streamName = streamProp.streamPrefix + "." + topic;
    String consumerName = streamProp.consumerPrefix + "." + topic;
    if (!environment.streamExists(streamName)) {
      environment.streamCreator().stream(streamName).create();
    }

    bindStreamToExchange(streamName, topic);

    String consumerId = UUID.randomUUID().toString();
    Handler handler = new Handler(topic, messageBodyConsumer, consumerId);
    com.rabbitmq.stream.Consumer consumer =
        environment.consumerBuilder().stream(streamName)
            .offset(OffsetSpecification.first())
            .name(consumerName)
            .manualTrackingStrategy()
            .builder()
            .messageHandler(handler)
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

    consumers.put(consumerId, consumer, false);
    handler.start();
    return consumerId;
  }

  private void bindStreamToExchange(String streamName, String topic) {
    String exchangeName = properties.getSection(Exchange.class).name;
    AMQPSession session = new AMQPSession(properties);
    try (Channel channel = session.createChannel()) {
      channel.queueBind(streamName, exchangeName, topic);
    } catch (IOException | TimeoutException ex) {
      logger.atSevere().withCause(ex).log("Failed to bind stream to exchange or close channel");
    }
    session.disconnect();
  }

  @Override
  public boolean removeSubscriber(String consumerId) {
    return consumers.closeConsumer(consumerId);
  }

  public void resetOffset(String consumerId, long offset) {
    consumers.reset(consumerId, offset);
  }

  public long getCurrentOffset(String consumerId) {
    return consumers.getCurrentOffset(consumerId);
  }

  private class Consumers {
    private volatile Map<String, ConsumerState> consumersMap = new ConcurrentHashMap<>();

    void put(String consumerId, com.rabbitmq.stream.Consumer consumer, boolean resetOffset) {
      consumersMap.put(consumerId, new ConsumerState(consumer, resetOffset));
    }

    boolean isReset(String consumerId) {
      return consumersMap.get(consumerId).resetOffset;
    }

    void reset(String consumerId, long offset) {
      com.rabbitmq.stream.Consumer consumer = consumersMap.get(consumerId).consumer;
      if (consumer != null) {
        synchronized (consumer) {
          consumersMap.get(consumerId).resetOffset = true;
          consumer.store(offset);
          // Also update current offset when resetting
          consumersMap.get(consumerId).currentOffset.set(offset);
        }
      }
    }

    long getOffset(String consumerId) {
      com.rabbitmq.stream.Consumer consumer = consumersMap.get(consumerId).consumer;
      if (consumer != null) {
        synchronized (consumer) {
          return consumer.storedOffset();
        }
      }
      return -1;
    }

    void updateCurrentOffset(String consumerId, long offset) {
      ConsumerState state = consumersMap.get(consumerId);
      if (state != null) {
        state.currentOffset.set(offset);
      }
    }

    long getCurrentOffset(String consumerId) {
      ConsumerState state = consumersMap.get(consumerId);
      if (state != null) {
        return state.currentOffset.get();
      }
      return -1;
    }

    boolean closeConsumer(String consumerId) {
      ConsumerState state = consumersMap.remove(consumerId);
      if (state == null) {
        return false;
      }
      state.consumer.close();
      return true;
    }

    void close() {
      synchronized (consumersMap) {
        Iterator<Entry<String, ConsumerState>> it = consumersMap.entrySet().iterator();
        while (it.hasNext()) {
          it.next().getValue().consumer.close();
          it.remove();
        }
      }
    }

    private class ConsumerState {
      com.rabbitmq.stream.Consumer consumer;
      boolean resetOffset;
      AtomicLong currentOffset;

      ConsumerState(com.rabbitmq.stream.Consumer consumer, boolean resetOffset) {
        this.consumer = consumer;
        this.resetOffset = resetOffset;
        // Initialize currentOffset with the consumer's stored offset if available
        long initialOffset = -1L;
        try {
          initialOffset = consumer.storedOffset();
        } catch (Exception e) {
          logger.atFine().withCause(e).log("Could not get initial stored offset, using -1");
        }
        this.currentOffset = new AtomicLong(initialOffset);
      }
    }
  }

  private class Handler implements MessageHandler {
    private AtomicInteger messageConsumed;
    private String topic;
    private Consumer<String> messageBodyConsumer;
    private String consumerId;
    private boolean run = false;

    Handler(String topic, Consumer<String> messageBodyConsumer, String consumerId) {
      messageConsumed = new AtomicInteger(0);
      this.topic = topic;
      this.messageBodyConsumer = messageBodyConsumer;
      this.consumerId = consumerId;
    }

    @Override
    public void handle(MessageHandler.Context context, Message message) {
      while(!run) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
      Stream prop = properties.getSection(Stream.class);
      try {
        logger.atFiner().log(
            "Consume message from topic %s with offset %d", topic, context.offset());

        consumers.updateCurrentOffset(consumerId, context.offset());
        messageBodyConsumer.accept(new String(message.getBodyAsBinary(), "UTF-8"));
        if (messageConsumed.incrementAndGet() % prop.windowSize == 0) {
          com.rabbitmq.stream.Consumer consumer = context.consumer();
          long newOffset = Math.max(context.offset() - prop.windowSize, 0);
          logger.atFine().log("Store new offset %d for stream with topic %s", newOffset, topic);
          synchronized (consumer) {
            if (!consumers.isReset(consumerId)) {
              consumer.store(newOffset);
            }
          }
        }
      } catch (IOException ex) {
        logger.atSevere().withCause(ex).log(
            "Error handling stream message with id %d", message.getPublishingId());
      }
    }
    void start() {
      run = true;
    }
  }
}
