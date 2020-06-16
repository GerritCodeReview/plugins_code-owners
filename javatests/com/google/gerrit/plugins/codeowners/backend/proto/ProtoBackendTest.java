package com.google.gerrit.plugins.codeowners.backend.proto;

import com.google.gerrit.plugins.codeowners.backend.AbstractFileBasedCodeOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.AbstractFileBasedCodeOwnersBackendTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;

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
}
