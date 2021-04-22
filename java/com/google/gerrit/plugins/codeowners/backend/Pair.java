// Copyright (C) 2021 The Android Open Source Project
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

import com.google.auto.value.AutoValue;

/**
 * Key-value pair.
 *
 * @param <K> the type of the key
 * @param <V> the type of the value
 */
@AutoValue
public abstract class Pair<K, V> {

  public abstract K key();

  public abstract V value();

  public static <K, V> Pair<K, V> create(K key, V value) {
    return new AutoValue_Pair<>(key, value);
  }
}
