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
import java.util.Map;

/**
 * Immutable implementation of a {@link java.util.Map.Entry}.
 *
 * <p>Immutable means that the entry value cannot be updated via the {@link #setValue(Object)}
 * method. Any attempt to do so results in a {@link IllegalStateException}.
 *
 * <p>By using this class, map entries in a stream can be mapped to map entries with another key
 * type and/or value type. E.g.:
 *
 * <pre>
 *   Stream<Map.Entry<String, Foo>> streamFoo = ...
 *   Stream<Map.Entry<String, Bar>> streamBar =
 *       streamFoo.map(entry -> ImmutableMapEntry.create(entry.getKey(),
 *           convertFooToBar(entry.getValue())));
 * </pre>
 *
 * <p>Without this class (or any other instantiatable {@link java.util.Map.Entry} implementation)
 * such a mapping would require collecting the entries of the stream and then creating a new stream,
 * which would be inefficient:
 *
 * <pre>
 *   Stream<Map.Entry<String, Foo>> streamFoo = ...
 *   Stream<Map.Entry<String, Bar>> streamBar =
 *       streamFoo.collect(ImmutableMap.toImmutableMap(Function.identity(),
 *               entry -> convertFooToBar(entry.getValue())))
 *           .entrySet()
 *           .stream();
 * </pre>
 *
 * @param <K> the type of the key
 * @param <V> the type of the value
 */
@AutoValue
public abstract class ImmutableMapEntry<K, V> implements Map.Entry<K, V> {

  @Override
  public abstract K getKey();

  @Override
  public abstract V getValue();

  @Override
  public V setValue(V value) {
    throw new IllegalStateException("value of immutable map entry cannot be updated");
  }

  public static <K, V> ImmutableMapEntry<K, V> create(K key, V value) {
    return new AutoValue_ImmutableMapEntry<>(key, value);
  }
}
