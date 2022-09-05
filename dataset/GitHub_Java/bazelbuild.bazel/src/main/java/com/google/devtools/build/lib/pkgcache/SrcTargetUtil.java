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
package com.google.devtools.build.lib.pkgcache;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.concurrent.ThreadSafety;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.packages.AttributeMap;
import com.google.devtools.build.lib.packages.FileTarget;
import com.google.devtools.build.lib.packages.NoSuchPackageException;
import com.google.devtools.build.lib.packages.NoSuchTargetException;
import com.google.devtools.build.lib.packages.RawAttributeMapper;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.packages.Target;
import com.google.devtools.build.lib.packages.Type;
import com.google.devtools.build.lib.syntax.Label;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A helper class for getting source and header files from a given {@link Rule}.
 */
public final class SrcTargetUtil {
  private SrcTargetUtil() {
  }

  /**
   * Given a Rule, returns an immutable list of FileTarget for its sources, in the order they appear
   * in its "srcs", "src" or "srcjar" attribute, and any filegroups or other rules it references.
   * An empty list is returned if no "srcs" or "src" attribute exists for this rule. The list may
   * contain OutputFiles if the sources were generated by another rule.
   *
   * <p>This method should be considered only a heuristic, and should not be used during the
   * analysis phase.
   *
   * <p>(We could remove the throws clauses if we restrict the results to srcs within the same
   * package.)
   *
   * @throws NoSuchTargetException or NoSuchPackageException when a source label cannot be resolved
   *         to a Target
   */
  @ThreadSafety.ThreadSafe
  public static List<FileTarget> getSrcTargets(EventHandler eventHandler, Rule rule,
                                               TargetProvider provider)
      throws NoSuchTargetException, NoSuchPackageException, InterruptedException  {
    return getTargets(eventHandler, rule, SOURCE_ATTRIBUTES, Sets.newHashSet(rule), provider);
  }

  // Attributes referring to "sources".
  private static final ImmutableSet<String> SOURCE_ATTRIBUTES =
      ImmutableSet.of("srcs", "src", "srcjar");

  // Attributes referring to "headers".
  private static final ImmutableSet<String> HEADER_ATTRIBUTES =
      ImmutableSet.of("hdrs");
  
  // The attribute to search in filegroups.
  private static final ImmutableSet<String> FILEGROUP_ATTRIBUTES =
      ImmutableSet.of("srcs");

  /**
   * Same as {@link #getSrcTargets}, but for both source and headers (i.e. also traversing
   * the "hdrs" attribute).
   */
  @ThreadSafety.ThreadSafe
  public static List<FileTarget> getSrcAndHdrTargets(EventHandler eventHandler, Rule rule,
                                                     TargetProvider provider)
      throws NoSuchTargetException, NoSuchPackageException, InterruptedException  {
    ImmutableSet<String> srcAndHdrAttributes = ImmutableSet.<String>builder()
        .addAll(SOURCE_ATTRIBUTES)
        .addAll(HEADER_ATTRIBUTES)
        .build();
    return getTargets(eventHandler, rule, srcAndHdrAttributes, Sets.newHashSet(rule), provider);
  }

  @ThreadSafety.ThreadSafe
  public static List<FileTarget> getHdrTargets(EventHandler eventHandler, Rule rule,
                                                     TargetProvider provider)
      throws NoSuchTargetException, NoSuchPackageException, InterruptedException  {
    ImmutableSet<String> srcAndHdrAttributes = ImmutableSet.copyOf(HEADER_ATTRIBUTES);
    return getTargets(eventHandler, rule, srcAndHdrAttributes, Sets.newHashSet(rule), provider);
  }
  
  /**
   * @see #getSrcTargets(EventHandler, Rule, TargetProvider)
   */
  private static List<FileTarget> getTargets(EventHandler eventHandler,
      Rule rule,
      ImmutableSet<String> attributes,
      Set<Rule> visitedRules,
      TargetProvider targetProvider)
      throws NoSuchTargetException, NoSuchPackageException, InterruptedException {
    Preconditions.checkState(!rule.hasConfigurableAttributes()); // Not currently supported.
    List<Label> srcLabels = Lists.newArrayList();
    AttributeMap attributeMap = RawAttributeMapper.of(rule);
    for (String attrName : attributes) {
      if (rule.isAttrDefined(attrName, Type.LABEL_LIST)) {
        srcLabels.addAll(attributeMap.get(attrName, Type.LABEL_LIST));
      } else if (rule.isAttrDefined(attrName, Type.LABEL)) {
        Label srcLabel = attributeMap.get(attrName, Type.LABEL);
        if (srcLabel != null) {
          srcLabels.add(srcLabel);
        }
      }
    }
    if (srcLabels.isEmpty()) {
      return ImmutableList.of();
    }
    List<FileTarget> srcTargets = new ArrayList<>();
    for (Label label : srcLabels) {
      Target target = targetProvider.getTarget(eventHandler, label);
      if (target instanceof FileTarget) {
        srcTargets.add((FileTarget) target);
      } else {
        Rule srcRule = target.getAssociatedRule();
        if (srcRule != null && !visitedRules.contains(srcRule)) {
          visitedRules.add(srcRule);
          if ("filegroup".equals(srcRule.getRuleClass())) {
            srcTargets.addAll(getTargets(eventHandler, srcRule, FILEGROUP_ATTRIBUTES, visitedRules,
                targetProvider));
          } else {
            srcTargets.addAll(srcRule.getOutputFiles());
          }
        }
      }
    }
    return ImmutableList.copyOf(srcTargets);
  }
}
