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

package com.googlesource.gerrit.plugins.rabbitmq.message;

import static com.gerritforge.gerrit.eventbroker.TopicSubscriber.topicSubscriber;

import com.gerritforge.gerrit.eventbroker.TopicSubscriber;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.restapi.PreconditionFailedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGson;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Stream;
import com.googlesource.gerrit.plugins.rabbitmq.session.SubscriberSession;
import com.googlesource.gerrit.plugins.rabbitmq.session.type.StreamSubscriberSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Singleton
public class BrokerApiSubscribers {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SubscriberSession session;
  private final Properties properties;
  private final Gson gson;
  private final Map<TopicSubscriber, String> consumerTags = new HashMap<>();

  @Inject
  public BrokerApiSubscribers(
      SubscriberSession.Factory sessionFactory,
      @EventGson Gson gson,
      @BrokerApiProperties Properties properties) {
    this.properties = properties;
    this.session = sessionFactory.create(properties);
    this.gson = gson;
  }

  public void stop() {
    if (session.isOpen()) {
      logger.atFine().log("Stopping BrokerApiSubscribers...");
      session.disconnect();
    } else {
      logger.atFine().log("BrokerApiSubscribers is already stopped");
    }
  }

  public boolean addSubscriber(TopicSubscriber topicSubscriber) {
    String topic = topicSubscriber.topic();
    logger.atFine().log("RabbitMqBrokerApi used to set consumer to topic %s", topic);
    String consumerTag =
        session.addSubscriber(
            topic,
            messageBody -> {
              logger.atFiner().log(
                  "The RabbitMqBrokerApi consumed event from topic %s with data: %s",
                  topic, messageBody);
              Event event = deserializeWithRetry(messageBody);
              if (event.type != null) {
                try {
                  topicSubscriber.consumer().accept(event);
                } catch (Exception e) {
                  logger.atWarning().withCause(e).log(
                      "Consumer listening on topic %s threw an exception for data: %s",
                      topic, messageBody);
                }
              } else {
                logger.atFine().log("Event does not have a type, ignoring Event");
              }
            });
    if (consumerTag != null) {
      consumerTags.put(topicSubscriber, consumerTag);
      return true;
    }
    return false;
  }

  private Event deserializeWithRetry(String messageBody) {
    int timeout = 5;
    int retryTime = 5;
    Retryer<Event> retryer =
        RetryerBuilder.<Event>newBuilder()
            .retryIfException()
            .withWaitStrategy(WaitStrategies.fixedWait(timeout, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterDelay(retryTime, TimeUnit.MINUTES))
            .build();
    try {
      return retryer.call(
          () -> {
            try {
              // May fail if not all plugins have registered their event types yet
              return gson.fromJson(messageBody, Event.class);
            } catch (JsonParseException e) {
              logger.atWarning().withCause(e).log(
                  "Deserializing json failed. Will retry again after %d seconds", timeout);
              throw e;
            }
          });
    } catch (RetryException e) {
      logger.atSevere().withCause(e).log(
          "Failed to deserialize event %s for %d minutes, stopping retries. This may be due to a plugin missing or failing to load.",
          messageBody, retryTime);
      return null;
    } catch (ExecutionException e) {
      // This should not happen
      logger.atSevere().withCause(e).log("Retrying of json deserializing failed unexpectedly");
      return null;
    }
  }

  public boolean removeSubscriber(TopicSubscriber topicSubscriber) {
    String consumerTag = consumerTags.remove(topicSubscriber);
    if (consumerTag == null) {
      logger.atWarning().log("Could not find consumerTag when trying to remove subscriber");
      return false;
    }
    return session.removeSubscriber(consumerTag);
  }

  public boolean replayAllEvents(TopicSubscriber topicSubscriber) {
    if (properties.getSection(Stream.class).enabled) {
      StreamSubscriberSession streamSession = (StreamSubscriberSession) session;
      streamSession.resetOffset(consumerTags.get(topicSubscriber), 0);
      removeSubscriber(topicSubscriber);
      addSubscriber(topicSubscriber);
      return true;
    }
    logger.atWarning().log(
        "Only streams support the replay functionality, please enable stream support to use this");
    return false;
  }

  public void replayAllEventsAt(String topic, long offset) throws ResourceNotFoundException, PreconditionFailedException {
    if (properties.getSection(Stream.class).enabled) {
      boolean found = false;
      StreamSubscriberSession streamSession = (StreamSubscriberSession) session;
      for (TopicSubscriber topicSubscriber : consumerTags.keySet()) {
        if (topicSubscriber.topic().equals(topic)) {
          streamSession.resetOffset(consumerTags.get(topicSubscriber), offset);
          removeSubscriber(topicSubscriber);
          addSubscriber(topicSubscriber);
          found = true;
        }
      }
      if (!found) {
        logger.atWarning().log("No subscriber found for topic %s", topic);
        throw new ResourceNotFoundException("No subscriber found for topic " + topic);
      }
    } else {
      logger.atWarning().log(
          "Only streams support the replay functionality, please enable stream support to use this");
      throw new PreconditionFailedException("Stream support must be enabled to use replay functionality");
    }
  }

  public void getOffsetsForTopic(String topic) throws ResourceNotFoundException, PreconditionFailedException {
    if (properties.getSection(Stream.class).enabled) {
      List<Long> offsets = new ArrayList<>();
      boolean found = false;
      StreamSubscriberSession streamSession = (StreamSubscriberSession) session;
      for (TopicSubscriber topicSubscriber : consumerTags.keySet()) {
        if (topicSubscriber.topic().equals(topic)) {
          offsets.add(streamSession.getOffset(consumerTags.get(topicSubscriber)));
          found = true;
        }
      }
      if (!found) {
        logger.atWarning().log("No subscriber found for topic %s", topic);
        throw new ResourceNotFoundException("No subscriber found for topic " + topic);
      }
    } else {
      logger.atWarning().log(
          "Only streams support the replay functionality, please enable stream support to use this");
      throw new PreconditionFailedException("Stream support must be enabled to use replay functionality");
    }
  }
}
