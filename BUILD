package_group(
    name = "visibility",
    packages = ["//plugins/code-owners/..."],
)

package(default_visibility = [":visibility"])

load(
    "//tools/bzl:plugin.bzl",
    "gerrit_plugin",
)
load("//tools/bzl:js.bzl", "polygerrit_plugin")
load("@npm_bazel_rollup//:index.bzl", "rollup_bundle")

gerrit_plugin(
    name = "code-owners",
    srcs = glob(["java/com/google/gerrit/plugins/codeowners/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: code-owners",
        "Gerrit-Module: com.google.gerrit.plugins.codeowners.Module",
    ],
    resource_strip_prefix = "plugins/code-owners/resources",
    resources = glob(["resources/**/*"]),
)


polygerrit_plugin(
    name = "code-owners-fe",
    plugin_name = "code-owners",
    app = "plugin-bundle.js",
)

rollup_bundle(
    name = "plugin-bundle",
    srcs = glob([
        "ui/**/*.js",
    ]),
    entry_point = "ui/plugin.js",
    format = "iife",
    rollup_bin = "//tools/node_tools:rollup-bin",
    sourcemap = "hidden",
    deps = [
        "@tools_npm//rollup-plugin-node-resolve",
    ],
)