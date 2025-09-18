@PLUGIN@ GET topics/{topic}/offsets
=====================================

SYNOPSIS
--------

```
GET /config/server/topics/{topic}/offsets
```

DESCRIPTION
-----------
Gets the current consumer offsets for a specific RabbitMQ topic in the events-rabbitmq plugin.

This endpoint allows administrators to monitor the current position of consumers for a given topic, which is useful for monitoring message processing status and identifying potential backlogs.

ACCESS
------
**Administrators only.** This endpoint requires the `ADMINISTRATE_SERVER` global capability.

PARAMETERS
----------
**topic**: The name of the RabbitMQ topic to get offsets for. This is specified as a path parameter in the URL.

EXAMPLES
--------

Get the current offsets for the "gerrit-events" topic:

```
curl -X GET --user admin:secret http://host:port/a/config/server/topics/gerrit-events/offsets
```

Response:

```
)]}'
{
   "topic": "gerrit-events",
   "offsets": [123, 456, 789]
}
```

**Response Fields:**

* **topic** (string): The name of the topic that was queried.
* **offsets** (array of numbers): Array of current consumer offsets for this topic. Each element represents the offset for a different consumer or partition.

ERROR RESPONSES
---------------

**Forbidden (403)**:
```
)]}'
{
   "message": "administrate server not permitted"
}
```

**Not Found (404)**:
```
)]}'
{
   "message": "No subscriber found for topic: non-existent-topic"
}
```

SEE ALSO
--------

* [POST replay-events](rest-api-replay-events.html)
* [Plugin Configuration](config.html)
* [Plugin Development](../../../Documentation/dev-plugins.html)
* [REST API Protocol Details](../../../Documentation/rest-api.html#_protocol_details)

GERRIT
------
Part of [Gerrit Code Review](../../../Documentation/index.html)
