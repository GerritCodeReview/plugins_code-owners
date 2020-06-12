load("@rules_proto//proto:defs.bzl", "proto_library")
load("@rules_java//java:defs.bzl", "java_proto_library")

package(default_visibility = ["//plugins/code-owners:visibility"])

proto_library(
    name = "owners_metadata_proto",
    srcs = ["owners_metadata.proto"],
)

java_proto_library(
    name = "owners_metadata_java_proto",
    visibility = ["//visibility:public"],
    deps = [":owners_metadata_proto"],
)
