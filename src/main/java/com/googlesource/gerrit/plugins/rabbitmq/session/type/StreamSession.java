// Copyright (C) 2025 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.rabbitmq.session.type;

import com.google.common.flogger.FluentLogger;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.AMQP;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Gerrit;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Stream;
import com.googlesource.gerrit.plugins.rabbitmq.session.Session;
import com.rabbitmq.stream.Environment;
import com.rabbitmq.stream.EnvironmentBuilder;
import org.apache.commons.lang3.StringUtils;

public abstract class StreamSession implements Session {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected volatile Environment environment;

  protected final Properties properties;

  public StreamSession(Properties properties) {
    this.properties = properties;
  }

  @Override
  public boolean isOpen() {
    if (environment != null) {
      logger.atWarning().log("Environment does not have a method to check if connection is open");
      return true;
    }
    return false;
  }

  @Override
  public synchronized boolean connect() {
    AMQP amqp = properties.getSection(AMQP.class);
    Stream stream = properties.getSection(Stream.class);

    if (!stream.isValid()) {
      return false;
    }
    EnvironmentBuilder builder = Environment.builder();
    builder.uri(stream.uri);
    if (StringUtils.isNotEmpty(amqp.username)) {
      builder.username(amqp.username);
    }
    Gerrit gerrit = properties.getSection(Gerrit.class);
    String securePassword = gerrit.getAMQPUserPassword(amqp.username);
    if (StringUtils.isNotEmpty(securePassword)) {
      builder.password(securePassword);
    } else if (StringUtils.isNotEmpty(amqp.password)) {
      builder.password(amqp.password);
    }

    environment = builder.build();
    logger.atInfo().log("Environment built for %s.", stream.uri);
    return true;
  }

  @Override
  public void disconnect() {
    environment.close();
    environment = null;
  }
}
