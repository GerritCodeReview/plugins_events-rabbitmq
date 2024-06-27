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
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGson;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.session.SubscriberSession;
import com.googlesource.gerrit.plugins.rabbitmq.session.type.AMQPSubscriberSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

@Singleton
public class BrokerApiSubscribers {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SubscriberSession session;
  private final Properties properties;
  private final Gson gson;
  private final Map<TopicSubscriber, List<String>> subscriberConsumerTags = new HashMap<>();

  @Inject
  public BrokerApiSubscribers(
      AMQPSubscriberSession.Factory sessionFactory,
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
    List<String> consumerTags = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
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
        consumerTags.add(consumerTag);
      } else {
        logger.atSevere().log("Failed to create subscriber for  topic %s in BrokerApiSubscribers", topic);
      }
    }
    if (!consumerTags.isEmpty()) {
      subscriberConsumerTags.put(topicSubscriber, consumerTags);
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
    List<String> consumerTags = subscriberConsumerTags.remove(topicSubscriber);
    if (consumerTags == null) {
      logger.atWarning().log("Could not find consumerTag when trying to remove subscriber");
      return false;
    }
    for (String consumerTag : consumerTags) {
      session.removeSubscriber(consumerTag);
    }
    return true;
  }
}
