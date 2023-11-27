// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Gerrit;
import com.googlesource.gerrit.plugins.rabbitmq.message.BaseProperties;
import com.googlesource.gerrit.plugins.rabbitmq.message.EventDrivenPublisher;
import com.googlesource.gerrit.plugins.rabbitmq.message.EventDrivenPublisherFactory;
import com.googlesource.gerrit.plugins.rabbitmq.message.Publisher;
import com.googlesource.gerrit.plugins.rabbitmq.message.PublisherPropertiesProvider;
import com.googlesource.gerrit.plugins.rabbitmq.worker.DefaultEventWorker;
import com.googlesource.gerrit.plugins.rabbitmq.worker.EventWorker;
import com.googlesource.gerrit.plugins.rabbitmq.worker.EventWorkerFactory;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class Manager implements LifecycleListener {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String pluginName;
  private final EventWorker defaultEventWorker;
  private final EventWorker userEventWorker;
  private final EventDrivenPublisherFactory publisherFactory;
  private final List<Publisher> publisherList = new ArrayList<>();
  private final Properties baseProperties;
  private final PublisherPropertiesProvider publisherPropertiesProvider;

  @Inject
  public Manager(
      @PluginName final String pluginName,
      final DefaultEventWorker defaultEventWorker,
      final EventWorkerFactory eventWorkerFactory,
      final EventDrivenPublisherFactory publisherFactory,
      final @BaseProperties Properties baseProperties,
      final PublisherPropertiesProvider publisherPropertiesProvider) {
    this.pluginName = pluginName;
    this.defaultEventWorker = defaultEventWorker;
    this.userEventWorker = eventWorkerFactory.create();
    this.publisherFactory = publisherFactory;
    this.baseProperties = baseProperties;
    this.publisherPropertiesProvider = publisherPropertiesProvider;
  }

  @Override
  public void start() {
    for (Properties properties : publisherPropertiesProvider.get()) {
      EventDrivenPublisher publisher = publisherFactory.create(properties);
      publisher.start();
      String listenAs = properties.getSection(Gerrit.class).listenAs;
      if (!listenAs.isEmpty()) {
        userEventWorker.addPublisher(pluginName, publisher, listenAs);
      } else {
        defaultEventWorker.addPublisher(publisher);
      }
      publisherList.add(publisher);
    }
  }

  @Override
  public void stop() {
    for (Publisher publisher : publisherList) {
      publisher.stop();
    }
    defaultEventWorker.clear();
    userEventWorker.clear();
    publisherList.clear();
  }
}
