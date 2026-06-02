<<<<<<< HEAD   (9db6b62d50424668870bba8b0070063381cfba00 docs: fix stray `)
load("@rules_proto//proto:defs.bzl", "proto_library")
load("@rules_java//java:defs.bzl", "java_proto_library")
||||||| BASE   (5fdb2b38927760d8adf1709ad464ee24f2a751ca Adapt to usage of aspect_rules_*)
load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@rules_proto//proto:defs.bzl", "proto_library")
=======
load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
>>>>>>> CHANGE (507f11a3901cb0fc2d41ab28c909b39df8a5e04c Adapt to recent upstream tooling migrations)

proto_library(
    name = "owners_metadata_proto",
    srcs = ["owners_metadata.proto"],
)

java_proto_library(
    name = "owners_metadata_java_proto",
    visibility = [
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/api:__pkg__",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/backend:__pkg__",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/restapi:__pkg__",
    ],
    deps = [":owners_metadata_proto"],
)
