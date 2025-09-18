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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.message.BrokerApiSubscribers;
import com.googlesource.gerrit.plugins.rabbitmq.session.OffsetInfo;
import java.util.ArrayList;
import java.util.List;

/** REST endpoint for getting current offsets for a specific topic. */
@Singleton
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
public class GetOffsetsForTopic implements RestReadView<TopicResource> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static class Output {
    public List<OffsetInfo> offsets;

    Output(List<OffsetInfo> offsets) {
      this.offsets = offsets;
    }
  }

  private final BrokerApiSubscribers brokerApiSubscribers;

  @Inject
  public GetOffsetsForTopic(BrokerApiSubscribers brokerApiSubscribers) {
    this.brokerApiSubscribers = brokerApiSubscribers;
  }

  @Override
  public Response<Output> apply(TopicResource resource) throws RestApiException {
    return Response.ok(new Output(brokerApiSubscribers.getOffsetInfoForTopic(resource.getTopicName())));
  }
}
