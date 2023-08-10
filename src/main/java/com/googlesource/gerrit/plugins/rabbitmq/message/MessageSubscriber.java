package com.googlesource.gerrit.plugins.rabbitmq.message;

import com.gerritforge.gerrit.eventbroker.EventDeserializer;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.session.Session;
import com.googlesource.gerrit.plugins.rabbitmq.session.SessionFactoryProvider;
import java.util.function.Consumer;

public class MessageSubscriber implements Subscriber, LifecycleListener {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Session session;
  private final Properties properties;
  private final EventDeserializer eventDeserializer;

  @Inject
  public MessageSubscriber(
      @Assisted final Properties properties,
      SessionFactoryProvider sessionFactoryProvider,
      EventDeserializer eventDeserializer) {
    this.session = sessionFactoryProvider.get().create(properties);
    this.properties = properties;
    this.eventDeserializer = eventDeserializer;
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    session.disconnect();
  }

  @Override
  public boolean subscribe(String topic, Consumer<Event> eventConsumer) {
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
  }
}
