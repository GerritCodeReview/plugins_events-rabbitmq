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

import com.rabbitmq.client.ConfirmListener;
import java.util.function.Consumer;

public interface Session {
  boolean isOpen();

  boolean connect();

  void disconnect();

  boolean publish(String messageBody, String routingKey);

  String addSubscriber(String topic, Consumer<String> messageBodyConsumer);

  boolean removeSubscriber(String consumerTag);

  void setConfirmListener(ConfirmListener confirmListener);

  Long getNextPublishSeqNo();
}
