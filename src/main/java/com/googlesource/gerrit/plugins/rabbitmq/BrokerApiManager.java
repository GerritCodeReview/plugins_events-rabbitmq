// Copyright (C) 2024 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.rabbitmq;

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.gerritforge.gerrit.eventbroker.TopicSubscriber;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.message.BrokerApiPublisher;
import com.googlesource.gerrit.plugins.rabbitmq.message.BrokerApiSubscribers;
import com.google.common.flogger.FluentLogger;
import java.util.Set;

@Singleton
public class BrokerApiManager implements LifecycleListener {

  private final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final BrokerApiPublisher publisher;
  private final BrokerApiSubscribers subscribers;
  private final Set<TopicSubscriber> consumers;
  private final BrokerApi brokerApi;

  @Inject
  public BrokerApiManager(
      BrokerApiPublisher publisher,
      BrokerApiSubscribers subscribers,
      Set<TopicSubscriber> consumers,
      BrokerApi brokerApi) {
    logger.atFine().log("BrokerApiManager Initialized");
    this.publisher = publisher;
    this.subscribers = subscribers;
    this.consumers = consumers;
    this.brokerApi = brokerApi;
  }

  @Override
  public void start() {
    logger.atInfo().log("BrokerApiManager started and loading existing subscribers");
    consumers.forEach(
        topicSubscriber ->
            brokerApi.receiveAsync(topicSubscriber.topic(), topicSubscriber.consumer()));
    logger.atInfo().log(
        "RabbitMQ broker started with %d topic subscribers", brokerApi.topicSubscribers().size());
  }

  @Override
  public void stop() {
    logger.atInfo().log("BrokerApiManager Stopping");
    subscribers.stop();
    publisher.stop();
  }
}
