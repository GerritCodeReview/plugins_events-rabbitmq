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
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.PropertiesFactory;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.General;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Gerrit;
import com.googlesource.gerrit.plugins.rabbitmq.message.MessagePublisher;
import com.googlesource.gerrit.plugins.rabbitmq.message.MessageSubscriber;
import com.googlesource.gerrit.plugins.rabbitmq.message.Publisher;
import com.googlesource.gerrit.plugins.rabbitmq.message.PublisherFactory;
import com.googlesource.gerrit.plugins.rabbitmq.message.Subscriber;
import com.googlesource.gerrit.plugins.rabbitmq.message.SubscriberFactory;
import com.googlesource.gerrit.plugins.rabbitmq.worker.DefaultEventWorker;
import com.googlesource.gerrit.plugins.rabbitmq.worker.EventWorker;
import com.googlesource.gerrit.plugins.rabbitmq.worker.EventWorkerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class Manager implements LifecycleListener {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String FILE_EXT = ".config";
  public static final String SITE_DIR = "site";

  private final String pluginName;
  private final Path pluginDataDir;
  private final EventWorker defaultEventWorker;
  private final EventWorker userEventWorker;
  private final PublisherFactory publisherFactory;
  private final SubscriberFactory subscriberFactory;
  private final PropertiesFactory propFactory;
  private final List<Publisher> publisherList = new ArrayList<>();
  private MessagePublisher rabbitMqApiPublisher;
  private MessageSubscriber rabbitMqApiSubscriber;

  @Inject
  public Manager(
      @PluginName final String pluginName,
      @PluginData final File pluginData,
      final DefaultEventWorker defaultEventWorker,
      final EventWorkerFactory eventWorkerFactory,
      final PublisherFactory publisherFactory,
      final SubscriberFactory subscriberFactory,
      final PropertiesFactory propFactory) {
    this.pluginName = pluginName;
    this.pluginDataDir = pluginData.toPath();
    this.defaultEventWorker = defaultEventWorker;
    this.userEventWorker = eventWorkerFactory.create();
    this.publisherFactory = publisherFactory;
    this.subscriberFactory = subscriberFactory;
    this.propFactory = propFactory;
  }

  @Override
  public void start() {
    List<Properties> propList = load();
    for (Properties properties : propList) {
      Publisher publisher = publisherFactory.create(properties);
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
    if (rabbitMqApiPublisher != null) {
      rabbitMqApiPublisher.stop();
      rabbitMqApiPublisher = null;
    }
    if (rabbitMqApiSubscriber != null) {
      rabbitMqApiSubscriber.stop();
      rabbitMqApiSubscriber = null;
    }
    for (Publisher publisher : publisherList) {
      publisher.stop();
      String listenAs = publisher.getProperties().getSection(Gerrit.class).listenAs;
      if (!listenAs.isEmpty()) {
        userEventWorker.removePublisher(publisher);
      } else {
        defaultEventWorker.removePublisher(publisher);
      }
    }
    publisherList.clear();
  }

  public Publisher getRabbitMqApiPublisher(Properties properties) {
    if (rabbitMqApiPublisher == null) {
      rabbitMqApiPublisher = (MessagePublisher) publisherFactory.create(properties);
      rabbitMqApiPublisher.start();
    }
    return rabbitMqApiPublisher;
  }

  public Subscriber getRabbitMqApiSubscriber(Properties properties) {
    if (rabbitMqApiSubscriber == null) {
      rabbitMqApiSubscriber = (MessageSubscriber) subscriberFactory.create(properties);
      rabbitMqApiSubscriber.start();
    }
    return rabbitMqApiSubscriber;
  }

  public void closeRabbitMqApi() {
    if (rabbitMqApiPublisher != null) {
      rabbitMqApiPublisher.stop();
      rabbitMqApiPublisher = null;
    }
    if (rabbitMqApiSubscriber != null) {
      rabbitMqApiSubscriber.stop();
      rabbitMqApiSubscriber = null;
    }
  }

  private List<Properties> load() {
    List<Properties> propList = new ArrayList<>();
    // Load base
    Properties base = propFactory.create(pluginDataDir.resolve(pluginName + FILE_EXT));
    base.load();
    if (!base.getSection(General.class).publishAllGerritEvents) {
      logger.atFine().log("publishAllGerritEvents disabled");
      return propList;
    }

    // Load sites
    try (DirectoryStream<Path> ds =
        Files.newDirectoryStream(pluginDataDir.resolve(SITE_DIR), "*" + FILE_EXT)) {
      for (Path configFile : ds) {
        Properties site = propFactory.create(configFile);
        if (site.load(base)) {
          propList.add(site);
        }
      }
    } catch (IOException ioe) {
      logger.atWarning().withCause(ioe).log("Failed to load properties.");
    }
    if (propList.isEmpty()) {
      logger.atWarning().log("No site configs found. Using base config only!");
      propList.add(base);
    }
    return propList;
  }
}
