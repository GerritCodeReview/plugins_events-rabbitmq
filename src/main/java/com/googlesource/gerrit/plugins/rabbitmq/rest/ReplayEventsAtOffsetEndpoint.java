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
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.rabbitmq.message.BrokerApiSubscribers;

/** REST endpoint for replaying all events from a specific offset. */
@Singleton
public class ReplayEventsAtOffsetEndpoint
    implements RestModifyView<ConfigResource, ReplayEventsAtOffsetEndpoint.Input> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Input {
    public Long offset;
    public String topic; // Now required since we don't get it from URL
  }

  public static class Result {
    public final String message;
    public final String topic;
    public final Long offset;
    public final boolean success;
    public final String timestamp;

    public Result(String message, String topic, Long offset, boolean success) {
      this.message = message;
      this.topic = topic;
      this.offset = offset;
      this.success = success;
      this.timestamp = java.time.Instant.now().toString();
    }
  }

  private final BrokerApiSubscribers brokerApiSubscribers;

  @Inject
  public ReplayEventsAtOffsetEndpoint(BrokerApiSubscribers brokerApiSubscribers) {
    this.brokerApiSubscribers = brokerApiSubscribers;
  }

  @Override
  public Response<Result> apply(ConfigResource resource, Input input)
      throws RestApiException {
    
    if (input == null) {
      throw new BadRequestException("Request body is required");
    }

    if (input.offset == null) {
      throw new BadRequestException("offset field is required");
    }

    if (input.offset < 0) {
      throw new BadRequestException("offset must be non-negative");
    }

    String topic = input.topic;

    if (topic == null || topic.isEmpty()) {
      throw new BadRequestException("topic must be specified in request body");
    }

    try {
      boolean success = brokerApiSubscribers.replayAllEventsAt(topic, input.offset);
      
      if (success) {
        logger.atInfo().log(
            "Successfully started replay of all events for topic '%s' from offset %d",
            topic, input.offset);

        Result result = new Result(
            "Event replay started successfully",
            topic,
            input.offset,
            true
        );

        return Response.ok(result);
      } else {
        logger.atWarning().log(
            "Failed to start replay of events for topic '%s' from offset %d",
            topic, input.offset);

        Result result = new Result(
            "Event replay failed - streams may not be enabled or topic not found",
            topic,
            input.offset,
            false
        );

        return Response.ok(result);
      }

    } catch (Exception e) {
      logger.atSevere().withCause(e).log(
          "Exception occurred while replaying events for topic '%s' from offset %d", 
          topic, input.offset);
      
      throw new UnprocessableEntityException(
          String.format("Failed to replay events: %s", e.getMessage()));
    }
  }
}
