@PLUGIN@ POST replay-events
============================

SYNOPSIS
--------

```
POST /config/server/replay-events
```

DESCRIPTION
-----------
Replays all events from a specific offset for a given RabbitMQ topic in the events-rabbitmq plugin.

This endpoint allows administrators to replay messages from a specific point in the stream, which is useful for:
* Recovering from message processing errors
* Re-processing events after configuration changes
* Debugging message handling issues
* Backfilling data after system maintenance

The replay operation will start from the specified offset and process all subsequent messages in the topic.

ACCESS
------
**Administrators only.** This endpoint requires the `ADMINISTRATE_SERVER` global capability.

REQUEST BODY
------------
The request must include a JSON body with the following fields:

* **topic** (string, required): The name of the RabbitMQ topic to replay events from.
* **offset** (number, required): The offset position to start replaying from. Can be:
  - A positive number for an absolute offset position
  - A negative number for a relative offset from the end (e.g., -10 means "10 messages from the end")

EXAMPLES
--------

Replay events from absolute offset 1000 for the "gerrit-events" topic:

```
curl -X POST --user admin:secret \
  -H "Content-Type: application/json" \
  -d '{"topic": "gerrit-events", "offset": 1000}' \
  http://host:port/a/config/server/replay-events
```

Replay the last 50 events from the "gerrit-events" topic:

```
curl -X POST --user admin:secret \
  -H "Content-Type: application/json" \
  -d '{"topic": "gerrit-events", "offset": -50}' \
  http://host:port/a/config/server/replay-events
```

Response:

```
)]}'
{
   "topic": "gerrit-events",
   "offset": 1000
}
```

**Response Fields:**

* **topic** (string): The name of the topic that events are being replayed from.
* **offset** (number): The starting offset position that was used for the replay operation.

ERROR RESPONSES
---------------

**Forbidden (403)**:
```
)]}'
{
   "message": "administrate server not permitted"
}
```

**Bad Request (400)**:
```
)]}'
{
   "message": "Request body is required"
}
```

```
)]}'
{
   "message": "topic is required"
}
```

```
)]}'
{
   "message": "offset is required"
}
```

**Not Found (404)**:
```
)]}'
{
   "message": "No subscriber found for topic: non-existent-topic"
}
```

NOTES
-----

* **Negative offsets**: When using negative offsets, the system will calculate the absolute position by subtracting from the current maximum offset in the topic.

SEE ALSO
--------

* [GET topics/{topic}/offsets](rest-api-topics-offsets-get.html)
* [Plugin Configuration](config.html)
* [Message Processing](message.html)
* [Plugin Development](../../../Documentation/dev-plugins.html)
* [REST API Protocol Details](../../../Documentation/rest-api.html#_protocol_details)

GERRIT
------
Part of [Gerrit Code Review](../../../Documentation/index.html)
