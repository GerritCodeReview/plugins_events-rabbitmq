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

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGson;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.rabbitmq.config.Properties;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.AMQP;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Gerrit;
import com.googlesource.gerrit.plugins.rabbitmq.config.section.Message;
import com.googlesource.gerrit.plugins.rabbitmq.session.PublisherSession;
import com.googlesource.gerrit.plugins.rabbitmq.session.type.AMQPPublisherSession;
import com.rabbitmq.client.ConfirmListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;

public class MessagePublisher implements Publisher, LifecycleListener {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int MAX_EVENTS = 16384;
  private static final String END_OF_STREAM = "END-OF-STREAM_$F7;XTSUQ(Dv#N6]g+gd,,uzRp%G-P";
  private static final Event EOS = new Event(END_OF_STREAM) {};

  private final Properties properties;
  private final PublisherSession session;
  private final Gson gson;
  private final LinkedBlockingQueue<TopicEvent> queue = new LinkedBlockingQueue<>(MAX_EVENTS);
  private final Object sessionMon = new Object();
  private GracefullyCancelableRunnable publisher;
  private Thread publisherThread;
  private final ConcurrentSkipListMap<Long, TopicEvent> eventsToBeAcked =
      new ConcurrentSkipListMap<>();
  private boolean publishConfirm;
  private final Object lostEventCountLock = new Object();
  private int lostEventCount = 0;

  @Inject
  public MessagePublisher(
      @Assisted final Properties properties,
      AMQPPublisherSession.Factory sessionFactory,
      @EventGson Gson gson) {
    this.properties = properties;
    this.publishConfirm = properties.getSection(Message.class).publishConfirm;
    this.gson = gson;
    this.session = sessionFactory.create(properties);
    if (publishConfirm) {
      session.setConfirmListener(new Listener());
    }
    this.publisher =
        new GracefullyCancelableRunnable() {

          volatile boolean canceled;

          @Override
          public void run() {
            canceled = false;
            while (!canceled) {
              try {
                TopicEvent topicEvent = queue.take();
                if (topicEvent.event.getType().equals(END_OF_STREAM)) {
                  continue;
                }
                while (!isConnected() && !canceled) {
                  synchronized (sessionMon) {
                    sessionMon.wait(1000);
                  }
                }
                if (!publishEvent(topicEvent) && !queue.offer(topicEvent)) {
                  logger.atSevere().log("Event lost: %s", gson.toJson(topicEvent.event));
                }
              } catch (InterruptedException e) {
                logger.atWarning().withCause(e).log(
                    "Interupted while waiting for event or connection.");
              }
            }
          }

          @Override
          public void cancel() {
            canceled = true;
            if (queue.isEmpty()) {
              queue.offer(new TopicEvent(null, EOS, null));
            }
          }

          @Override
          public String toString() {
            return "Rabbitmq publisher: "
                + properties.getSection(Gerrit.class).listenAs
                + "-"
                + properties.getSection(AMQP.class).uri;
          }
        };
  }

  @Override
  public void start() {
    ensurePublisherThreadStarted();
    if (!isConnected()) {
      connect();
    }
  }

  @Override
  public void stop() {
    publisher.cancel();
    if (publisherThread != null) {
      try {
        publisherThread.join();
      } catch (InterruptedException e) {
        // Do nothing
      }
    }
    session.disconnect();
  }

  @Override
  public ListenableFuture<Boolean> publish(String topic, Event event) {
    SettableFuture<Boolean> future = SettableFuture.create();
    publish(new TopicEvent(topic, event, future));
    return future;
  }

  private void publish(TopicEvent topicEvent) {
    if (!publisherThread.isAlive()) {
      ensurePublisherThreadStarted();
    }
    logger.atFine().log(
        "Adding event %s for topic %s to publisher queue", topicEvent.event, topicEvent.topic);
    synchronized (lostEventCountLock) {
      if (queue.offer(topicEvent)) {
        if (lostEventCount > 0) {
          logger.atWarning().log(
              "Event queue is no longer full, %d events were lost", lostEventCount);
          lostEventCount = 0;
        }
      } else {
        if (lostEventCount++ % 10 == 0) {
          logger.atSevere().log("Event queue is full, lost %d event(s)", lostEventCount);
        }
      }
    }
  }

