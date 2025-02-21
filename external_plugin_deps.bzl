load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "amqp_client",
        artifact = "com.rabbitmq:amqp-client:5.13.0",
        sha1 = "ee1119a0a9ca86075d694859979cfaed0b579091",
    )

    maven_jar(
        name = "stream_client",
        artifact = "com.rabbitmq:stream-client:0.24.0",
        sha1 = "561c4ba357745d109850ce8fa77ba0c418025956",
    )

    maven_jar(
        name = "io_netty",
        artifact = "io.netty:netty-all:4.2.0.Final",
        sha1 = "9fabfd2cecd0b8b01ba00bf10f8c7252fc641b53",
    )

    maven_jar(
        name = "xerial_snappy",
        artifact = "org.xerial.snappy:snappy-java:1.1.10.7",
        sha1 = "3049f95640f4625a945cfab85715f603fa4c8f80",
    )

    maven_jar(
        name = "lz4",
        artifact = "org.lz4:lz4-java:1.8.0",
        sha1 = "4b986a99445e49ea5fbf5d149c4b63f6ed6c6780",
    )

    maven_jar(
        name = "zstd_jni",
        artifact = "com.github.luben:zstd-jni:1.5.6-9",
        sha1 = "69cdf67db2077e677113172f5b9cb30a15cffc05",
    )

    maven_jar(
        name = "proton_j",
        artifact = "org.apache.qpid:proton-j:0.34.1",
        sha1 = "e0d6c62cef4929db66dd6df55bee699b2274a9cc",
    )
