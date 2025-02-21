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

package com.googlesource.gerrit.plugins.rabbitmq.config.section;

import com.google.common.flogger.FluentLogger;
import com.googlesource.gerrit.plugins.rabbitmq.annotation.Default;
import java.util.ArrayList;
import java.util.List;

public class Stream implements Section {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Default("false")
  public Boolean enabled;

  @Default public String uri;

  @Default public String streamPrefix;

  @Default public String consumerPrefix;

  @Default("500")
  public Integer windowSize;

  public boolean isValid() {
    List<String> missingSettings = new ArrayList<>(3);

    if (uri.isEmpty()) missingSettings.add("uri");
    if (streamPrefix.isEmpty()) missingSettings.add("streamPrefix");
    if (consumerPrefix.isEmpty()) missingSettings.add("consumerPrefix");

    if (!missingSettings.isEmpty()) {
      logger.atSevere().log(
          "Missing mandatory stream settings: %s, please set these if you want to use streams.",
          missingSettings);
      return false;
    }
    return true;
  }
}
