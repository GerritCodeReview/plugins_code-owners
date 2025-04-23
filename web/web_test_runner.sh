#!/bin/bash

set -euo pipefail
./$1 --config $2 \
  --dir 'plugins/code-owners/web/_bazel_ts_out_tests' \
  --test-files 'plugins/code-owners/web/_bazel_ts_out_tests/*_test.js' \
  --type-config="plugins/code-owners/web/tsconfig.json"
