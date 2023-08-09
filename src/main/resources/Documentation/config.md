RabbitMQ Configuration
======================

Some parameters can be configured using config files.

Directory
---------------------

To setup a publisher that just publish stream events to a specific exchange on a specifc RabbitMQ
host you create a config file at `$site_path/data/@PLUGIN@/site`.
File extension must be `.config`.
Connection to RabbitMQ will be established for each files. If no config files are located in this
directory no connection with this type of publisher will be established.

If `@PLUGIN@.config` exists in `$site_path/data/@PLUGIN@`, it is loaded at first.
It means that this is default for all config files, including the events-broker API config file.


Broker.config
---------------------

To make specific configurations for the events-broker API you do those in
`$site_path/data/@PLUGIN@/broker/broker.config`. You can use the same configuration options as the
other configs with the additions of queuePrefix, durable, exclusive and autoDelete that decides
queue properties. The event-broker API use its own publisher that is separate from the previously
mentioned publisher.

Secure.config
---------------------

If amqp.<username>.password is specified in `$site_path/etc/secure.config`. This
password is used when trying to connect to amqp with username = <username>.

```
  [amqp "guest"]
    password = guest
```

File format
---------------------

```
  [amqp]
    uri = amqp://localhost
    username = guest
    password = guest
  [exchange]
    name = exchange-for-gerrit-queue
  [message]
    deliveryMode = 1
    priority = 0
    routingKey = com.foobar.www.gerrit
  [gerrit]
    name = foobar-gerrit
    hostname = www.foobar.com
    scheme = ssh
    port = 29418
    listenAs = gerrituser
  [monitor]
    interval = 15000
    failureCount = 15
```

* `amqp.uri`
    * The URI of RabbitMQ server's endpoint.

* `amqp.username`
    * Username for RabbitMQ connection authentication.

* `amqp.password`
    * Password for RabbitMQ connection authentication.

* `amqp.queuePrefix`
    * If set the queues that store the messages of the the subscribed topics will be named
    prefix + '.' + topic, otherwise the queues will get a random name decided by RabbitMQ. Only
    used in broker.config.

* `amqp.durable`
    * Make queues persistant on disk. So they stick even after a restart of RabbitMQ. Only used in
    broker.config and is only used if `amqp.queuePrefix` is specified.

* `amqp.exclusive`
    * Make the queues only usable by their declaring connection. Only used in broker.config and is
    only used if `amqp.queuePrefix` is specified.

* `amqp.autoDelete`
    * Make the queues automatically deleted when their last consummer stop subscribing. Only used
    in broker.config and is only used if `amqp.queuePrefix` is specified.

* `exchange.name`
    * The name of exchange.

* `general.publishAllGerritEvents`
    * Will publish gerrit stream events to configured exchange automatically if enabled, defaults
      to true.

* `general.enableBrokerApi`
    * Enable the RabbitMQ Broker API, defaults to false.

* `message.deliveryMode`
    * The delivery mode. if not specified, defaults to 1.
        * 1 - non-persistent
        * 2 - persistent

* `message.priority`
    * The priority of message. if not specified, defaults to 0.

* `message.routingKey`
    * The name of routingKey. This is stored to message property. If not specified, defaults to
      the type of the Gerrit event (e.g. "patchset-created", "change-merged").

* `message.publishConfirm`
    * Enable reliable publishing with acking when a event is published.

* `gerrit.name`
    * The name of gerrit(not hostname). This is your given name to identify your gerrit.
      This can be used for message header only.

* `gerrit.hostname`
    * The hostname of gerrit for SCM connection.
      This can be used for message header only.

* `gerrit.scheme`
    * The scheme of gerrit for SCM connection.
      This can be used for message header only.

* `gerrit.port`
    * The port number of gerrit for SCM connection.
      This can be used for message header only.

* `gerrit.listenAs`
    * The user of gerrit who listen events.
      If not specified, listen events as unrestricted user. This is not applicable for the RabbitMQ broker API.

* `monitor.interval`
    * The interval time in milliseconds for connection monitor.
      You can specify the value more than 5000.

* `monitor.failureCount`
    * The count of failure. If the command for publishing message failed in the specified number of times
      in succession, connection will be renewed.

Default Values
-----------------

You can change the below values by specifying them in config file.

**Bold** is String value.

|name                 | value
|:--------------------|:------------------
|amqp.uri             | **amqp://localhost**
|amqp.username        | **guest**
|amqp.password        | **guest**
|exchange.name        | **gerrit.publish**
|message.deliveryMode | 1
|message.priority     | 0
|message.routingKey   | **event.type**
|gerrit.name          | *Empty*
|gerrit.hostname      | *Empty*
|gerrit.scheme        | **ssh**
|gerrit.port          | 29418
|gerrit.listenAs      | *Unrestricted user*
|monitor.interval     | 15000
|monitor.failureCount | 15
