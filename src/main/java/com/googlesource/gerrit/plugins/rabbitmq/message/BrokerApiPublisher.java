package com.googlesource.gerrit.plugins.rabbitmq.message;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGson;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.General;
import com.googlesource.gerrit.plugins.rabbitmq.session.SessionFactoryProvider;

@Singleton
public class BrokerApiPublisher extends MessagePublisher {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private boolean enabled;
  private boolean running;

  @Inject
  public BrokerApiPublisher(
      SessionFactoryProvider sessionFactoryProvider,
      @EventGson Gson gson,
      BrokerApiPropertiesRetriever brokerApiPropertiesRetriever) {
    super(brokerApiPropertiesRetriever.getBrokerApiProperties(), sessionFactoryProvider, gson);
    this.enabled = properties.getSection(General.class).enableBrokerApi;
  }

  @Override
  public void start() {
    if (running) {
      logger.atFine().log("BrokerApiPublisher is already running");
    } else if (enabled) {
      super.start();
      running = true;
    } else {
      logger.atWarning().log(
          "The RabbitMqBrokerApi is disabled, set enableBrokerApi to true to enable");
    }
  }

  @Override
  public void stop() {
    if (running) {
      logger.atFine().log("BrokerApiPublisher is getting stopped");
      super.stop();
      running = false;
    } else {
      logger.atFine().log("BrokerApiPublisher is already stopped");
    }
  }

  @Override
  public ListenableFuture<Boolean> publish(String topic, Event event) {
    if (enabled) {
      logger.atFine().log(
          "RabbitMqBrokerApi used to send message to topic %s with data: %s", topic, event);
      return super.publish(topic, event);
    } else {
      logger.atWarning().log(
          "The RabbitMqBrokerApi is disabled, set enableBrokerApi to true to enable");
      return null;
    }
  }
}
