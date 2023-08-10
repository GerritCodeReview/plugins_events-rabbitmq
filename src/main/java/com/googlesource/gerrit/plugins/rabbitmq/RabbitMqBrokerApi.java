package com.googlesource.gerrit.plugins.rabbitmq;

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.gerritforge.gerrit.eventbroker.EventDeserializer;
import com.gerritforge.gerrit.eventbroker.TopicSubscriber;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.PropertiesFactory;
import com.googlesource.gerrit.plugins.rabbitmq.message.Publisher;
import com.googlesource.gerrit.plugins.rabbitmq.message.PublisherFactory;
import com.googlesource.gerrit.plugins.rabbitmq.session.Session;
import com.googlesource.gerrit.plugins.rabbitmq.session.SessionFactoryProvider;
import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class RabbitMqBrokerApi implements BrokerApi {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String BROKER_DIR = "broker";

  private final Session session;
  private final Set<TopicSubscriber> topicSubscribers;
  private final Publisher publisher;
  private final EventDeserializer eventDeserializer;

  @Inject
  public RabbitMqBrokerApi(
      @PluginName final String pluginName,
      @PluginData final File pluginData,
      PropertiesFactory propFactory,
      SessionFactoryProvider sessionFactoryProvider,
      PublisherFactory publisherFactory,
      EventDeserializer eventDeserializer) {
    Properties properties = loadProperties(pluginName, pluginData.toPath(), propFactory);
    this.session = sessionFactoryProvider.get().create(properties);
    this.topicSubscribers = new HashSet<>();
    this.publisher = publisherFactory.create(properties);
    this.eventDeserializer = eventDeserializer;
    publisher.start();
  }

  private Properties loadProperties(
      String pluginName, Path pluginDataDir, PropertiesFactory propFactory) {
    Path basePath = pluginDataDir.resolve(pluginName + Manager.FILE_EXT);
    Properties baseProperties = propFactory.create(basePath);
    baseProperties.load();

    Path path = pluginDataDir.resolve(BROKER_DIR + "/" + "broker" + Manager.FILE_EXT);
    Properties properties = propFactory.create(path);
    properties.load(baseProperties);
    return properties;
  }

  @Override
  public ListenableFuture<Boolean> send(String topic, Event message) {
    logger.atFine().log(
        "RabbitMqBrokerApi used to send message to topic %s with data: %s", topic, message);
    return publisher.publish(topic, message);
  }

  @Override
  public void receiveAsync(String topic, Consumer<Event> eventConsumer) {
    logger.atFine().log("RabbitMqBrokerApi used to set consumer to topic %s", topic);
    synchronized (topicSubscribers) {
      topicSubscribers.add(TopicSubscriber.topicSubscriber(topic, eventConsumer));
    }
    session.subscribe(
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
  }

  @Override
  public Set<TopicSubscriber> topicSubscribers() {
    return ImmutableSet.copyOf(topicSubscribers);
  }

  @Override
  public void disconnect() {
    logger.atFine().log("RabbitMqBrokerApi disconnects consumers");
    session.disconnect();
    topicSubscribers.clear();
  }

  @Override
  public void replayAllEvents(String topic) {
    logger.atWarning().log("The RabbitMqBrokerApi does not support replayAllEvents yet.");
  }
}
