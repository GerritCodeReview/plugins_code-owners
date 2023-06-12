// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
   *   <li>{@code FATAL}: only fatal issues are returned
   *   <li>{@code ERROR}: only fatal and error issues are returned
   *   <li>{@code WARNING}: all issues (warning, error and fatal) are returned
   * </ul>
   *
   * <p>If unset, {@code WARNING} is used.
   */
  public ConsistencyProblemInfo.Status verbosity;
}
