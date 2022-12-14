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
 * Rule definition for the new_repository rule.
 */
@BlazeRule(name = NewLocalRepositoryRule.NAME,
           type = RuleClassType.WORKSPACE,
           ancestors = { WorkspaceBaseRule.class },
           factoryClass = WorkspaceConfiguredTargetFactory.class)
public class NewLocalRepositoryRule implements RuleDefinition {
  public static final String NAME = "new_local_repository";

  @Override
  public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
    return builder
        /* <!-- #BLAZE_RULE(new_local_repository).ATTRIBUTE(path) -->
        A path on the local filesystem.
        ${SYNOPSIS}

        <p>This must be an absolute path to an existing file or a directory.</p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("path", STRING).mandatory())
        /* <!-- #BLAZE_RULE(new_local_repository).ATTRIBUTE(build_file) -->
        A file to use as a BUILD file for this directory.
        ${SYNOPSIS}

        <p>This path must be relative to the build's workspace. The file does not need to be named
        BUILD, but can be (something like BUILD.new-repo-name may work well for distinguishing it
        from the repository's actual BUILD files.</p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("build_file", STRING).mandatory())
        .setWorkspaceOnly()
        .build();
  }
}
/*<!-- #BLAZE_RULE (NAME = new_local_repository, TYPE = OTHER, FAMILY = General)[GENERIC_RULE] -->

${ATTRIBUTE_SIGNATURE}

<p>Allows a local directory to be turned into a Bazel repository. This means that the current
  repository can define and use targets from anywhere on the filesystem.</p>

<p>This rule creates a Bazel repository by creating a WORKSPACE file and subdirectory containing
symlinks to the BUILD file and path given.  The build file should create targets relative to the
path, which can then be bound and used by the current build.

${ATTRIBUTE_DEFINITION}

<h4 id="new_local_repository_examples">Examples</h4>

<p>Suppose the current repository is a chat client, rooted at the directory <i>~/chat-app</i>. It
  would like to use an SSL library which is defined in a different directory: <i>~/ssl</i>.</p>

<p>The user can add a dependency by creating a BUILD file for the SSL library
(~/chat-app/BUILD.my-ssl) containing:

<pre class="code">
java_library(
    name = "openssl",
    srcs = glob(['ssl/*.java'])
)
</pre>

<p>Then they can add the following lines to <i>~/chat-app/WORKSPACE</i>:</p>

<pre class="code">
new_local_repository(
    name = "my-ssl",
    path = "/home/user/ssl",
    build_file = "BUILD.my-ssl",
)

bind(
    name = "openssl",
    actual = "@my-ssl//my-ssl:openssl",
)
</pre>

<p>This will create a @my-ssl repository containing a my-ssl package that contains a symlink to
/home/user/ssl named ssl (so the BUILD file must refer to paths within /home/user/ssl relative to
ssl).</p>

<p>See <a href="#bind_examples">Bind</a> for how to use bound targets.</p>

<p>You can also use <code>new_local_repository</code> to include single files, not just
directories. For example, suppose you had a jar file at /home/username/Downloads/piano.jar. You
could add just that file to your build by adding the following to your WORKSPACE file:

<pre class="code">
new_local_repository(
    name = "piano",
    path = "/home/username/Downloads/piano.jar",
    build_file = "BUILD.piano",
)

bind(
    name = "music",
    actual = "@piano//piano:play-music",
)
</pre>

<p>And creating the following BUILD file:</p>

<pre class="code">
java_import(
    name = "play-music",
    jars = ["piano.jar"],
)
</pre>

Then targets can depend on //external:music to use piano.jar.

<!-- #END_BLAZE_RULE -->*/
