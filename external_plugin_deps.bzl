load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "amqp_client",
        artifact = "com.rabbitmq:amqp-client:5.10.0",
        sha1 = "4de351467a13b8ca4eb7e8023032f9f964a21796",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.7.2",
        sha1 = "3b387b3bd134bed3e4bbd743a69411b05c86461a",
    )
