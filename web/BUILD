load("//tools/js:eslint.bzl", "plugin_eslint")
load("//tools/bzl:js.bzl", "gerrit_js_bundle", "web_test_runner")
load("@npm//@bazel/typescript:index.bzl", "ts_config", "ts_project")

package_group(
    name = "visibility",
    packages = ["//plugins/code-owners/..."],
)

package(default_visibility = [":visibility"])

ts_config(
    name = "tsconfig",
    src = "tsconfig.json",
    deps = [
        "//plugins:tsconfig-plugins-base.json",
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
    tsc = "//tools/node_tools:tsc-bin",
    tsconfig = ":tsconfig",
    deps = [
        "@plugins_npm//@gerritcodereview/typescript-api",
        "@plugins_npm//lit",
        "@plugins_npm//rxjs",
    ],
)

ts_project(
    name = "code-owners-ts-tests",
    srcs = glob(["**/*.ts"]),
    incremental = True,
    out_dir = "_bazel_ts_out_tests",
    tsc = "//tools/node_tools:tsc-bin",
    tsconfig = ":tsconfig",
    deps = [
        "@plugins_npm//:node_modules",
        "@ui_dev_npm//:node_modules",
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
        "@plugins_npm//:node_modules",
    ],
)

plugin_eslint()
