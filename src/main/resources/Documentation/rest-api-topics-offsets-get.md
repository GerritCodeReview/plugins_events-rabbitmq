@PLUGIN@ GET topics/{topic}/offsets
=====================================

SYNOPSIS
--------

```
GET /config/server/events-rabbitmq~topics/{topic}/offsets
```

DESCRIPTION
-----------
Gets the current consumer offsets for a specific RabbitMQ topic in the events-rabbitmq plugin.

This endpoint allows administrators to monitor the current position of consumers for a given topic,
which is useful for monitoring message processing status and identifying potential backlogs.

ACCESS
------
**Administrators only.** This endpoint requires the `ADMINISTRATE_SERVER` global capability.

PARAMETERS
----------
**topic**: The name of the RabbitMQ topic to get offsets for. This is specified as a path parameter
in the URL.

EXAMPLES
--------

Get the current offsets for the "gerrit" topic:

```
curl -X GET --user admin:secret \
  http://host:port/a/config/server/events-rabbitmq~topics/gerrit/offsets
```

Response:

```
)]}'
{
   "offsets": {
     "stream.subid-0" : 162,
     "stream.subid-1": 158
   }
}
```

**Response Fields:**

* **offsets** (object): Map of current consumer offsets for this topic. Each key is a consumer tag
and each value is that consumer's current offset position.

NOTES
-----

* Each consumer may be at a different offset position depending on processing speed and when it was
started.
* Offset values represent the position of the last processed message for each consumer.
* An offset value of -1 indicates that the consumer hasn't processed any messages yet.

SEE ALSO
--------

* [POST replay-events](rest-api-replay-events.html)
* [Plugin Configuration](config.html)
* [Plugin Development](../../../Documentation/dev-plugins.html)
* [REST API Protocol Details](../../../Documentation/rest-api.html#_protocol_details)

GERRIT
------
Part of [Gerrit Code Review](../../../Documentation/index.html)
