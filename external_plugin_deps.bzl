load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "amqp_client",
        artifact = "com.rabbitmq:amqp-client:5.10.0",
        sha1 = "4de351467a13b8ca4eb7e8023032f9f964a21796",
    )

    maven_jar(
        name = "stream_client",
        artifact = "com.rabbitmq:stream-client:1.1.0",
        sha1 = "585736dd2da4d3da6e72adeef4103cdb362435eb",
    )

    # Transitive dependencies for stream-client below
    maven_jar(
        name = "zstd_jni",
        artifact = "com.github.luben:zstd-jni:1.5.7-3",
        sha1 = "dc55c256583fb810f9fe3ede271843b68132fb7c",
    )

    maven_jar(
        name = "netty_buffer",
        artifact = "io.netty:netty-buffer:4.2.1.Final",
        sha1 = "095e48c931eb31614c06adf775a2118f4297fd97",
    )

    maven_jar(
        name = "netty_codec",
        artifact = "io.netty:netty-codec:4.2.1.Final",
        sha1 = "55421a0c16ccd25692613e86f75b5a3f8db3ea82",
    )

    maven_jar(
        name = "netty_codec_base",
        artifact = "io.netty:netty-codec-base:4.2.1.Final",
        sha1 = "ee2a21f30549d6fb0ec1da71ac748c385368d807",
    )

    maven_jar(
        name = "netty_codec_compression",
        artifact = "io.netty:netty-codec-compression:4.2.1.Final",
        sha1 = "b85ef35b94d1b540c021df6bb778cc8dc086588b",
    )

    maven_jar(
        name = "netty_codec_marshalling",
        artifact = "io.netty:netty-codec-marshalling:4.2.1.Final",
        sha1 = "838fd5dc1ae854879a9e553a0d217abfd5d7fda9",
    )

    maven_jar(
        name = "netty_codec_protobuf",
        artifact = "io.netty:netty-codec-protobuf:4.2.1.Final",
        sha1 = "086c04147717b63edadb9e2d1bd371f6091e2ba0",
    )

    maven_jar(
        name = "netty_common",
        artifact = "io.netty:netty-common:4.2.1.Final",
        sha1 = "ed99ce89380bc3e9b297c9a5d87fbb6e669e1cf4",
    )

    maven_jar(
        name = "netty_handler",
        artifact = "io.netty:netty-handler:4.2.1.Final",
        sha1 = "1673ad3e66acb5bfaa2dab839ee0d6b22225fbee",
    )

    maven_jar(
        name = "netty_resolver",
        artifact = "io.netty:netty-resolver:4.2.1.Final",
        sha1 = "80a111e04ee696cca0fe928b7a64787551a848e4",
    )

    maven_jar(
        name = "netty_transport",
        artifact = "io.netty:netty-transport:4.2.1.Final",
        sha1 = "5c9140fffd02fe833a570dfd00c3dced88c72abd",
    )

    maven_jar(
        name = "netty_transport_native_unix_common",
        artifact = "io.netty:netty-transport-native-unix-common:4.2.1.Final",
        sha1 = "9edcc29edb12cb33890e6de025fbb60cc3846d6d",
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
