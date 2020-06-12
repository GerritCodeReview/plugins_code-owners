load("@rules_proto//proto:defs.bzl", "proto_library")
load("@rules_java//java:defs.bzl", "java_proto_library")

package(default_visibility = ["//plugins/code-owners:visibility"])

proto_library(
    name = "owner_metadata_proto",
    srcs = ["owner_metadata_proto.proto"],
)

java_proto_library(
    name = "owner_metadata_java_proto",
    visibility = ["//visibility:public"],
    deps = [":owner_metadata_proto"],
)
