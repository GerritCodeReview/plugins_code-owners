package com.google.gerrit.plugins.codeowners.api;

/**
 * Representation of CodeOwnerScoringFactor in the REST
 * API.
 *
 * <p>This class determines the JSON format in the REST API.
 */
public class CodeOwnerScoringFactorInfo {
  /** Code owner info */
  public CodeOwnerInfo codeOwnerInfo;
  /** The score used*/
  public String score;
  /** The value of the score */
  public String value;

}
