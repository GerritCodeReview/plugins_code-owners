package_group(
    name = "visibility",
    packages = ["//plugins/code-owners/..."],
)

package(default_visibility = [":visibility"])

load(
    "//tools/bzl:plugin.bzl",
    "gerrit_plugin",
)
load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:js.bzl", "polygerrit_plugin")
load("@npm//@bazel/rollup:index.bzl", "rollup_bundle")

gerrit_plugin(
    name = "code-owners",
    srcs = glob(["java/com/google/gerrit/plugins/codeowners/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: code-owners",
        "Gerrit-Module: com.google.gerrit.plugins.codeowners.Module",
        "Gerrit-HttpModule: com.google.gerrit.plugins.codeowners.HttpModule",
    ],
    resource_jars = [":code-owners-fe-static"],
    resource_strip_prefix = "plugins/code-owners/resources",
    resources = glob(["resources/**/*"]),
    deps = ["//plugins/code-owners/proto:owners_metadata_java_proto"],
)

polygerrit_plugin(
    name = "code-owners-fe",
    app = "plugin-bundle.js",
    plugin_name = "code-owners",
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

genrule2(
    name = "code-owners-fe-static",
    srcs = [":code-owners-fe"],
    outs = ["code-owners-fe-static.jar"],
    cmd = " && ".join([
        "mkdir $$TMP/static",
        "cp -r $(locations :code-owners-fe) $$TMP/static",
        "cd $$TMP",
        "zip -Drq $$ROOT/$@ -g .",
    ]),
)