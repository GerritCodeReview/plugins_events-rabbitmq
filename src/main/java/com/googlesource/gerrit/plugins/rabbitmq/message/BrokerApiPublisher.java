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

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGson;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.session.type.AMQPPublisherSession;

@Singleton
public class BrokerApiPublisher extends MessagePublisher {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  public BrokerApiPublisher(
      AMQPPublisherSession.Factory sessionFactory,
      @EventGson Gson gson,
      @BrokerApiProperties Properties properties) {
    super(properties, sessionFactory, gson);
  }

  @Override
  public void start() {
    logger.atFine().log("BrokerApiPublisher is starting");
    super.start();
  }

  @Override
  public void stop() {
    logger.atFine().log("BrokerApiPublisher is getting stopped");
    super.stop();
  }

  @Override
  public ListenableFuture<Boolean> publish(String topic, Event event) {
    logger.atFine().log("Message sent to topic %s with data: %s", topic, event);
    return super.publish(topic, event);
  }
}
