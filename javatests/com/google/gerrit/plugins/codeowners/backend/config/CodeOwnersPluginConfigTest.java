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

package com.google.gerrit.plugins.codeowners.backend.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.inject.Inject;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnersPluginConfig}. */
public class CodeOwnersPluginConfigTest extends AbstractCodeOwnersTest {
  private static final String SECTION = CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS;
  private static final String SUBSECTION = "subsection";
  private static final String SUBSECTION_2 = "subsection2";
  private static final String SUBSECTION_3 = "subsection3";
  private static final String KEY = "key";
  private static final String VALUE = "foo";
  private static final String VALUE_2 = "bar";
  private static final String VALUE_3 = "baz";
  private static final String VALUE_4 = "foo_bar";
  private static final String VALUE_5 = "foo_baz";
  private static final String VALUE_6 = "bar_foo";

  @Inject private ProjectOperations projectOperations;

  private CodeOwnersPluginConfig.Factory codeOwnersPluginConfigFactory;
  private Project.NameKey parent;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnersPluginConfigFactory =
        plugin.getSysInjector().getInstance(CodeOwnersPluginConfig.Factory.class);
  }

  @Before
  public void setUpProject() throws Exception {
    parent = project;
    project = projectOperations.newProject().parent(parent).create();
  }

  @Test
  public void getSingleValue_noValueSet() throws Exception {
    assertThat(cfg().getString(SECTION, /* subsection= */ null, KEY)).isNull();
  }

  @Test
  public void getSingleValue_singleValueSet() throws Exception {
    setSingleValue(project, VALUE);
    assertThat(cfg().getString(SECTION, /* subsection= */ null, KEY)).isEqualTo(VALUE);
  }

  @Test
  public void getSingleValue_multiValueSet() throws Exception {
    setMultiValue(project, VALUE, VALUE_2, VALUE_3);

    // last value takes precedence
    assertThat(cfg().getString(SECTION, /* subsection= */ null, KEY)).isEqualTo(VALUE_3);
  }

  @Test
  public void getSingleValue_singleValueSetForParent() throws Exception {
    setSingleValue(parent, VALUE);
    assertThat(cfg().getString(SECTION, /* subsection= */ null, KEY)).isEqualTo(VALUE);
  }

  @Test
  public void getSingleValue_multiValueSetForParent() throws Exception {
    setMultiValue(parent, VALUE, VALUE_2, VALUE_3);

    // last value takes precedence
    assertThat(cfg().getString(SECTION, /* subsection= */ null, KEY)).isEqualTo(VALUE_3);
  }

  @Test
  public void getSingleValue_valueOverridesSingleParentValues() throws Exception {
    setSingleValue(allProjects, VALUE);
    setSingleValue(parent, VALUE_2);
    setSingleValue(project, VALUE_3);
    assertThat(cfg().getString(SECTION, /* subsection= */ null, KEY)).isEqualTo(VALUE_3);
  }

  @Test
  public void getSingleValue_valueOverridesMultiParentValues() throws Exception {
    setMultiValue(allProjects, VALUE, VALUE_2);
    setMultiValue(parent, VALUE_3, VALUE_4);
    setSingleValue(project, VALUE_5);
    assertThat(cfg().getString(SECTION, /* subsection= */ null, KEY)).isEqualTo(VALUE_5);
  }

  @Test
  public void getSingleValue_unsetSingleParentValues() throws Exception {
    setSingleValue(allProjects, VALUE);
    setSingleValue(parent, VALUE_2);
    setSingleValue(project, "");
    assertThat(cfg().getString(SECTION, /* subsection= */ null, KEY)).isNull();
  }

  @Test
  public void getSingleValue_unsetMultiParentValues() throws Exception {
    setMultiValue(allProjects, VALUE, VALUE_2);
    setMultiValue(parent, VALUE_3, VALUE_4);
    setSingleValue(project, "");
    assertThat(cfg().getString(SECTION, /* subsection= */ null, KEY)).isNull();
  }

  @Test
  public void getSingleValueFromSubsection_noValueSet() throws Exception {
    assertThat(cfg().getString(SECTION, SUBSECTION, KEY)).isNull();
  }

  @Test
  public void getSingleValueFromSubsection_singleValueSet() throws Exception {
    setSingleValueForSubsection(project, SUBSECTION, VALUE);
    assertThat(cfg().getString(SECTION, SUBSECTION, KEY)).isEqualTo(VALUE);
  }

  @Test
  public void getSingleValueFromSubsection_multiValueSet() throws Exception {
    setMultiValueForSubsection(project, SUBSECTION, VALUE, VALUE_2, VALUE_3);

    // last value takes precedence
    assertThat(cfg().getString(SECTION, SUBSECTION, KEY)).isEqualTo(VALUE_3);
  }

  @Test
  public void getSingleValueFromSubsection_singleValueSetForParent() throws Exception {
    setSingleValueForSubsection(parent, SUBSECTION, VALUE);
    assertThat(cfg().getString(SECTION, SUBSECTION, KEY)).isEqualTo(VALUE);
  }

  @Test
  public void getSingleValueFromSubsection_multiValueSetForParent() throws Exception {
    setMultiValueForSubsection(parent, SUBSECTION, VALUE, VALUE_2, VALUE_3);

    // last value takes precedence
    assertThat(cfg().getString(SECTION, SUBSECTION, KEY)).isEqualTo(VALUE_3);
  }

  @Test
  public void getSingleValueFromSubsection_valueOverridesSingleParentValues() throws Exception {
    setSingleValueForSubsection(allProjects, SUBSECTION, VALUE);
    setSingleValueForSubsection(parent, SUBSECTION, VALUE_2);
    setSingleValueForSubsection(project, SUBSECTION, VALUE_3);
    assertThat(cfg().getString(SECTION, SUBSECTION, KEY)).isEqualTo(VALUE_3);
  }

  @Test
  public void getSingleValueFromSubsection_valueOverridesMultiParentValues() throws Exception {
    setMultiValueForSubsection(allProjects, SUBSECTION, VALUE, VALUE_2);
    setMultiValueForSubsection(parent, SUBSECTION, VALUE_3, VALUE_4);
    setSingleValueForSubsection(project, SUBSECTION, VALUE_5);
    assertThat(cfg().getString(SECTION, SUBSECTION, KEY)).isEqualTo(VALUE_5);
  }

  @Test
  public void getSingleValueFromSubsection_unsetSingleParentValues() throws Exception {
    setSingleValueForSubsection(allProjects, SUBSECTION, VALUE);
    setSingleValueForSubsection(parent, SUBSECTION, VALUE_2);
    setSingleValueForSubsection(project, SUBSECTION, "");
    assertThat(cfg().getString(SECTION, SUBSECTION, KEY)).isNull();
  }

  @Test
  public void getSingleValueFromSubsection_unsetMultiParentValues() throws Exception {
    setMultiValueForSubsection(allProjects, SUBSECTION, VALUE, VALUE_2);
    setMultiValueForSubsection(parent, SUBSECTION, VALUE_3, VALUE_4);
    setSingleValueForSubsection(project, SUBSECTION, "");
    assertThat(cfg().getString(SECTION, SUBSECTION, KEY)).isNull();
  }

  @Test
  public void getMultiValue_noValueSet() throws Exception {
    assertThat(cfg().getStringList(SECTION, /* subsection= */ null, KEY)).isEmpty();
  }

  @Test
  public void getMultiValue_singleValueSet() throws Exception {
    setSingleValue(project, VALUE);
    assertThat(cfg().getStringList(SECTION, /* subsection= */ null, KEY))
        .asList()
        .containsExactly(VALUE);
  }

  @Test
  public void getMultiValue_multiValueSet() throws Exception {
    setMultiValue(project, VALUE, VALUE_2, VALUE_3);
    assertThat(cfg().getStringList(SECTION, /* subsection= */ null, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, VALUE_3)
        .inOrder();
  }

  @Test
  public void getMultiValue_singleValueSetForParent() throws Exception {
    setSingleValue(parent, VALUE);
    assertThat(cfg().getStringList(SECTION, /* subsection= */ null, KEY))
        .asList()
        .containsExactly(VALUE);
  }

  @Test
  public void getMultiValue_multiValueSetForParent() throws Exception {
    setMultiValue(parent, VALUE, VALUE_2, VALUE_3);
    assertThat(cfg().getStringList(SECTION, /* subsection= */ null, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, VALUE_3)
        .inOrder();
  }

  @Test
  public void getMultiValue_singleValueExtendsSingleParentValues() throws Exception {
    setSingleValue(allProjects, VALUE);
    setSingleValue(parent, VALUE_2);
    setSingleValue(project, VALUE_3);
    assertThat(cfg().getStringList(SECTION, /* subsection= */ null, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, VALUE_3)
        .inOrder();
  }

  @Test
  public void getMultiValue_singleValueOverridesMultiParentValues() throws Exception {
    setMultiValue(allProjects, VALUE, VALUE_2);
    setMultiValue(parent, VALUE_3, VALUE_4);
    setSingleValue(project, VALUE_5);
    assertThat(cfg().getStringList(SECTION, /* subsection= */ null, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, VALUE_3, VALUE_4, VALUE_5)
        .inOrder();
  }

  @Test
  public void getMultiValue_multiValueExtendsSingleParentValues() throws Exception {
    setSingleValue(allProjects, VALUE);
    setSingleValue(parent, VALUE_2);
    setMultiValue(project, VALUE_3, VALUE_4);
    assertThat(cfg().getStringList(SECTION, /* subsection= */ null, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, VALUE_3, VALUE_4)
        .inOrder();
  }

  @Test
  public void getMultiValue_multiValueExtendsMultiParentValues() throws Exception {
    setMultiValue(allProjects, VALUE, VALUE_2);
    setMultiValue(parent, VALUE_3, VALUE_4);
    setMultiValue(project, VALUE_5, VALUE_6);
    assertThat(cfg().getStringList(SECTION, /* subsection= */ null, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, VALUE_3, VALUE_4, VALUE_5, VALUE_6)
        .inOrder();
  }

  @Test
  public void getMultiValue_multiValueExtendsMultiParentValues_withDuplicates() throws Exception {
    setMultiValue(allProjects, VALUE, VALUE_2);
    setMultiValue(parent, VALUE_3);
    setMultiValue(project, VALUE, VALUE_2);
    assertThat(cfg().getStringList(SECTION, /* subsection= */ null, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, VALUE_3, VALUE, VALUE_2)
        .inOrder();
  }

  @Test
  public void getMultiValue_cannotUnsetSingleParentValues() throws Exception {
    setSingleValue(allProjects, VALUE);
    setSingleValue(parent, VALUE_2);
    setSingleValue(project, "");

    // the empty string is returned as null value in the list
    assertThat(cfg().getStringList(SECTION, /* subsection= */ null, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, null)
        .inOrder();
  }

  @Test
  public void getMultiValue_cannotUnsetMultiParentValues() throws Exception {
    setMultiValue(allProjects, VALUE, VALUE_2);
    setMultiValue(parent, VALUE_3, VALUE_4);
    setSingleValue(project, "");

    // the empty string is returned as null value in the list
    assertThat(cfg().getStringList(SECTION, /* subsection= */ null, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, VALUE_3, VALUE_4, null)
        .inOrder();
  }

  @Test
  public void getMultiValueFromSubsection_noValueSet() throws Exception {
    assertThat(cfg().getStringList(SECTION, SUBSECTION, KEY)).isEmpty();
  }

  @Test
  public void getMultiValueFromSubsection_singleValueSet() throws Exception {
    setSingleValueForSubsection(project, SUBSECTION, VALUE);
    assertThat(cfg().getStringList(SECTION, SUBSECTION, KEY)).asList().containsExactly(VALUE);
  }

  @Test
  public void getMultiValueFromSubsection_multiValueSet() throws Exception {
    setMultiValueForSubsection(project, SUBSECTION, VALUE, VALUE_2, VALUE_3);
    assertThat(cfg().getStringList(SECTION, SUBSECTION, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, VALUE_3)
        .inOrder();
  }

  @Test
  public void getMultiValueFromSubsection_singleValueSetForParent() throws Exception {
    setSingleValueForSubsection(parent, SUBSECTION, VALUE);
    assertThat(cfg().getStringList(SECTION, SUBSECTION, KEY)).asList().containsExactly(VALUE);
  }

  @Test
  public void getMultiValueFromSubsection_multiValueSetForParent() throws Exception {
    setMultiValueForSubsection(parent, SUBSECTION, VALUE, VALUE_2, VALUE_3);
    assertThat(cfg().getStringList(SECTION, SUBSECTION, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, VALUE_3)
        .inOrder();
  }

  @Test
  public void getMultiValueFromSubsection_singleValueExtendsSingleParentValues() throws Exception {
    setSingleValueForSubsection(allProjects, SUBSECTION, VALUE);
    setSingleValueForSubsection(parent, SUBSECTION, VALUE_2);
    setSingleValueForSubsection(project, SUBSECTION, VALUE_3);
    assertThat(cfg().getStringList(SECTION, SUBSECTION, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, VALUE_3)
        .inOrder();
  }

  @Test
  public void getMultiValueFromSubsection_singleValueOverridesMultiParentValues() throws Exception {
    setMultiValueForSubsection(allProjects, SUBSECTION, VALUE, VALUE_2);
    setMultiValueForSubsection(parent, SUBSECTION, VALUE_3, VALUE_4);
    setSingleValueForSubsection(project, SUBSECTION, VALUE_5);
    assertThat(cfg().getStringList(SECTION, SUBSECTION, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, VALUE_3, VALUE_4, VALUE_5)
        .inOrder();
  }

  @Test
  public void getMultiValueFromSubsection_multiValueExtendsSingleParentValues() throws Exception {
    setSingleValueForSubsection(allProjects, SUBSECTION, VALUE);
    setSingleValueForSubsection(parent, SUBSECTION, VALUE_2);
    setMultiValueForSubsection(project, SUBSECTION, VALUE_3, VALUE_4);
    assertThat(cfg().getStringList(SECTION, SUBSECTION, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, VALUE_3, VALUE_4)
        .inOrder();
  }

  @Test
  public void getMultiValueFromSubsection_multiValueExtendsMultiParentValues() throws Exception {
    setMultiValueForSubsection(allProjects, SUBSECTION, VALUE, VALUE_2);
    setMultiValueForSubsection(parent, SUBSECTION, VALUE_3, VALUE_4);
    setMultiValueForSubsection(project, SUBSECTION, VALUE_5, VALUE_6);
    assertThat(cfg().getStringList(SECTION, SUBSECTION, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, VALUE_3, VALUE_4, VALUE_5, VALUE_6)
        .inOrder();
  }

  @Test
  public void getMultiValueFromSubsection_cannotUnsetSingleParentValues() throws Exception {
    setSingleValueForSubsection(allProjects, SUBSECTION, VALUE);
    setSingleValueForSubsection(parent, SUBSECTION, VALUE_2);
    setSingleValueForSubsection(project, SUBSECTION, "");

    // the empty string is returned as null value in the list
    assertThat(cfg().getStringList(SECTION, SUBSECTION, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, null)
        .inOrder();
  }

  @Test
  public void getMultiValueFromSubsection_cannotUnsetMultiParentValues() throws Exception {
    setMultiValueForSubsection(allProjects, SUBSECTION, VALUE, VALUE_2);
    setMultiValueForSubsection(parent, SUBSECTION, VALUE_3, VALUE_4);
    setSingleValueForSubsection(project, SUBSECTION, "");

    // the empty string is returned as null value in the list
    assertThat(cfg().getStringList(SECTION, SUBSECTION, KEY))
        .asList()
        .containsExactly(VALUE, VALUE_2, VALUE_3, VALUE_4, null)
        .inOrder();
  }

  @Test
  public void getSubsections() throws Exception {
    setSingleValueForSubsection(allProjects, SUBSECTION, VALUE);
    setSingleValueForSubsection(parent, SUBSECTION_2, VALUE_2);
    setSingleValueForSubsection(project, SUBSECTION_3, VALUE_3);
    assertThat(cfg().getSubsections(SECTION))
        .containsExactly(SUBSECTION, SUBSECTION_2, SUBSECTION_3);
  }

  @Test
  public void getEmptySubsections() throws Exception {
    createConfigWithEmptySubsection(allProjects, SUBSECTION);
    createConfigWithEmptySubsection(parent, SUBSECTION_2);
    createConfigWithEmptySubsection(project, SUBSECTION_3);
    assertThat(cfg().getSubsections(SECTION))
        .containsExactly(SUBSECTION, SUBSECTION_2, SUBSECTION_3);
  }

  private Config cfg() {
    return codeOwnersPluginConfigFactory.create(project).get();
  }

  private void setSingleValue(Project.NameKey project, String value) throws Exception {
    setSingleValueForSubsection(project, /* subsection= */ null, value);
  }

  private void setSingleValueForSubsection(
      Project.NameKey project, @Nullable String subsection, String value) throws Exception {
    setCodeOwnersConfig(project, subsection, KEY, value);
  }

  private void setMultiValue(Project.NameKey project, String... values) throws Exception {
    setMultiValueForSubsection(project, /* subsection= */ null, values);
  }

  private void setMultiValueForSubsection(
      Project.NameKey project, @Nullable String subsection, String... values) throws Exception {
    setCodeOwnersConfig(project, subsection, KEY, ImmutableList.copyOf(values));
  }

  private void createConfigWithEmptySubsection(Project.NameKey project, String subsection)
      throws Exception {
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      Ref ref = testRepo.getRepository().exactRef(RefNames.REFS_CONFIG);
      RevCommit head = testRepo.getRevWalk().parseCommit(ref.getObjectId());
      testRepo.update(
          RefNames.REFS_CONFIG,
          testRepo
              .commit()
              .parent(head)
              .message("Configure code owner backend")
              .add("code-owners.config", String.format("[%s \"%s\"]", SECTION, subsection)));
    }
    projectCache.evict(project);
  }
}
