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
   "offsets": [
     {
       "offset": 162,
       "lastUpdated": "2025-10-07T14:30:15.123Z"
     },
     {
       "offset": 158,
       "lastUpdated": "2025-10-07T14:30:12.456Z"
     },
     {
       "offset": 145,
       "lastUpdated": "2025-10-07T14:29:58.789Z"
     }
   ]
}
```

**Response Fields:**

* **offsets** (array): List of offset information objects for each consumer on this topic.
  * **offset** (number): The current offset position of the consumer.
  * **lastUpdated** (string): ISO 8601 timestamp indicating when this offset was last updated.

NOTES
-----

* Each consumer may be at a different offset position depending on processing speed and when it was
started.
* Offset values represent the position of the last processed message for each consumer.
* An offset value of -1 indicates that the consumer hasn't processed any messages yet.
* The `lastUpdated` timestamp shows when the offset was last modified, which helps identify stale
or inactive consumers.
* All timestamps are returned in UTC timezone using ISO 8601 format.
* The list contains one offset information object per consumer for the specified topic.

SEE ALSO
--------

* [POST topics/{topic}/replay](rest-api-replay-events.html)
* [Plugin Configuration](config.html)
* [Plugin Development](../../../Documentation/dev-plugins.html)
* [REST API Protocol Details](../../../Documentation/rest-api.html#_protocol_details)

GERRIT
------
Part of [Gerrit Code Review](../../../Documentation/index.html)
