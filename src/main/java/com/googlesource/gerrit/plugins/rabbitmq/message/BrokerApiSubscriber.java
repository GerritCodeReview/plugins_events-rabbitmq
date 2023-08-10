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

import com.gerritforge.gerrit.eventbroker.EventDeserializer;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.General;
import com.googlesource.gerrit.plugins.rabbitmq.session.Session;
import com.googlesource.gerrit.plugins.rabbitmq.session.SessionFactoryProvider;
import java.util.function.Consumer;

@Singleton
public class BrokerApiSubscriber implements Subscriber {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Session session;
  private final Properties properties;
  private final EventDeserializer eventDeserializer;
  private final boolean enabled;

  @Inject
  public BrokerApiSubscriber(
      SessionFactoryProvider sessionFactoryProvider,
      EventDeserializer eventDeserializer,
      @BrokerApiProperties Properties properties) {
    this.properties = properties;
    this.session = sessionFactoryProvider.get().create(properties);
    this.eventDeserializer = eventDeserializer;
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
  public boolean subscribe(String topic, Consumer<Event> eventConsumer) {
    if (enabled) {
      logger.atFine().log("RabbitMqBrokerApi used to set consumer to topic %s", topic);
      return session.subscribe(
          topic,
          messageBody -> {
            logger.atFiner().log(
                "The RabbitMqBrokerApi consumed event from topic %s with data: %s",
                topic, messageBody);
            try {
              Event event = eventDeserializer.deserialize(messageBody);
              eventConsumer.accept(event);
            } catch (NullPointerException e) {
              logger.atFine().log("Event does not have a type, ignoring Event");
            }
          });
    } else {
      logger.atWarning().log(
          "The RabbitMqBrokerApi is disabled, set enableBrokerApi to true to enable");
      return false;
    }
  }
}
