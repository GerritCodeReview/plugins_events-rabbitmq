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
import com.googlesource.gerrit.plugins.rabbitmq.session.SessionFactoryProvider;

public class EventDrivenPublisher extends TopicEventPublisher {

  @Inject
  public EventDrivenPublisher(
      @Assisted final Properties properties,
      SessionFactoryProvider sessionFactoryProvider,
      @EventGson Gson gson) {
    super(properties, sessionFactoryProvider, gson);
  }

  public ListenableFuture<Boolean> publish(Event event) {
    Message message = properties.getSection(Message.class);
    if (message.routingKey != null && !message.routingKey.isEmpty()) {
      // Set routingKey from configuration.
      return publish(message.routingKey, event);
    } else {
      return publish(event.type, event);
    }
  }
}
