// Copyright 2015 Google Inc. All rights reserved.
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

package com.google.devtools.build.workspace;

import com.google.devtools.build.lib.analysis.ConfiguredRuleClassProvider;
import com.google.devtools.build.lib.bazel.BazelMain;
import com.google.devtools.build.lib.cmdline.PackageIdentifier;
import com.google.devtools.build.lib.cmdline.TargetParsingException;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.packages.AggregatingAttributeMapper;
import com.google.devtools.build.lib.packages.AttributeMap;
import com.google.devtools.build.lib.packages.ExternalPackage;
import com.google.devtools.build.lib.packages.RuleClassProvider;
import com.google.devtools.build.lib.packages.Target;
import com.google.devtools.build.lib.packages.Type;
import com.google.devtools.build.lib.packages.WorkspaceFactory;
import com.google.devtools.build.lib.runtime.BlazeModule;
import com.google.devtools.build.lib.runtime.BlazeRuntime;
import com.google.devtools.build.lib.syntax.ParserInputSource;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.workspace.maven.DefaultModelResolver;
import com.google.devtools.build.workspace.maven.Rule;

import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.UnresolvableModelException;

import java.io.IOException;
import java.util.List;

/**
 * Finds the transitive dependencies of a WORKSPACE file.
 */
public class Resolver {

  private final RuleClassProvider ruleClassProvider;
  private final EventHandler handler;
  private final com.google.devtools.build.workspace.maven.Resolver resolver;

  Resolver(com.google.devtools.build.workspace.maven.Resolver resolver, EventHandler handler) {
    this.resolver = resolver;
    this.handler = handler;
    ConfiguredRuleClassProvider.Builder ruleClassBuilder =
        new ConfiguredRuleClassProvider.Builder();
    List<BlazeModule> blazeModules = BlazeRuntime.createModules(BazelMain.BAZEL_MODULES);
    for (BlazeModule blazeModule : blazeModules) {
      blazeModule.initializeRuleClasses(ruleClassBuilder);
    }
    this.ruleClassProvider = ruleClassBuilder.build();
  }

  /**
   * Converts the WORKSPACE file content into an ExternalPackage.
   */
  public ExternalPackage parse(Path workspacePath) {
    resolver.addHeader(workspacePath.getPathString());
    ExternalPackage.Builder builder = new ExternalPackage.Builder(workspacePath,
        ruleClassProvider.getRunfilesPrefix());
    WorkspaceFactory parser = new WorkspaceFactory(builder, ruleClassProvider);
    try {
      parser.parse(ParserInputSource.create(workspacePath));
    } catch (IOException | InterruptedException e) {
      handler.handle(Event.error(Location.fromFile(workspacePath), e.getMessage()));
    }

    return builder.build();
  }

  /**
   * Calculates transitive dependencies of the given //external package.
   */
  public void resolveTransitiveDependencies(ExternalPackage externalPackage) {
    Location location = Location.fromFile(externalPackage.getFilename());
    for (Target target : externalPackage.getTargets()) {
      // Targets are //external:foo.
      if (target.getTargetKind().startsWith("bind")
          || target.getTargetKind().startsWith("source ")) {
        continue;
      } else if (target.getTargetKind().startsWith("maven_jar ")) {
        PackageIdentifier.RepositoryName repositoryName;
        try {
          repositoryName = PackageIdentifier.RepositoryName.create("@" + target.getName());
        } catch (TargetParsingException e) {
          handler.handle(Event.error(location, "Invalid repository name for " + target + ": "
              + e.getMessage()));
          return;
        }
        com.google.devtools.build.lib.packages.Rule workspaceRule =
            externalPackage.getRepositoryInfo(repositoryName);

        DefaultModelResolver modelResolver = resolver.getModelResolver();
        AttributeMap attributeMap = AggregatingAttributeMapper.of(workspaceRule);
        Rule rule;
        try {
          if (attributeMap.has("artifact", Type.STRING)
              && !attributeMap.get("artifact", Type.STRING).isEmpty()) {
            rule = new Rule(attributeMap.get("artifact", Type.STRING));
          } else {
            rule = new Rule(attributeMap.get("group_id", Type.STRING) + ":"
                + attributeMap.get("artifact_id", Type.STRING) + ":"
                + attributeMap.get("version", Type.STRING));
          }
          if (attributeMap.has("repository", Type.STRING)
              && !attributeMap.get("repository", Type.STRING).isEmpty()) {
            modelResolver.addUserRepository(attributeMap.get("repository", Type.STRING));
            rule.setRepository(attributeMap.get("repository", Type.STRING), handler);
          }
        } catch (Rule.InvalidRuleException e) {
          handler.handle(Event.error(location, "Couldn't get attribute: " + e.getMessage()));
          return;
        }
        ModelSource modelSource;

        try {
          modelSource = modelResolver.resolveModel(
              rule.groupId(), rule.artifactId(), rule.version());
        } catch (UnresolvableModelException e) {
          handler.handle(Event.error(
              "Could not resolve model for " + target + ": " + e.getMessage()));
          continue;
        }
        resolver.resolveEffectiveModel(modelSource);
      } else {
        handler.handle(Event.warn(location, "Cannot fetch transitive dependencies for " + target
            + " yet, skipping"));
      }
    }
  }
}
