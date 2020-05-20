load("@rules_java//java:defs.bzl", "java_library")

package(
    default_testonly = True,
    default_visibility = ["//plugins/code-owners:visibility"],
)

java_library(
    name = "testing",
    srcs = glob(["*.java"]),
    deps = [
        "//lib/truth",
        "//plugins/code-owners:code-owners__plugin",
    ],
)

