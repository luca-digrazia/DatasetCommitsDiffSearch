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

package com.google.devtools.build.lib.bazel.rules.workspace;

import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.packages.Type.STRING;

import com.google.devtools.build.lib.analysis.BlazeRule;
import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder;
import com.google.devtools.build.lib.packages.RuleClass.Builder.RuleClassType;

/**
 * Rule definition for the local_repository rule.
 */
@BlazeRule(name = LocalRepositoryRule.NAME,
  type = RuleClassType.WORKSPACE,
  ancestors = { WorkspaceBaseRule.class },
  factoryClass = WorkspaceConfiguredTargetFactory.class)
public class LocalRepositoryRule implements RuleDefinition {

  public static final String NAME = "local_repository";

  @Override
  public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
    return builder
        /* <!-- #BLAZE_RULE(local_repository).ATTRIBUTE(path) -->
        The path to the local repository's directory.
        ${SYNOPSIS}

        <p>This must be an absolute path to the directory containing the repository's
        <i>WORKSPACE</i> file.</p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("path", STRING).mandatory())
        .setWorkspaceOnly()
        .build();
  }
}
/*<!-- #BLAZE_RULE (NAME = local_repository, TYPE = OTHER, FAMILY = General)[GENERIC_RULE] -->

${ATTRIBUTE_SIGNATURE}

<p>Allows targets from a local directory to be bound. This means that the current repository can
  use targets defined in this other directory. See the <a href="#bind_examples">bind section</a>
  for more details.</p>

${ATTRIBUTE_DEFINITION}

<h4 id="local_repository_examples">Examples</h4>

<p>Suppose the current repository is a chat client, rooted at the directory <i>~/chat-app</i>. It
  would like to use an SSL library which is defined in a different repository: <i>~/ssl</i>.  The
  SSL library has a target <code>//src:openssl-lib</code>.</p>

<p>The user can add a dependency on this target by adding the following lines to
  <i>~/chat-app/WORKSPACE</i>:</p>

<pre class="code">
local_repository(
    name = "my-ssl",
    path = "/home/user/ssl",
)

bind(
    name = "openssl",
    actual = "@my-ssl//src:openssl-lib",
)
</pre>

<p>See <a href="#bind_examples">Bind</a> for how to use bound targets.</p>

<!-- #END_BLAZE_RULE -->*/
