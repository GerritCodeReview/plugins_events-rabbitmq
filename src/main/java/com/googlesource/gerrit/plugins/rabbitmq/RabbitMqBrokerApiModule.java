// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.rabbitmq;

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.gerritforge.gerrit.eventbroker.TopicSubscriber;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.message.BrokerApiProperties;
import com.googlesource.gerrit.plugins.rabbitmq.message.BrokerApiPropertiesProvider;
import java.util.Set;

@Singleton
public class RabbitMqBrokerApiModule extends LifecycleModule {
  private final FluentLogger logger = FluentLogger.forEnclosingClass();
  private Set<TopicSubscriber> activeConsumers = Sets.newHashSet();

  @Inject
  public RabbitMqBrokerApiModule() {
    logger.atFine().log("RabbitMqBrokerApiModule loaded");
  }

  @Override
  protected void configure() {
    bind(Properties.class)
        .annotatedWith(BrokerApiProperties.class)
        .toProvider(BrokerApiPropertiesProvider.class);
    DynamicItem.bind(binder(), BrokerApi.class).to(RabbitMqBrokerApi.class).in(Scopes.SINGLETON);
    DynamicSet.bind(binder(), LifecycleListener.class).to(BrokerApiManager.class);
  }
}
