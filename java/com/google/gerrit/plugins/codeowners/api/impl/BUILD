load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS_NEVERLINK")

java_library(
    name = "impl",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = PLUGIN_DEPS_NEVERLINK + [
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/api",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/restapi",
    ],
)
