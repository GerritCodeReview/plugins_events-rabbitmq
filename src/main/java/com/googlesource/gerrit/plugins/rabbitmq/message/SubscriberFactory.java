package com.googlesource.gerrit.plugins.rabbitmq.message;

import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;

public interface SubscriberFactory {
  Subscriber create(Properties properties);
}
