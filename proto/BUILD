load("@rules_proto//proto:defs.bzl", "proto_library")
load("@rules_java//java:defs.bzl", "java_proto_library")

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
