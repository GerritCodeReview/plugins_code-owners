load("@aspect_rules_ts//ts:defs.bzl", "ts_config", "ts_project")
load("@com_googlesource_gerrit_bazlets//js:defs.bzl", "gerrit_js_bundle")
load("//tools/bzl:js.bzl", "web_test_runner")
load("//tools/js:eslint.bzl", "plugin_eslint")

package_group(
    name = "visibility",
    packages = ["//plugins/code-owners/..."],
)

package(default_visibility = [":visibility"])

ts_config(
    name = "tsconfig",
    src = "tsconfig.json",
    deps = [
        "//plugins:tsconfig-plugins-base",
    ],
)

ts_project(
    name = "code-owners-ts",
    srcs = glob(
        ["**/*.ts"],
        exclude = ["**/*test*"],
    ),
    incremental = True,
    out_dir = "_bazel_ts_out",
    tsconfig = ":tsconfig",
    deps = [
        "//plugins:node_modules",
    ],
)

ts_project(
    name = "code-owners-ts-tests",
    srcs = glob(["**/*.ts"]),
    incremental = True,
    out_dir = "_bazel_ts_out_tests",
    tsconfig = ":tsconfig",
    tags = ["manual"],
    deps = [
        "//plugins:node_modules",
        "//polygerrit-ui:node_modules",
    ],
)

gerrit_js_bundle(
    name = "code-owners",
    srcs = [":code-owners-ts"],
    entry_point = "_bazel_ts_out/plugin.js",
)

filegroup(
    name = "tsconfig-sources",
    srcs = glob(["**/tsconfig.json"])
)

web_test_runner(
    name = "web_test_runner",
    srcs = ["web_test_runner.sh"],
    data = [
        ":tsconfig-sources",
        ":code-owners-ts-tests",
        "//plugins:node_modules",
    ],
)

plugin_eslint()
