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

package com.google.gerrit.plugins.codeowners.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.gerrit.truth.OptionalSubject.optionals;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.backend.DebugMessage;
import com.google.gerrit.plugins.codeowners.backend.OptionalResultWithMessages;
import java.util.Optional;

/** {@link Subject} for doing assertions on {@link OptionalResultWithMessages}s. */
public class OptionalResultWithMessagesSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link OptionalResultWithMessages}.
   *
   * @param optionalResultWithMessages the optionalResultWithMessages instance on which assertions
   *     should be done
   * @return the created {@link OptionalResultWithMessagesSubject}
   */
  public static OptionalResultWithMessagesSubject assertThat(
      OptionalResultWithMessages<?> optionalResultWithMessages) {
    return assertAbout(optionalResultsWithMessages()).that(optionalResultWithMessages);
  }

  /**
   * Creates subject factory for mapping {@link OptionalResultWithMessages}s to {@link
   * OptionalResultWithMessagesSubject}s.
   */
  private static Subject.Factory<OptionalResultWithMessagesSubject, OptionalResultWithMessages<?>>
      optionalResultsWithMessages() {
    return OptionalResultWithMessagesSubject::new;
  }

  private final OptionalResultWithMessages<?> optionalResultWithMessages;

  private OptionalResultWithMessagesSubject(
      FailureMetadata metadata, OptionalResultWithMessages<?> optionalResultWithMessages) {
    super(metadata, optionalResultWithMessages);
    this.optionalResultWithMessages = optionalResultWithMessages;
  }

  public void isEmpty() {
    check("result()").about(optionals()).that(optionalResultWithMessages().result()).isEmpty();
  }

  public OptionalResultWithMessagesSubject assertContainsAdminOnlyMessage(
      String expectedAdminMessage) {
    hasAdminMessagesThat().contains(expectedAdminMessage);
    hasUserMessagesThat().doesNotContain(expectedAdminMessage);
    return this;
  }

  public OptionalResultWithMessagesSubject assertContainsMessage(String expectedMessage) {
    hasAdminMessagesThat().contains(expectedMessage);
    hasUserMessagesThat().contains(expectedMessage);
    return this;
  }

  public OptionalResultWithMessagesSubject assertContainsMessage(
      String expectedAdminMessage, String expectedUserMessage) {
    hasAdminMessagesThat().contains(expectedAdminMessage);
    hasUserMessagesThat().contains(expectedUserMessage);
    return this;
  }

  public OptionalResultWithMessagesSubject assertContainsExactlyMessage(String expectedMessage) {
    hasAdminMessagesThat().containsExactly(expectedMessage);
    hasUserMessagesThat().containsExactly(expectedMessage);
    return this;
  }

  public IterableSubject hasAdminMessagesThat() {
    return check("messages()")
        .that(
            optionalResultWithMessages().messages().stream()
                .map(DebugMessage::adminMessage)
                .collect(toImmutableList()));
  }

  public IterableSubject hasUserMessagesThat() {
    return check("messages()")
        .that(
            optionalResultWithMessages().messages().stream()
                .map(DebugMessage::userMessage)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toImmutableList()));
  }

  private OptionalResultWithMessages<?> optionalResultWithMessages() {
    isNotNull();
    return optionalResultWithMessages;
  }
}
