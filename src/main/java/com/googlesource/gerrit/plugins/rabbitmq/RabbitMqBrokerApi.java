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
import com.gerritforge.gerrit.eventbroker.TopicSubscriberWithGroupId;
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
  private final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final BrokerApiPublisher publisher;
  private final BrokerApiSubscribers subscribers;
  private final Set<TopicSubscriber> topicSubscribers;

  @Inject
  public RabbitMqBrokerApi(BrokerApiPublisher publisher, BrokerApiSubscribers subscribers) {
    logger.atFine().log("Initializing RabbitMQBrokerApi");
    this.publisher = publisher;
    this.subscribers = subscribers;
    this.topicSubscribers = Collections.synchronizedSet(new HashSet<>());
    publisher.start();
  }

  @Override
  public ListenableFuture<Boolean> send(String topic, Event message) {
    return publisher.publish(topic, message);
  }

  @Override
  public void receiveAsync(String topic, Consumer<Event> eventConsumer) {
    logger.atInfo().log("Setting up async consumer for topic: %s", topic);
    TopicSubscriber topicSubscriber = TopicSubscriber.topicSubscriber(topic, eventConsumer);
    if (subscribers.addSubscriber(topicSubscriber)) {
      topicSubscribers.add(topicSubscriber);
    } else {
      logger.atWarning().log("failed to add %s to topic %s", eventConsumer, topic);
    }
  }

  @Override
  public Set<TopicSubscriber> topicSubscribers() {
    return ImmutableSet.copyOf(topicSubscribers);
  }

  @Override
  public void disconnect() {
    logger.atInfo().log("Disconnecting from broker and cancelling all consumers");
    for (TopicSubscriber topicSubscriber : topicSubscribers) {
      subscribers.removeSubscriber(topicSubscriber);
    }
    topicSubscribers.clear();
  }

  @Override
  public void replayAllEvents(String topic) {
    throw new NotImplementedException(
        "The RabbitMqBrokerApi does not support replayAllEvents yet.");
  }

  @Override
  public void disconnect(String topic, String groupId) {
    throw new NotImplementedException(
        "The RabbitMqBrokerApi does not support TopicSubscribers with group ID yet.");
  }

  @Override
  public void receiveAsync(String topic, String groupId, Consumer<Event> consumer) {
    throw new NotImplementedException(
        "The RabbitMqBrokerApi does not support TopicSubscribers with group ID yet.");
  }

  @Override
  public Set<TopicSubscriberWithGroupId> topicSubscribersWithGroupId() {
    throw new NotImplementedException(
        "The RabbitMqBrokerApi does not support TopicSubscribers with group ID yet.");
  }
}
