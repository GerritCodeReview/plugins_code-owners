package com.google.gerrit.plugins.codeowners.backend.proto;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.plugins.codeowners.backend.AbstractFileBasedCodeOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.AbstractFileBasedCodeOwnersBackendTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;
import org.junit.Test;

/** Tests for {@link ProtoBackend}. */
public class ProtoBackendTest extends AbstractFileBasedCodeOwnersBackendTest {
  @Override
  protected Class<? extends AbstractFileBasedCodeOwnersBackend> getBackendClass() {
    return ProtoBackend.class;
  }

  @Override
  protected String getFileName() {
    return ProtoBackend.CODE_OWNER_CONFIG_FILE_NAME;
  }

  @Override
  protected Class<? extends CodeOwnerConfigParser> getParserClass() {
    return ProtoCodeOwnerConfigParser.class;
  }

  @Test
  public void getPathExpressionMatcher() throws Exception {
    assertThat(codeOwnersBackend.getPathExpressionMatcher())
        .isInstanceOf(Google3PathExpressionMatcher.class);
  }
}
