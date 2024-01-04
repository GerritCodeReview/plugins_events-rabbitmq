Build
=====

This @PLUGIN@ plugin is built with Bazel.

Clone (or link) this plugin to the `plugins` directory of Gerrit's source tree.

Put the external dependency Bazel build file into the Gerrit /plugins directory,
replacing the existing empty one. The rabbitMQ broker implementation depends on [events-broker](https://gerrit.googlesource.com/modules/events-broker)
which is linked directly from source with the same 'in-tree' plugin structure.
```
  cd gerrit/plugins
  git clone "https://gerrit.googlesource.com/modules/events-broker"
  rm external_plugin_deps.bzl
  ln -s @PLUGIN@/external_plugin_deps.bzl .
```

Then issue

```
  bazel build plugins/@PLUGIN@
```

in the root of Gerrit's source tree to build

The output is created in

```
  bazel-bin/plugins/@PLUGIN@/@PLUGIN@.jar
```

This project can be imported into the Eclipse IDE.
Add the plugin name to the `CUSTOM_PLUGINS` set in
Gerrit core in `tools/bzl/plugins.bzl`, and execute:

```
  ./tools/eclipse/project.py
```

Use RabbitMQBroker Api with multi-site or another setup with custom Gerrit events
------------------------------------------------------------------------------------

To make events-rabbitmq able to deserialize events from the rabbitMQ queues, every event type needs
to be registered before. This means that every plugin that needs to register its own event types
needs to load before events-rabbimq. Gerrit load plugins lexicographically based on the names of
the jars of the plugins. So in the case of multi-site you could rename replication.jar to
0-replication.jar. Do not forget to re-point any symlinks.
```
  mv gerrit/plugins/replication.jar gerrit/plugins/0-replication.jar
```
