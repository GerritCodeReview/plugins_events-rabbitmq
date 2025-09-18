@PLUGIN@ POST topics/{topic}/replay
====================================

SYNOPSIS
--------

```
POST /config/server/events-rabbitmq~topics/{topic}/replay
```

DESCRIPTION
-----------
Replays all events from a specific offset for a given RabbitMQ topic in the events-rabbitmq plugin.

This endpoint allows administrators to replay messages from a specific point in the stream, which
is useful for:
* Recovering from message processing errors
* Re-processing events after configuration changes
* Debugging message handling issues
* Backfilling data after system maintenance

The replay operation will start from the specified offset and process all subsequent messages in
the topic.

ACCESS
------
**Administrators only.** This endpoint requires the `ADMINISTRATE_SERVER` global capability.

PARAMETERS
----------
**topic**: The name of the RabbitMQ topic to replay events from. This is specified as a path parameter
in the URL.

REQUEST BODY
------------
The request must include a JSON body with the following field:

* **offset** (number, required): The offset position to start replaying from. Can be:
  - A positive number for an absolute offset position
  - A negative number for a relative offset from the current maximum offset (e.g., -10 means
  "10 messages back from current position")

EXAMPLES
--------

Replay events from absolute offset 1000 for the "gerrit" topic:

```
curl -X POST --user admin:secret \
  -H "Content-Type: application/json" \
  -d '{"offset": 1000}' \
  http://host:port/a/config/server/events-rabbitmq~topics/gerrit/replay
```

Replay starting 50 messages back from the current position for the "gerrit" topic:

```
curl -X POST --user admin:secret \
  -H "Content-Type: application/json" \
  -d '{"offset": -50}' \
  http://host:port/a/config/server/events-rabbitmq~topics/gerrit/replay
```

Response:

```
)]}'
{
   "startOffset": 1000
}
```

**Response Fields:**

* **startOffset** (number): The calculated absolute offset position where the replay operation
started.

NOTES
-----

* **Negative offsets**: When using negative offsets, the system calculates the absolute position by
finding the maximum current offset from all consumers for the topic and adding the negative offset
value. If the calculated position would be less than 0, it starts from offset 0.
* **Consumer reset**: The replay operation will reset and recreate consumers for the specified topic.
* **Offset behavior**: The offset specifies the starting position for replay:
  - **Offset 0**: The first event (at offset 0) will be the first event processed
  - **Offset n**: The event at offset n+1 will be the first event processed (offset n is used as the starting point, then processing begins from the next message)

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
