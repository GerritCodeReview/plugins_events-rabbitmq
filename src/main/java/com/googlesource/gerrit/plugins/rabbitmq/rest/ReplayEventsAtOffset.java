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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.message.BrokerApiSubscribers;
import java.util.List;

/** REST endpoint for replaying all events from a specific offset. */
@Singleton
public class ReplayEventsAtOffset
    implements RestModifyView<ConfigResource, ReplayEventsAtOffset.Input> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Input {
    public Long offset;
    public String topic;
  }

  private final BrokerApiSubscribers brokerApiSubscribers;

  @Inject
  public ReplayEventsAtOffset(BrokerApiSubscribers brokerApiSubscribers) {
    this.brokerApiSubscribers = brokerApiSubscribers;
  }

  @Override
  public Response<Input> apply(ConfigResource resource, Input input)
      throws RestApiException {
    
    if (input == null) {
      throw new BadRequestException("Request body is required");
    }

    if (input.offset == null) {
      throw new BadRequestException("offset field is required");
    }

    if (input.topic == null || input.topic.isEmpty()) {
      throw new BadRequestException("topic must be specified in request body");
    }

    long replayOffset = input.offset;
    
    // If offset is negative, calculate relative to current offset
    if (input.offset < 0) {
      List<Long> currentOffsets = brokerApiSubscribers.getOffsetsForTopic(input.topic);
      
      if (currentOffsets.isEmpty()) {
        throw new BadRequestException("No current offsets found for topic " + input.topic);
      }
      
      // Use the maximum current offset for calculation
      long currentOffset = currentOffsets.stream().mapToLong(Long::longValue).max().orElse(0);
      replayOffset = currentOffset + input.offset;
      
      if (replayOffset < 0) {
        replayOffset = 0;
      }
      
      logger.atInfo().log("Calculated replay offset %d from current offset %d with relative offset %d for topic %s", 
          replayOffset, currentOffset, input.offset, input.topic);
    }
    
    brokerApiSubscribers.replayAllEventsAt(input.topic, replayOffset);
    
    logger.atInfo().log("Successfully replayed events for topic %s at offset %d", input.topic, replayOffset);
    
    return Response.ok(input);
  }
}
