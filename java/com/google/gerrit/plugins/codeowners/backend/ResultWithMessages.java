// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins.codeowners.backend;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * A result of an operation with optional messages.
 *
 * @param <T> type of the result
 */
@AutoValue
public abstract class ResultWithMessages<T> {
  /** Gets the result. */
  public abstract T get();

  /** Gets the messages. */
  public abstract ImmutableList<String> messages();

  /** Creates a {@link ResultWithMessages} instance without messages. */
  public static <T> ResultWithMessages<T> create(T result) {
    return create(result, ImmutableList.of());
  }

  /** Creates a {@link ResultWithMessages} instance with a single message. */
  public static <T> ResultWithMessages<T> create(T result, String message) {
    requireNonNull(message, "message");
    return create(result, ImmutableList.of(message));
  }

  /** Creates a {@link ResultWithMessages} instance with messages. */
  public static <T> ResultWithMessages<T> create(T result, List<String> messages) {
    requireNonNull(result, "result");
    requireNonNull(messages, "messages");
    return new AutoValue_ResultWithMessages<>(result, ImmutableList.copyOf(messages));
  }
}
