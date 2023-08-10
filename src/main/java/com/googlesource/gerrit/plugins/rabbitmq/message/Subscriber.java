package com.googlesource.gerrit.plugins.rabbitmq.message;

import com.google.gerrit.server.events.Event;
import java.util.function.Consumer;

public interface Subscriber {
  boolean subscribe(String topic, Consumer<Event> eventConsumer);
}
