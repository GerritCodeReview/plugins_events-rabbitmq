package com.googlesource.gerrit.plugins.rabbitmq.worker;

import com.googlesource.gerrit.plugins.rabbitmq.message.EventDrivenPublisher;

public interface EventWorker {
  void addPublisher(EventDrivenPublisher publisher);

  void addPublisher(String pluginName, EventDrivenPublisher publisher, String userName);

  void removePublisher(EventDrivenPublisher publisher);

  void clear();
}
