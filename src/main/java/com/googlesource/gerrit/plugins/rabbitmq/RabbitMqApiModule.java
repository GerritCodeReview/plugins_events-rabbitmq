package com.googlesource.gerrit.plugins.rabbitmq;

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

@Singleton
public class RabbitMqApiModule extends LifecycleModule {

  @Inject
  public RabbitMqApiModule() {}

  @Override
  protected void configure() {
    DynamicItem.bind(binder(), BrokerApi.class).to(RabbitMqBrokerApi.class).in(Scopes.SINGLETON);
  }
}
