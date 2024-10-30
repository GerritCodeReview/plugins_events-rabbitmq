Message Format
======================

This plugin publish message like the below format to RabbitMQ.

*Italic* is key name in `@PLUGIN@.config`.

**Bold** is literal.

Properties
-----------------------

Properties are stored as message property.

| name             | value
|:-----------------|:--------------------------
| app_id           | **gerrit**
| priority         | *message.priority*
| delivery_mode    | *message.deliveryMode*
| headers          | &lt;See Headers section&gt;
| content_encoding | **UTF-8**
| content_type     | **application/json**


Headers
-----------------------

Headers are stored in property.headers.

| name             | value
|:-----------------|:------------------------------------------
| gerrit-name      | *gerrit.name*
| gerrit-front-url | *gerrit.canonicalWebUrl* in `gerrit.config`
| gerrit-version   | gerrit version


Payload
-----------------------

Payload is JSON string. (same gerrit-events)
