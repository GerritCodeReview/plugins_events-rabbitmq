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

package com.googlesource.gerrit.plugins.rabbitmq.rest;

import static com.google.gerrit.server.config.ConfigResource.CONFIG_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

public class RestModule extends RestApiModule {

  @Override
  protected void configure() {
    // Register the TopicResource kind so Gerrit knows about it
    DynamicMap.mapOf(binder(), TopicResource.TOPIC_KIND);
    // Register topics as a child collection
    child(CONFIG_KIND, "topics").to(TopicsCollection.class);
    get(TopicResource.TOPIC_KIND, "offsets").to(GetOffsetsForTopic.class);

    post(CONFIG_KIND, "replay-events").to(ReplayEventsAtOffset.class);
  }
}
