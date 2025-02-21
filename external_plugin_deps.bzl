load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "amqp_client",
        artifact = "com.rabbitmq:amqp-client:5.10.0",
        sha1 = "4de351467a13b8ca4eb7e8023032f9f964a21796",
    )

    maven_jar(
        name = "stream_client",
        artifact = "com.rabbitmq:stream-client:0.22.0",
        sha1 = "3501f9fd4ebe31f2bb15f2254cbc305ce6d1eb57",
    )

    # Transitive dependencies for stream-client below
    maven_jar(
        name = "zstd_jni",
        artifact = "com.github.luben:zstd-jni:1.5.6-9",
        sha1 = "69cdf67db2077e677113172f5b9cb30a15cffc05",
    )

    maven_jar(
        name = "netty_buffer",
        artifact = "io.netty:netty-buffer:4.1.117.Final",
        sha1 = "022b4cc28194cb23671274499229e0ef35028fbd",
    )

    maven_jar(
        name = "netty_codec",
        artifact = "io.netty:netty-codec:4.1.117.Final",
        sha1 = "2831d3431ed93d9c0b64b1c0cce2ced4737539aa",
    )

    maven_jar(
        name = "netty_common",
        artifact = "io.netty:netty-common:4.1.117.Final",
        sha1 = "9e074a4382f56b37f3b9ee1fc21d53e7af58ec9d",
    )

    maven_jar(
        name = "netty_handler",
        artifact = "io.netty:netty-handler:4.1.117.Final",
        sha1 = "db14cd99515f8c98a3f2a347718e59f14d85c503",
    )

    maven_jar(
        name = "netty_resolver",
        artifact = "io.netty:netty-resolver:4.1.117.Final",
        sha1 = "581b37489a03162f473264b65f53d504269a74b0",
    )

    maven_jar(
        name = "netty_transport",
        artifact = "io.netty:netty-transport:4.1.117.Final",
        sha1 = "f81d72962bd134d8d8e11b514321134fa5fd0ce6",
    )

    maven_jar(
        name = "netty_transport_native",
        artifact = "io.netty:netty-transport-native-unix-common:4.1.117.Final",
        sha1 = "684f2316ff2b2171babbc17c95ac3bd97f5f091e",
    )

    maven_jar(
        name = "proton_j",
        artifact = "org.apache.qpid:proton-j:0.34.1",
        sha1 = "e0d6c62cef4929db66dd6df55bee699b2274a9cc",
    )

    maven_jar(
        name = "lz4_java",
        artifact = "org.lz4:lz4-java:1.8.0",
        sha1 = "4b986a99445e49ea5fbf5d149c4b63f6ed6c6780",
    )

    maven_jar(
        name = "slf4j_api",
        artifact = "org.slf4j:slf4j-api:1.7.36",
        sha1 = "6c62681a2f655b49963a5983b8b0950a6120ae14",
    )

    maven_jar(
        name = "snappy_java",
        artifact = "org.xerial.snappy:snappy-java:1.1.10.7",
        sha1 = "3049f95640f4625a945cfab85715f603fa4c8f80",
    )
