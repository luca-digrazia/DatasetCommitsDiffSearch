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
package com.google.devtools.build.lib.analysis.actions;

import static com.google.devtools.build.lib.actions.util.ActionsTestUtil.NULL_ACTION_OWNER;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.devtools.build.lib.actions.ActionExecutionContext;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.Executor;
import com.google.devtools.build.lib.actions.Root;
import com.google.devtools.build.lib.analysis.BlazeDirectories;
import com.google.devtools.build.lib.analysis.actions.TemplateExpansionAction.Substitution;
import com.google.devtools.build.lib.analysis.actions.TemplateExpansionAction.Template;
import com.google.devtools.build.lib.analysis.config.BinTools;
import com.google.devtools.build.lib.exec.util.TestExecutorBuilder;
import com.google.devtools.build.lib.testutil.FoundationTestCase;
import com.google.devtools.build.lib.util.io.FileOutErr;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.Path;

import java.util.List;

/**
 * Tests {@link TemplateExpansionAction}.
 */
public class TemplateExpansionActionTest extends FoundationTestCase {

  private static final String TEMPLATE = Joiner.on('\n').join("key=%key%", "value=%value%");

  private Root outputRoot;
  private Artifact inputArtifact;
  private Artifact outputArtifact;
  private Path output;
  private List<Substitution> substitutions;
  private BlazeDirectories directories;
  private BinTools binTools;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Root workspace = Root.asSourceRoot(scratch.dir("/workspace"));
    outputRoot = Root.asDerivedRoot(scratch.dir("/workspace"), scratch.dir("/workspace/out"));
    Path input = scratchFile("/workspace/input.txt", TEMPLATE);
    inputArtifact = new Artifact(input, workspace);
    output = scratchFS().getPath("/workspace/out/destination.txt");
    outputArtifact = new Artifact(output, outputRoot);
    substitutions = Lists.newArrayList();
    substitutions.add(Substitution.of("%key%", "foo"));
    substitutions.add(Substitution.of("%value%", "bar"));
    directories = new BlazeDirectories(
        scratchFS().getPath("/install"),
        scratchFS().getPath("/base"),
        scratchFS().getPath("/workspace"));
    binTools = BinTools.empty(directories);
  }

  private TemplateExpansionAction create() {
    TemplateExpansionAction result = new TemplateExpansionAction(NULL_ACTION_OWNER,
         outputArtifact, Template.forString(TEMPLATE), substitutions, false);
    return result;
  }

  public void testInputsIsEmpty() {
    assertTrue(Iterables.isEmpty(create().getInputs()));
  }

  public void testDestinationArtifactIsOutput() {
    assertEquals(ImmutableSet.of(outputArtifact), create().getOutputs());
  }

  public void testExpansion() throws Exception {
    Executor executor = new TestExecutorBuilder(directories, binTools).build();
    create().execute(createContext(executor));
    String content = new String(FileSystemUtils.readContentAsLatin1(output));
    String expected = Joiner.on('\n').join("key=foo", "value=bar");
    assertEquals(expected, content);
  }

  public void testKeySameIfSame() throws Exception {
    Artifact outputArtifact2 = new Artifact(scratchFS().getPath("/workspace/out/destination.txt"),
        outputRoot);
    TemplateExpansionAction a = new TemplateExpansionAction(NULL_ACTION_OWNER,
         outputArtifact, Template.forString(TEMPLATE),
         ImmutableList.of(Substitution.of("%key%", "foo")), false);
    TemplateExpansionAction b = new TemplateExpansionAction(NULL_ACTION_OWNER,
         outputArtifact2, Template.forString(TEMPLATE),
         ImmutableList.of(Substitution.of("%key%", "foo")), false);
    assertEquals(a.computeKey(), b.computeKey());
  }

  public void testKeyDiffersForSubstitution() throws Exception {
    Artifact outputArtifact2 = new Artifact(scratchFS().getPath("/workspace/out/destination.txt"),
        outputRoot);
    TemplateExpansionAction a = new TemplateExpansionAction(NULL_ACTION_OWNER,
         outputArtifact, Template.forString(TEMPLATE),
         ImmutableList.of(Substitution.of("%key%", "foo")), false);
    TemplateExpansionAction b = new TemplateExpansionAction(NULL_ACTION_OWNER,
         outputArtifact2, Template.forString(TEMPLATE),
         ImmutableList.of(Substitution.of("%key%", "foo2")), false);
    assertFalse(a.computeKey().equals(b.computeKey()));
  }

  public void testKeyDiffersForExecutable() throws Exception {
    Artifact outputArtifact2 = new Artifact(scratchFS().getPath("/workspace/out/destination.txt"),
        outputRoot);
    TemplateExpansionAction a = new TemplateExpansionAction(NULL_ACTION_OWNER,
         outputArtifact, Template.forString(TEMPLATE),
         ImmutableList.of(Substitution.of("%key%", "foo")), false);
    TemplateExpansionAction b = new TemplateExpansionAction(NULL_ACTION_OWNER,
         outputArtifact2, Template.forString(TEMPLATE),
         ImmutableList.of(Substitution.of("%key%", "foo")), true);
    assertFalse(a.computeKey().equals(b.computeKey()));
  }

  public void testKeyDiffersForTemplates() throws Exception {
    Artifact outputArtifact2 = new Artifact(scratchFS().getPath("/workspace/out/destination.txt"),
        outputRoot);
    TemplateExpansionAction a = new TemplateExpansionAction(NULL_ACTION_OWNER,
         outputArtifact, Template.forString(TEMPLATE),
         ImmutableList.of(Substitution.of("%key%", "foo")), false);
    TemplateExpansionAction b = new TemplateExpansionAction(NULL_ACTION_OWNER,
         outputArtifact2, Template.forString(TEMPLATE + " "),
         ImmutableList.of(Substitution.of("%key%", "foo")), false);
    assertFalse(a.computeKey().equals(b.computeKey()));
  }

  private TemplateExpansionAction createWithArtifact() {
    TemplateExpansionAction result = new TemplateExpansionAction(NULL_ACTION_OWNER,
         inputArtifact, outputArtifact, substitutions, false);
    return result;
  }

  public void testArtifactTemplateHasInput() {
    assertEquals(ImmutableList.of(inputArtifact), createWithArtifact().getInputs());
  }

  public void testArtifactTemplateHasOutput() {
    assertEquals(ImmutableSet.of(outputArtifact), createWithArtifact().getOutputs());
  }

  public void testArtifactTemplateExpansion() throws Exception {
    Executor executor = new TestExecutorBuilder(directories, binTools).build();
    createWithArtifact().execute(createContext(executor));
    String content = new String(FileSystemUtils.readContentAsLatin1(output));
    // The trailing "" is needed because scratchFile implicitly appends "\n".
    String expected = Joiner.on('\n').join("key=foo", "value=bar", "");
    assertEquals(expected, content);
  }

  private ActionExecutionContext createContext(Executor executor) {
    return new ActionExecutionContext(executor, null, null, new FileOutErr(), null);
  }
}
