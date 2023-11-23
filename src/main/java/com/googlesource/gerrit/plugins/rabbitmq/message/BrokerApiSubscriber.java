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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGson;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.General;
import com.googlesource.gerrit.plugins.rabbitmq.session.SessionFactoryProvider;
import java.util.HashMap;
import java.util.Map;
import com.googlesource.gerrit.plugins.rabbitmq.session.SubscriberSession;

@Singleton
public class BrokerApiSubscriber implements Subscriber {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SubscriberSession session;
  private final Properties properties;
  private final Gson gson;
  private final boolean enabled;
  private final Map<TopicSubscriber, String> consumerTags = new HashMap<>();

  @Inject
  public BrokerApiSubscriber(
      SessionFactoryProvider sessionFactoryProvider,
      @EventGson Gson gson,
      @BrokerApiProperties Properties properties) {
    this.properties = properties;
    this.session = sessionFactoryProvider.get().createSubscriber(properties);
    this.gson = gson;
    this.enabled = properties.getSection(General.class).enableBrokerApi;
  }

  @Override
  public void stop() {
    if (session.isOpen()) {
      logger.atFine().log("Stopping BrokerApiSubscriber...");
      session.disconnect();
    } else {
      logger.atFine().log("BrokerApiSubscriber is already stopped");
    }
  }

  @Override
  public boolean subscribe(TopicSubscriber topicSubscriber) {
    String topic = topicSubscriber.topic();
    if (enabled) {
      logger.atFine().log("RabbitMqBrokerApi used to set consumer to topic %s", topic);
      String consumerTag =
          session.subscribe(
              topic,
              messageBody -> {
                logger.atFiner().log(
                    "The RabbitMqBrokerApi consumed event from topic %s with data: %s",
                    topic, messageBody);
                Event event = gson.fromJson(messageBody, Event.class);
                if (event.type != null) {
                  topicSubscriber.consumer().accept(event);
                } else {
                  logger.atFine().log("Event does not have a type, ignoring Event");
                }
              });
      if (consumerTag != null) {
        consumerTags.put(topicSubscriber, consumerTag);
        return true;
      }
    } else {
      logger.atWarning().log(
          "The RabbitMqBrokerApi is disabled, set enableBrokerApi to true to enable");
    }
    return false;
  }

  @Override
  public boolean removeSubscriber(TopicSubscriber topicSubscriber) {
    String consumerTag = consumerTags.remove(topicSubscriber);
    if (consumerTag == null) {
      logger.atWarning().log("Could not find consumerTag when trying to remove subscriber");
      return false;
    }
    return session.removeSubscriber(consumerTag);
  }
}
