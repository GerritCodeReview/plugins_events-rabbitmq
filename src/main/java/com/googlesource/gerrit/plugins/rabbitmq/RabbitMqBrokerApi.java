package com.googlesource.gerrit.plugins.rabbitmq;

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.gerritforge.gerrit.eventbroker.TopicSubscriber;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.rabbitmq.message.BrokerApiPublisher;
import com.googlesource.gerrit.plugins.rabbitmq.message.BrokerApiSubscriber;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class RabbitMqBrokerApi implements BrokerApi {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final BrokerApiPublisher publisher;
  private final BrokerApiSubscriber subscriber;
  private final Set<TopicSubscriber> topicSubscribers;

  @Inject
  public RabbitMqBrokerApi(BrokerApiPublisher publisher, BrokerApiSubscriber subscriber) {
    this.publisher = publisher;
    this.subscriber = subscriber;
    this.topicSubscribers = new HashSet<>();
    publisher.start();
  }

  @Override
  public ListenableFuture<Boolean> send(String topic, Event message) {
    return publisher.publish(topic, message);
  }

  @Override
  public void receiveAsync(String topic, Consumer<Event> eventConsumer) {
    synchronized (topicSubscribers) {
      topicSubscribers.add(TopicSubscriber.topicSubscriber(topic, eventConsumer));
    }
    subscriber.subscribe(topic, eventConsumer);
  }

  @Override
  public Set<TopicSubscriber> topicSubscribers() {
    return ImmutableSet.copyOf(topicSubscribers);
  }

  @Override
  public void disconnect() {
    publisher.stop();
    subscriber.stop();
    topicSubscribers.clear();
  }

  @Override
  public void replayAllEvents(String topic) {
    logger.atWarning().log("The RabbitMqBrokerApi does not support replayAllEvents yet.");
  }
}
