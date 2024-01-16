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
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.PropertiesFactory;
import java.io.File;
import java.nio.file.Path;

@Singleton
public class BasePropertiesProvider implements Provider<Properties> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String FILE_EXT = ".config";

  private final Properties properties;

  @Inject
  public BasePropertiesProvider(
      @PluginName final String pluginName,
      @PluginData final File pluginData,
      PropertiesFactory propFactory) {
    this.properties = loadProperties(pluginName, pluginData.toPath(), propFactory);
  }

  public Properties get() {
    return properties;
  }

  private static Properties loadProperties(
      String pluginName, Path pluginDataDir, PropertiesFactory propFactory) {
    Path basePath = pluginDataDir.resolve(pluginName + FILE_EXT);
    Properties baseProperties = propFactory.create(basePath);
    baseProperties.load();
    return baseProperties;
  }
}
