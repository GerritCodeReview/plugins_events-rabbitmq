// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGson;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Message;
import com.googlesource.gerrit.plugins.rabbitmq.session.type.AMQPPublisherSession;
import java.util.Optional;

public class GerritEventPublisher extends MessagePublisher {
  private final Optional<String> defaultTopic;

  @Inject
  public GerritEventPublisher(
      @Assisted final Properties properties,
      AMQPPublisherSession.Factory sessionFactory,
      @EventGson Gson gson) {
    super(properties, sessionFactory, gson);
    String routingKey = properties.getSection(Message.class).routingKey;
    this.defaultTopic =
        routingKey != null && !routingKey.isEmpty() ? Optional.of(routingKey) : Optional.empty();
  }

  public ListenableFuture<Boolean> publish(String topic, Event event) {
    return super.publish(defaultTopic.orElse(topic), event);
  }
}
