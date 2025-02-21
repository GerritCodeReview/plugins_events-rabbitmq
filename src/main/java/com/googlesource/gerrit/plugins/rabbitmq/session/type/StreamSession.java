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
import com.rabbitmq.stream.Address;
import com.rabbitmq.stream.Environment;
import com.rabbitmq.stream.EnvironmentBuilder;
import org.apache.commons.lang3.StringUtils;

public abstract class StreamSession extends AMQPSession {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected volatile Environment environment;

  public StreamSession(Properties properties) {
    super(properties);
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

    EnvironmentBuilder builder = Environment.builder();
    if (StringUtils.isNotEmpty(stream.uri)) {
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

      /**
       * Add resolver that make sure domain is present in Address to be able to match Subject
       * Alternative DNS in certificate properly
       */
      String temp = stream.uri.substring(stream.uri.indexOf(".") + 1);
      String domain = temp.substring(0, temp.indexOf(":"));
      builder.addressResolver(
          address -> {
            if (address.host().endsWith("." + domain)) {
              return address;
            }
            return new Address(address.host() + "." + domain, address.port());
          });
      environment = builder.build();
      logger.atInfo().log("Environment built for %s.", stream.uri);
      return true;
    }
    logger.atSevere().log(
        "Streams for broker is enabled, please set an uri in the stream section if you want to use streams");
    return false;
  }

  @Override
  public void disconnect() {
    environment.close();
    environment = null;
  }
}
