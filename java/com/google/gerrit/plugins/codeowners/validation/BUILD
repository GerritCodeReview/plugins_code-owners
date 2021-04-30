load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS_NEVERLINK")

java_library(
    name = "validation",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = PLUGIN_DEPS_NEVERLINK + [
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/backend",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/common",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/metrics",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/util",
    ],
)