  private boolean isConnected() {
    return session != null && session.isOpen();
  }

  private boolean publishEvent(TopicEvent topicEvent) {
    if (publishConfirm) {
      Long seqNbr = session.getNextPublishSeqNo();
      if (seqNbr == null) {
        return false;
      }
      eventsToBeAcked.put(seqNbr, topicEvent);
      return session.publish(gson.toJson(topicEvent.event), topicEvent.topic);
    }
    boolean published = session.publish(gson.toJson(topicEvent.event), topicEvent.topic);
    topicEvent.published.set(published);
    return published;
  }

  private void connect() {
    if (!isConnected() && session.connect()) {
      synchronized (sessionMon) {
        sessionMon.notifyAll();
      }
    }
  }

  private synchronized void ensurePublisherThreadStarted() {
    if (publisherThread == null || !publisherThread.isAlive()) {
      logger.atInfo().log("Creating new publisher thread.");
      publisherThread = new Thread(publisher);
      publisherThread.setName("rabbitmq-publisher");
      publisherThread.start();
    }
  }
  /** Runnable that can be gracefully canceled while running. */
  private interface GracefullyCancelableRunnable extends Runnable {
    /** Gracefully cancels the Runnable after completing ongoing task. */
    public void cancel();
  }

  private class TopicEvent {
    String topic;
    Event event;
    SettableFuture<Boolean> published;

    TopicEvent(String topic, Event event, SettableFuture<Boolean> published) {
      this.topic = topic;
      this.event = event;
      this.published = published;
    }
  }

  private class Listener implements ConfirmListener {
    public void handleAck(long deliveryTag, boolean multiple) throws IOException {
      Map<Long, TopicEvent> ackedEvents = getEvents(deliveryTag, multiple);
      if (ackedEvents.size() > 1) {
        logger.atFine().log(
            "Multiple deliveries was acked at the same time, expected count: %d",
            ackedEvents.size());
      }
      ackedEvents.forEach(
          (dt, topicEvent) -> {
            if (topicEvent != null) {
              logger.atFine().log(
                  "Event with sequence number %d that was published to the topic %s was acked.",
                  dt, topicEvent.topic);
              topicEvent.published.set(true);
            } else {
              logger.atWarning().log(
                  "Event with sequence number %d that was about to be acked is unexpectedly missing",
                  dt);
            }
          });
    }

    public void handleNack(long deliveryTag, boolean multiple) throws IOException {
      Map<Long, TopicEvent> nackedEvents = getEvents(deliveryTag, multiple);
      if (nackedEvents.size() > 1) {
        logger.atFine().log(
            "Multiple deliveries was nacked at the same time, expected count: %d",
            nackedEvents.size());
      }
      nackedEvents.forEach(
          (dt, topicEvent) -> {
            if (topicEvent != null) {
              logger.atWarning().log(
                  "Event with sequence number %d that was published to the topic %s was not acked. Retrying publish of event",
                  dt, topicEvent.topic);
              publish(topicEvent);
            } else {
              logger.atWarning().log(
                  "Event with sequence number %d that was about to be nacked is unexpectedly missing",
                  dt);
            }
          });
    }

    private Map<Long, TopicEvent> getEvents(long deliveryTag, boolean multiple) {
      Map<Long, TopicEvent> events = new HashMap<>();
      if (multiple) {
        Map<Long, TopicEvent> map = eventsToBeAcked.headMap(deliveryTag, true);
        events.putAll(map);
        map.clear();
      } else {
        events.put(deliveryTag, eventsToBeAcked.remove(deliveryTag));
      }
      return events;
    }
  }
}
