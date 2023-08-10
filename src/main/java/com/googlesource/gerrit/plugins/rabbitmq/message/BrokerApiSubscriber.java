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
  private boolean running;
  private boolean enabled;

  @Inject
  public BrokerApiSubscriber(
      SessionFactoryProvider sessionFactoryProvider,
      EventDeserializer eventDeserializer,
      BrokerApiPropertiesRetriever brokerApiPropertiesRetriever) {
    this.properties = brokerApiPropertiesRetriever.getBrokerApiProperties();
    this.session = sessionFactoryProvider.get().create(properties);
    this.eventDeserializer = eventDeserializer;
    this.enabled = properties.getSection(General.class).enableBrokerApi;
  }

  @Override
  public void stop() {
    if (running) {
      logger.atFine().log("BrokerApiSubscriber is getting stopped");
      session.disconnect();
      running = false;
    } else {
      logger.atFine().log("BrokerApiSubscriber is already stopped");
    }
  }

  @Override
  public boolean subscribe(String topic, Consumer<Event> eventConsumer) {
    if (enabled) {
      running = true;
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
