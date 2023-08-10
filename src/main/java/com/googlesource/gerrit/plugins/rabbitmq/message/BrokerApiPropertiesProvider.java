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

package com.googlesource.gerrit.plugins.rabbitmq.message;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.PropertiesFactory;
import java.io.File;
import java.nio.file.Path;

@Singleton
public class BrokerApiPropertiesProvider implements Provider<Properties> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Properties properties;

  @Inject
  public BrokerApiPropertiesProvider(
      @PluginData final File pluginData,
      PropertiesFactory propFactory,
      @BaseProperties Properties baseProperties) {
    this.properties = loadProperties(pluginData.toPath(), propFactory, baseProperties);
  }

  public Properties get() {
    return properties;
  }

  private Properties loadProperties(
      Path pluginDataDir, PropertiesFactory propFactory, Properties baseProperties) {
    Path path = pluginDataDir.resolve("broker/broker" + BasePropertiesProvider.FILE_EXT);
    Properties properties = propFactory.create(path);
    if (properties.load(baseProperties)) {
      return properties;
    }
    logger.atInfo().log(
        "Could not load broker config. Use base config only for RabbitMQ broker API!");
    return baseProperties;
  }
}
