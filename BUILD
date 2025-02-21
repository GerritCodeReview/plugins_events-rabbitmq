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
        "@stream_client//jar",
        "@zstd_jni//jar",
        "@netty_buffer//jar",
        "@netty_codec//jar",
        "@netty_codec_base//jar",
        "@netty_codec_compression//jar",
        "@netty_codec_marshalling//jar",
        "@netty_codec_protobuf//jar",
        "@netty_common//jar",
        "@netty_handler//jar",
        "@netty_resolver//jar",
        "@netty_transport//jar",
        "@netty_transport_native_unix_common//jar",
        "@proton_j//jar",
        "@lz4_java//jar",
        "@slf4j_api//jar",
        "@snappy_java//jar",
        "@commons-codec//jar:neverlink",
        "@commons-io//jar",
        "@commons-lang3//jar:neverlink",
        "@gson//jar:neverlink",
    ],
)

java_library(
    name = "events-broker-neverlink",
    neverlink = 1,
    exports = ["//plugins/events-broker"],
)
