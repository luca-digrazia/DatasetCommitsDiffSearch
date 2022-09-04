// Copyright 2016 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.rules.objc;

import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.ConfiguredAspect;
import com.google.devtools.build.lib.analysis.ConfiguredAspectFactory;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.PrerequisiteArtifacts;
import com.google.devtools.build.lib.analysis.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.packages.AspectDefinition;
import com.google.devtools.build.lib.packages.AspectParameters;
import com.google.devtools.build.lib.packages.BuildType;
import com.google.devtools.build.lib.packages.NativeAspectClass;
import com.google.devtools.build.lib.rules.proto.ProtoSourcesProvider;

/**
 * Aspect that gathers the proto dependencies of the attached rule target, and propagates the proto
 * values of its dependencies through the ObjcProtoProvider.
 */
public class ObjcProtoAspect extends NativeAspectClass implements ConfiguredAspectFactory {
  public static final String NAME = "ObjcProtoAspect";

  @Override
  public AspectDefinition getDefinition(AspectParameters aspectParameters) {
    return new AspectDefinition.Builder(this)
        .propagateAlongAttribute("deps")
        .build();
  }

  @Override
  public ConfiguredAspect create(
      ConfiguredTarget base, RuleContext ruleContext, AspectParameters parameters)
      throws InterruptedException {
    ConfiguredAspect.Builder aspectBuilder = new ConfiguredAspect.Builder(
        this, parameters, ruleContext);

    ObjcProtoProvider.Builder aspectObjcProtoProvider = new ObjcProtoProvider.Builder();

    if (ruleContext.attributes().has("deps", BuildType.LABEL_LIST)) {
      Iterable<ObjcProtoProvider> depObjcProtoProviders =
          ruleContext.getPrerequisites("deps", Mode.TARGET, ObjcProtoProvider.class);
      aspectObjcProtoProvider.addTransitive(depObjcProtoProviders);
    }

    ProtoAttributes attributes = new ProtoAttributes(ruleContext);

    // If the rule has the portable_proto_filters or uses_protobuf, it must be an objc_proto_library
    // configured to use the third party protobuf library, in contrast with the PB2 internal
    // library. Only the third party library is enabled to propagate the protos with this aspect.
    // Validation for the correct target attributes is done in ProtoSupport.java.
    if (attributes.requiresProtobuf()) {

      // Gather up all the dependency protos depended by this target.
      Iterable<ProtoSourcesProvider> protoProviders =
          ruleContext.getPrerequisites("deps", Mode.TARGET, ProtoSourcesProvider.class);

      for (ProtoSourcesProvider protoProvider : protoProviders) {
        aspectObjcProtoProvider.addProtoGroup(protoProvider.getTransitiveProtoSources());
      }

      NestedSet<Artifact> portableProtoFilters =
          PrerequisiteArtifacts.nestedSet(
              ruleContext, ObjcProtoLibraryRule.PORTABLE_PROTO_FILTERS_ATTR, Mode.HOST);

      // If this target does not provide filters but specifies direct proto_library dependencies,
      // generate a filter file only for those proto files.
      if (Iterables.isEmpty(portableProtoFilters) && !Iterables.isEmpty(protoProviders)) {
        Artifact generatedFilter =
            ProtobufSupport.getGeneratedPortableFilter(ruleContext, ruleContext.getConfiguration());
        ProtobufSupport.registerPortableFilterGenerationAction(
            ruleContext,
            generatedFilter,
            protoProviders);
        portableProtoFilters = NestedSetBuilder.create(Order.STABLE_ORDER, generatedFilter);
      }

      aspectObjcProtoProvider.addPortableProtoFilters(portableProtoFilters);

      // Propagate protobuf's headers and search paths so the BinaryLinkingTargetFactory subclasses
      // (i.e. objc_binary) don't have to depend on it.
      ObjcProvider protobufObjcProvider =
          ruleContext.getPrerequisite(
              ObjcRuleClasses.PROTO_LIB_ATTR, Mode.TARGET, ObjcProvider.SKYLARK_CONSTRUCTOR);
      aspectObjcProtoProvider.addProtobufHeaders(protobufObjcProvider.get(ObjcProvider.HEADER));
      aspectObjcProtoProvider.addProtobufHeaderSearchPaths(
          protobufObjcProvider.get(ObjcProvider.INCLUDE));
    }

    // Only add the provider if it has any values, otherwise skip it.
    if (!aspectObjcProtoProvider.isEmpty()) {
      aspectBuilder.addProvider(aspectObjcProtoProvider.build());
    }
    return aspectBuilder.build();
  }
}
