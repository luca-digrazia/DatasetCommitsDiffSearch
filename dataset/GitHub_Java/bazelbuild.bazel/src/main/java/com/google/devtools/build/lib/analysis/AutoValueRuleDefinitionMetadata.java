// Copyright 2014 Google Inc. All rights reserved.
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

/**
 * TODO(bazel-team): This file has been generated by @AutoValue, and copied here because we do not
 * support @AutoValue yet. Remove this file and add @AutoValue annotations to RuleDefinition
 * once possible.
 */
package com.google.devtools.build.lib.analysis;

import com.google.devtools.build.lib.packages.RuleClass.Builder.RuleClassType;
import com.google.devtools.build.lib.rules.RuleConfiguredTargetFactory;

import java.util.List;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValueRuleDefinitionMetadata extends RuleDefinition.Metadata {

  private final String name;
  private final RuleClassType type;
  private final Class<? extends RuleConfiguredTargetFactory> factoryClass;
  private final List<Class<? extends RuleDefinition>> ancestors;

  private AutoValueRuleDefinitionMetadata(
      String name,
      RuleClassType type,
      Class<? extends RuleConfiguredTargetFactory> factoryClass,
      List<Class<? extends RuleDefinition>> ancestors) {
    if (name == null) {
      throw new NullPointerException("Null name");
    }
    this.name = name;
    if (type == null) {
      throw new NullPointerException("Null type");
    }
    this.type = type;
    if (factoryClass == null) {
      throw new NullPointerException("Null factoryClass");
    }
    this.factoryClass = factoryClass;
    if (ancestors == null) {
      throw new NullPointerException("Null ancestors");
    }
    this.ancestors = ancestors;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public RuleClassType type() {
    return type;
  }

  @Override
  public Class<? extends RuleConfiguredTargetFactory> factoryClass() {
    return factoryClass;
  }

  @Override
  public List<Class<? extends RuleDefinition>> ancestors() {
    return ancestors;
  }

  @Override
  public String toString() {
    return "Metadata{"
        + "name=" + name + ", "
        + "type=" + type + ", "
        + "factoryClass=" + factoryClass + ", "
        + "ancestors=" + ancestors
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof RuleDefinition.Metadata) {
      RuleDefinition.Metadata that = (RuleDefinition.Metadata) o;
      return (this.name.equals(that.name()))
           && (this.type.equals(that.type()))
           && (this.factoryClass.equals(that.factoryClass()))
           && (this.ancestors.equals(that.ancestors()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= name.hashCode();
    h *= 1000003;
    h ^= type.hashCode();
    h *= 1000003;
    h ^= factoryClass.hashCode();
    h *= 1000003;
    h ^= ancestors.hashCode();
    return h;
  }

  static final class Builder extends RuleDefinition.Metadata.Builder {
    private String name;
    private RuleClassType type;
    private Class<? extends RuleConfiguredTargetFactory> factoryClass;
    private List<Class<? extends RuleDefinition>> ancestors;
    Builder() {
    }
    Builder(RuleDefinition.Metadata source) {
      name(source.name());
      type(source.type());
      factoryClass(source.factoryClass());
      ancestors(source.ancestors());
    }
    @Override
    public RuleDefinition.Metadata.Builder name(String name) {
      this.name = name;
      return this;
    }
    @Override
    public RuleDefinition.Metadata.Builder type(RuleClassType type) {
      this.type = type;
      return this;
    }
    @Override
    public RuleDefinition.Metadata.Builder factoryClass(
        Class<? extends RuleConfiguredTargetFactory> factoryClass) {
      this.factoryClass = factoryClass;
      return this;
    }
    @Override
    public RuleDefinition.Metadata.Builder ancestors(
        List<Class<? extends RuleDefinition>> ancestors) {
      this.ancestors = ancestors;
      return this;
    }
    @Override
    public RuleDefinition.Metadata build() {
      String missing = "";
      if (name == null) {
        missing += " name";
      }
      if (type == null) {
        missing += " type";
      }
      if (factoryClass == null) {
        missing += " factoryClass";
      }
      if (ancestors == null) {
        missing += " ancestors";
      }
      if (!missing.isEmpty()) {
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      RuleDefinition.Metadata result = new AutoValueRuleDefinitionMetadata(
          this.name,
          this.type,
          this.factoryClass,
          this.ancestors);
      return result;
    }
  }
}
