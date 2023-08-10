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

package com.googlesource.gerrit.plugins.rabbitmq;

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.gerritforge.gerrit.eventbroker.TopicSubscriber;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.rabbitmq.message.BrokerApiPublisher;
import com.googlesource.gerrit.plugins.rabbitmq.message.BrokerApiSubscribers;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

public class RabbitMqBrokerApi implements BrokerApi {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final BrokerApiPublisher publisher;
  private final BrokerApiSubscribers subscriber;
  private final Set<TopicSubscriber> topicSubscribers;

  @Inject
  public RabbitMqBrokerApi(BrokerApiPublisher publisher, BrokerApiSubscribers subscriber) {
    this.publisher = publisher;
    this.subscriber = subscriber;
    this.topicSubscribers = Collections.synchronizedSet(new HashSet<>());
    publisher.start();
  }

  @Override
  public ListenableFuture<Boolean> send(String topic, Event message) {
    return publisher.publish(topic, message);
  }

  @Override
  public void receiveAsync(String topic, Consumer<Event> eventConsumer) {
    TopicSubscriber topicSubscriber = TopicSubscriber.topicSubscriber(topic, eventConsumer);
    if (subscriber.addSubscriber(topicSubscriber)) {
      topicSubscribers.add(topicSubscriber);
    }
  }

  @Override
  public Set<TopicSubscriber> topicSubscribers() {
    return ImmutableSet.copyOf(topicSubscribers);
  }

  @Override
  public void disconnect() {
    for (TopicSubscriber topicSubscriber : topicSubscribers) {
      subscriber.removeSubscriber(topicSubscriber);
    }
    topicSubscribers.clear();
  }

  @Override
  public void replayAllEvents(String topic) {
    throw new NotImplementedException(
        "The RabbitMqBrokerApi does not support replayAllEvents yet.");
  }
}
