load("//tools/bzl:plugin.bzl", "gerrit_plugin")

gerrit_plugin(
    name = "events-rabbitmq",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: events-rabbitmq",
        "Gerrit-Module: com.googlesource.gerrit.plugins.rabbitmq.Module",
        "Implementation-Title: Gerrit events-rabbitmq plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/events-rabbitmq",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        ":events-broker-neverlink",
        "@amqp_client//jar",
        "@commons-codec//jar:neverlink",
        "@commons-io//jar",
        "@commons-lang3//jar:neverlink",
        "@gson//jar:neverlink",
    ],
)

java_library(
    name = "events-broker-neverlink",
    neverlink = 1,
    exports = ["//modules/events-broker"],
)
