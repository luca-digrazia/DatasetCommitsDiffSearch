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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkInterfaceUtils;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * An abstract class containing documentation for a Skylark method.
 */
public abstract class SkylarkMethodDoc extends SkylarkDoc {
  /** Returns whether the Skylark method is documented. */
  public abstract boolean documented();

  /**
   * Returns a string containing additional documentation about the method's return value.
   *
   * <p>Returns an empty string by default.
   */
  public String getReturnTypeExtraMessage() {
    return "";
  }

  /** Returns a string containing a name for the method's return type. */
  public String getReturnType() {
    return "";
  }

  /**
   * Returns whether a method can be called as a function.
   *
   * <p>E.g. True is not callable.
   */
  public Boolean isCallable() {
    return true;
  }

  /**
   * Returns a string containing the method's name. GetName() returns the complete signature in case
   * of overloaded methods. This is used to extract only the name of the method.
   *
   * <p>E.g. ctx.new_file is overloaded. In this case getName() returns "new_file(filename)", while
   * getShortName() returns only "new_file".
   */
  public String getShortName() {
    return getName();
  }

  /**
   * Returns a list containing the documentation for each of the method's parameters.
   */
  public List<SkylarkParamDoc> getParams() {
    return ImmutableList.<SkylarkParamDoc>of();
  }

  private String getParameterString(Method method) {
    SkylarkCallable annotation = SkylarkInterfaceUtils.getSkylarkCallable(method);
    List<String> argList = new ArrayList<>();

    boolean named = false;
    for (Param param : withoutSelfParam(annotation, method)) {
      if (param.named() && !param.positional() && !named) {
        named = true;
        if (!argList.isEmpty()) {
          argList.add("*");
        }
      }
      argList.add(formatParameter(param));
    }
    if (!annotation.extraPositionals().name().isEmpty()) {
      argList.add("*" + annotation.extraPositionals().name());
    }
    if (!annotation.extraKeywords().name().isEmpty()) {
      argList.add("**" + annotation.extraKeywords().name());
    }
    return Joiner.on(", ").join(argList);
  }

  /**
   * Returns a string representing the method signature of the Skylark method, which contains
   * HTML links to the documentation of parameter types if available.
   */
  public abstract String getSignature();

  protected String getSignature(String objectName, String methodName, Method method) {
    String objectDotExpressionPrefix =
        objectName.isEmpty() ? "" : objectName + ".";

    return getSignature(objectDotExpressionPrefix + methodName, method);
  }

  protected String getSignature(String fullyQualifiedMethodName, Method method) {
    String args = SkylarkInterfaceUtils.getSkylarkCallable(method).structField()
        ? "" : "(" + getParameterString(method) + ")";

    return String.format("%s %s%s",
        getTypeAnchor(method.getReturnType()), fullyQualifiedMethodName, args);
  }

  protected String getSignature(String objectName, SkylarkSignature method) {
    List<String> argList = new ArrayList<>();
    boolean named = false;
    for (Param param : withoutSelfParam(method)) {
      if (param.named() && !param.positional() && !named) {
        named = true;
        if (!method.extraPositionals().name().isEmpty()) {
          argList.add("*" + method.extraPositionals().name());
        }
        if (!argList.isEmpty()) {
          argList.add("*");
        }
      }
      argList.add(formatParameter(param));
    }
    if (!named && !method.extraPositionals().name().isEmpty()) {
      argList.add("*" + method.extraPositionals().name());
    }
    if (!method.extraKeywords().name().isEmpty()) {
      argList.add("**" + method.extraKeywords().name());
    }
    String args = "(" + Joiner.on(", ").join(argList) + ")";
    if (!objectName.equals(TOP_LEVEL_ID)) {
      return String.format("%s %s.%s%s\n",
          getTypeAnchor(method.returnType()), objectName, method.name(), args);
    } else {
      return String.format("%s %s%s\n",
          getTypeAnchor(method.returnType()), method.name(), args);
    }
  }

  private String formatParameter(Param param) {
    String defaultValue = param.defaultValue();
    String name = param.name();
    if (defaultValue == null || !defaultValue.isEmpty()) {
      return String.format("%s=%s", name, defaultValue == null ? "&hellip;" : defaultValue);
    } else {
      return name;
    }
  }
}
