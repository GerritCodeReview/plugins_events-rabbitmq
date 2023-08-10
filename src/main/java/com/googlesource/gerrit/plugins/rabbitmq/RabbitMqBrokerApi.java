package com.googlesource.gerrit.plugins.rabbitmq;

import com.gerritforge.gerrit.eventbroker.BrokerApi;
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
import com.googlesource.gerrit.plugins.rabbitmq.config.section.General;
import com.googlesource.gerrit.plugins.rabbitmq.message.Publisher;
import com.googlesource.gerrit.plugins.rabbitmq.message.Subscriber;
import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class RabbitMqBrokerApi implements BrokerApi {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String BROKER_DIR = "broker";

  private final Subscriber subscriber;
  private final Set<TopicSubscriber> topicSubscribers;
  private final Publisher publisher;
  private final Manager manager;
  private final boolean isEnabled;

  @Inject
  public RabbitMqBrokerApi(
      @PluginName final String pluginName,
      @PluginData final File pluginData,
      PropertiesFactory propFactory,
      Manager manager) {
    Properties properties = loadProperties(pluginName, pluginData.toPath(), propFactory);
    this.isEnabled = properties.getSection(General.class).enableBrokerApi;
    if (isEnabled) {
      this.topicSubscribers = new HashSet<>();
      this.subscriber = manager.getRabbitMqApiSubscriber(properties);
      this.publisher = manager.getRabbitMqApiPublisher(properties);
      this.manager = manager;
    } else {
      this.topicSubscribers = null;
      this.subscriber = null;
      this.publisher = null;
      this.manager = null;
    }
  }

  private Properties loadProperties(
      String pluginName, Path pluginDataDir, PropertiesFactory propFactory) {
    Path basePath = pluginDataDir.resolve(pluginName + Manager.FILE_EXT);
    Properties baseProperties = propFactory.create(basePath);
    baseProperties.load();

    Path path = pluginDataDir.resolve(BROKER_DIR + "/" + "broker" + Manager.FILE_EXT);
    Properties properties = propFactory.create(path);
    if (properties.load(baseProperties)) {
      return properties;
    }
    logger.atInfo().log(
        "Could not load broker config. Use base config only for RabbitMQ broker API!");
    return baseProperties;
  }

  @Override
  public ListenableFuture<Boolean> send(String topic, Event message) {
    if (apiIsEnabled()) {
      logger.atFine().log(
          "RabbitMqBrokerApi used to send message to topic %s with data: %s", topic, message);
      return publisher.publish(topic, message);
    }
    return null;
  }

  @Override
  public void receiveAsync(String topic, Consumer<Event> eventConsumer) {
    if (apiIsEnabled()) {
      logger.atFine().log("RabbitMqBrokerApi used to set consumer to topic %s", topic);
      synchronized (topicSubscribers) {
        topicSubscribers.add(TopicSubscriber.topicSubscriber(topic, eventConsumer));
      }
      subscriber.subscribe(topic, eventConsumer);
    }
  }

  @Override
  public Set<TopicSubscriber> topicSubscribers() {
    if (apiIsEnabled()) {
      return ImmutableSet.copyOf(topicSubscribers);
    }
    return null;
  }

  @Override
  public void disconnect() {
    if (apiIsEnabled()) {
      logger.atFine().log("RabbitMqBrokerApi disconnects consumers");
      manager.closeRabbitMqApi();
      topicSubscribers.clear();
    }
  }

  @Override
  public void replayAllEvents(String topic) {
    logger.atWarning().log("The RabbitMqBrokerApi does not support replayAllEvents yet.");
  }

  private boolean apiIsEnabled() {
    if (isEnabled) {
      return true;
    }
    logger.atWarning().log(
        "The RabbitMqBrokerApi is disabled, set enableBrokerApi to true to enable");
    return false;
  }
}
