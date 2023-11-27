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
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class PublisherPropertiesProvider implements Provider<List<Properties>> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String SITE_DIR = "site";

  private final Path pluginDataDir;
  private final PropertiesFactory propFactory;
  private final Properties baseProperties;

  @Inject
  public PublisherPropertiesProvider(
      @PluginData final File pluginData,
      PropertiesFactory propFactory,
      @BaseProperties Properties baseProperties) {
    this.pluginDataDir = pluginData.toPath();
    this.propFactory = propFactory;
    this.baseProperties = baseProperties;
  }

  public List<Properties> get() {
    List<Properties> propList = new ArrayList<>();
    // Load sites
    try (DirectoryStream<Path> ds =
        Files.newDirectoryStream(
            pluginDataDir.resolve(SITE_DIR), "*" + BasePropertiesProvider.FILE_EXT)) {
      for (Path configFile : ds) {
        Properties site = propFactory.create(configFile);
        if (site.load(baseProperties)) {
          propList.add(site);
        }
      }
    } catch (IOException ioe) {
      logger.atWarning().withCause(ioe).log("Failed to load properties.");
    }
    if (propList.isEmpty()) {
      logger.atWarning().log("No site configs found. Using base config only!");
      propList.add(baseProperties);
    }
    return propList;
  }
}
