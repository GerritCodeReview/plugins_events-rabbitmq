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
package com.googlesource.gerrit.plugins.rabbitmq.session;

import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.session.type.AMQPPublisherSession;
import com.googlesource.gerrit.plugins.rabbitmq.session.type.AMQPSubscriberSession;

public class SessionFactory {
  public PublisherSession createPublisher(Properties properties) {
    return new AMQPPublisherSession(properties);
  }

  public SubscriberSession createSubscriber(Properties properties) {
    return new AMQPSubscriberSession(properties);
  }
}
