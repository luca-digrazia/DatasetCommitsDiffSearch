// Copyright 2014 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.docgen.skylark;

import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature.Param;

/**
 * A class containing the documentation for a Skylark method parameter.
 */
public final class SkylarkParamDoc extends SkylarkDoc {
  private SkylarkMethodDoc method;
  private Param param;

  public SkylarkParamDoc(SkylarkMethodDoc method, Param param) {
    this.method = method;
    this.param = param;
  }

  /**
   * Returns the string representing the type of this parameter with the link to the
   * documentation for the type if available.
   *
   * <p>If the parameter type is Object, then returns the empty string. If the parameter
   * type is not a generic, then this method returns a string representing the type name
   * with a link to the documentation for the type if available. If the parameter type
   * is a generic, then this method returns a string "CONTAINER of TYPE".
   */
  public String getType() {
    if (param.type().equals(Object.class)) {
      return "";
    }
    if (param.generic1().equals(Object.class)) {
      return getTypeAnchor(param.type());
    } else {
      return getTypeAnchor(param.type(), param.generic1());
    }
  }

  public SkylarkMethodDoc getMethod() {
    return method;
  }

  public String getName() {
    return param.name();
  }

  public String getDocumentation() {
    return param.doc();
  }
}
