// Copyright (C) 2024 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.gerrit.extensions.registration.DynamicMap;

/** Collection of topics in the RabbitMQ plugin. */
@Singleton
public class TopicsCollection implements ChildCollection<ConfigResource, TopicResource> {

  private final Injector injector;

  @Inject
  public TopicsCollection(Injector injector) {
    this.injector = injector;
  }

  @Override
  public RestView<ConfigResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException("Topic listing not supported");
  }

  @Override
  public TopicResource parse(ConfigResource parent, IdString id) 
      throws ResourceNotFoundException {
    // Simply create a TopicResource with the provided topic name
    // Validation will happen in the actual endpoint
    return new TopicResource(parent, id.get());
  }

  @Override
  public DynamicMap<RestView<TopicResource>> views() {
    // Return empty map since child views are registered in RestModule
    return DynamicMap.emptyMap();
  }
}
