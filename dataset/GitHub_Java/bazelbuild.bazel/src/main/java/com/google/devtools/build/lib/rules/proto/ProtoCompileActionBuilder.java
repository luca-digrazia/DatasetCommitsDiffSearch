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

package com.google.devtools.build.lib.rules.proto;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.isEmpty;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.ParameterFile;
import com.google.devtools.build.lib.actions.ResourceSet;
import com.google.devtools.build.lib.analysis.FilesToRunProvider;
import com.google.devtools.build.lib.analysis.MakeVariableExpander;
import com.google.devtools.build.lib.analysis.RuleConfiguredTarget;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.actions.CustomCommandLine;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.util.LazyString;
import java.util.Map;
import javax.annotation.Nullable;

/** Constructs actions to run the protocol compiler to generate sources from .proto files. */
public class ProtoCompileActionBuilder {
  private static final String MNEMONIC = "GenProto";
  private static final ResourceSet GENPROTO_RESOURCE_SET =
      ResourceSet.createWithRamCpuIo(100, .1, .0);
  private static final Action[] NO_ACTIONS = new Action[0];

  private RuleContext ruleContext;
  private SupportData supportData;
  private String language;
  private String langPrefix;
  private Iterable<Artifact> outputs;
  private String langParameter;
  private String langPluginName;
  private String langPluginParameter;
  private Supplier<String> langPluginParameterSupplier;
  private boolean hasServices;
  private Iterable<String> additionalCommandLineArguments;
  private Iterable<FilesToRunProvider> additionalTools;

  /** Build a proto compiler commandline argument for use in setXParameter methods. */
  public static String buildProtoArg(String arg, String value, Iterable<String> flags) {
    return String.format(
        "--%s=%s%s", arg, (isEmpty(flags) ? "" : Joiner.on(',').join(flags) + ":"), value);
  }

  public ProtoCompileActionBuilder setRuleContext(RuleContext ruleContext) {
    this.ruleContext = ruleContext;
    return this;
  }

  public ProtoCompileActionBuilder setSupportData(SupportData supportData) {
    this.supportData = supportData;
    return this;
  }

  public ProtoCompileActionBuilder setLanguage(String language) {
    this.language = language;
    return this;
  }

  public ProtoCompileActionBuilder setLangPrefix(String langPrefix) {
    this.langPrefix = langPrefix;
    return this;
  }

  public ProtoCompileActionBuilder allowServices(boolean hasServices) {
    this.hasServices = hasServices;
    return this;
  }

  public ProtoCompileActionBuilder setOutputs(Iterable<Artifact> outputs) {
    this.outputs = outputs;
    return this;
  }

  public ProtoCompileActionBuilder setLangParameter(String langParameter) {
    this.langParameter = langParameter;
    return this;
  }

  public ProtoCompileActionBuilder setLangPluginName(String langPluginName) {
    this.langPluginName = langPluginName;
    return this;
  }

  public ProtoCompileActionBuilder setLangPluginParameter(String langPluginParameter) {
    this.langPluginParameter = langPluginParameter;
    return this;
  }

  public ProtoCompileActionBuilder setLangPluginParameterSupplier(
      Supplier<String> langPluginParameterSupplier) {
    this.langPluginParameterSupplier = langPluginParameterSupplier;
    return this;
  }

  public ProtoCompileActionBuilder setAdditionalCommandLineArguments(
      Iterable<String> additionalCmdLine) {
    this.additionalCommandLineArguments = additionalCmdLine;
    return this;
  }

  public ProtoCompileActionBuilder setAdditionalTools(
      Iterable<FilesToRunProvider> additionalTools) {
    this.additionalTools = additionalTools;
    return this;
  }

  public ProtoCompileActionBuilder(
      RuleContext ruleContext,
      SupportData supportData,
      String language,
      String langPrefix,
      Iterable<Artifact> outputs) {
    this.ruleContext = ruleContext;
    this.supportData = supportData;
    this.language = language;
    this.langPrefix = langPrefix;
    this.outputs = outputs;
  }

  /** Static class to avoid keeping a reference to this builder after build() is called. */
  private static class LazyLangPluginFlag extends LazyString {
    private final String langPrefix;
    private final Supplier<String> langPluginParameter1;

    LazyLangPluginFlag(String langPrefix, Supplier<String> langPluginParameter1) {
      this.langPrefix = langPrefix;
      this.langPluginParameter1 = langPluginParameter1;
    }

    @Override
    public String toString() {
      return String.format("--%s_out=%s", langPrefix, langPluginParameter1.get());
    }
  }

  private static class LazyCommandLineExpansion extends LazyString {
    // E.g., --java_out=%s
    private final String template;
    private final Map<String, ? extends CharSequence> variableValues;

    private LazyCommandLineExpansion(
        String template, Map<String, ? extends CharSequence> variableValues) {
      this.template = template;
      this.variableValues = variableValues;
    }

    @Override
    public String toString() {
      try {
        return MakeVariableExpander.expand(
            template,
            new MakeVariableExpander.Context() {
              @Override
              public String lookupMakeVariable(String var)
                  throws MakeVariableExpander.ExpansionException {
                CharSequence value = variableValues.get(var);
                return value != null ? value.toString() : var;
              }
            });
      } catch (MakeVariableExpander.ExpansionException e) {
        // Squeelch. We don't throw this exception in the lookupMakeVariable implementation above,
        // and we can't report it here anyway, because this code will typically execute in the
        // Execution phase.
      }
      return template;
    }
  }

  public Action[] build() {
    checkState(
        langPluginParameter == null || langPluginParameterSupplier == null,
        "Only one of {langPluginParameter, langPluginParameterSupplier} should be set.");

    if (isEmpty(outputs)) {
      return NO_ACTIONS;
    }

    try {
      return createAction().build(ruleContext);
    } catch (MissingPrerequisiteException e) {
      return NO_ACTIONS;
    }
  }

  private SpawnAction.Builder createAction() {
    SpawnAction.Builder result =
        new SpawnAction.Builder().addTransitiveInputs(supportData.getTransitiveImports());

    // We also depend on the strict protodeps result to ensure this is run.
    if (supportData.getUsedDirectDeps() != null) {
      result.addInput(supportData.getUsedDirectDeps());
    }

    FilesToRunProvider langPluginTarget = getLangPluginTarget();
    if (langPluginTarget != null) {
      result.addTool(langPluginTarget);
    }

    FilesToRunProvider compilerTarget =
        ruleContext.getExecutablePrerequisite(":proto_compiler", RuleConfiguredTarget.Mode.HOST);
    if (ruleContext.hasErrors()) {
      throw new MissingPrerequisiteException();
    }

    if (this.additionalTools != null) {
      for (FilesToRunProvider tool : additionalTools) {
        result.addTool(tool);
      }
    }

    result
        .useParameterFile(ParameterFile.ParameterFileType.UNQUOTED)
        .addOutputs(outputs)
        .setResources(GENPROTO_RESOURCE_SET)
        .useDefaultShellEnvironment()
        .setExecutable(compilerTarget)
        .setCommandLine(createProtoCompilerCommandLine().build())
        .setProgressMessage("Generating " + language + " proto_library " + ruleContext.getLabel())
        .setMnemonic(MNEMONIC);

    return result;
  }

  @Nullable
  private FilesToRunProvider getLangPluginTarget() {
    if (langPluginName == null) {
      return null;
    }
    FilesToRunProvider result =
        ruleContext.getExecutablePrerequisite(langPluginName, RuleConfiguredTarget.Mode.HOST);
    if (ruleContext.hasErrors()) {
      throw new MissingPrerequisiteException();
    }
    return result;
  }

  /** Commandline generator for protoc invocations. */
  private CustomCommandLine.Builder createProtoCompilerCommandLine() {
    CustomCommandLine.Builder result = CustomCommandLine.builder();

    if (langPluginName == null) {
      if (langParameter != null) {
        result.add(langParameter);
      }
    } else {
      FilesToRunProvider langPluginTarget = getLangPluginTarget();
      Supplier<String> langPluginParameter1 =
          langPluginParameter == null
              ? langPluginParameterSupplier
              : Suppliers.ofInstance(langPluginParameter);

      Preconditions.checkArgument(langParameter == null);
      Preconditions.checkArgument(langPluginParameter1 != null);
      // We pass a separate langPluginName as there are plugins that cannot be overridden
      // and thus we have to deal with "$xx_plugin" and "xx_plugin".
      result.add(
          String.format(
              "--plugin=protoc-gen-%s=%s",
              langPrefix, langPluginTarget.getExecutable().getExecPathString()));
      result.add(new LazyLangPluginFlag(langPrefix, langPluginParameter1));
    }

    result.add(ruleContext.getFragment(ProtoConfiguration.class).protocOpts());

    // Add include maps
    result.add(new ProtoCommandLineArgv(supportData.getTransitiveImports()));

    for (Artifact src : supportData.getDirectProtoSources()) {
      result.addPath(src.getRootRelativePath());
    }

    if (!hasServices) {
      result.add("--disallow_services");
    }

    if (additionalCommandLineArguments != null) {
      result.add(additionalCommandLineArguments);
    }

    return result;
  }

  /**
   * Static inner class since these objects live into the execution phase and so they must not keep
   * alive references to the surrounding analysis-phase objects.
   */
  private static class ProtoCommandLineArgv extends CustomCommandLine.CustomMultiArgv {
    private final Iterable<Artifact> transitiveImports;

    ProtoCommandLineArgv(Iterable<Artifact> transitiveImports) {
      this.transitiveImports = transitiveImports;
    }

    @Override
    public Iterable<String> argv() {
      ImmutableList.Builder<String> builder = ImmutableList.builder();
      for (Artifact artifact : transitiveImports) {
        builder.add(
            "-I"
                + artifact.getRootRelativePath().getPathString()
                + "="
                + artifact.getExecPathString());
      }
      return builder.build();
    }
  }

  /** Signifies that a prerequisite could not be satisfied. */
  private static class MissingPrerequisiteException extends RuntimeException {}

  /**
   * Registers actions to generate code from .proto files.
   *
   * <p>This method uses information from proto_lang_toolchain() rules. New rules should use this
   * method instead of the soup of methods above.
   *
   * @param toolchainInvocations See {@link #createCommandLineFromToolchains}.
   * @param outputs The artifacts that the resulting action must create.
   * @param flavorName e.g., "Java (Immutable)"
   * @param allowServices If false, the compilation will break if any .proto file has service
   *     definitions.
   */
  public static void registerActions(
      RuleContext ruleContext,
      Map<String, ToolchainInvocation> toolchainInvocations,
      SupportData supportData,
      Iterable<Artifact> outputs,
      String flavorName,
      boolean allowServices) {
    if (isEmpty(outputs)) {
      return;
    }

    SpawnAction.Builder result =
        new SpawnAction.Builder().addTransitiveInputs(supportData.getTransitiveImports());

    for (ToolchainInvocation invocation : toolchainInvocations.values()) {
      ProtoLangToolchainProvider toolchain = invocation.toolchain;
      if (toolchain.pluginExecutable() != null) {
        result.addTool(toolchain.pluginExecutable());
      }
    }

    // We also depend on the strict protodeps result to ensure this is run.
    if (supportData.getUsedDirectDeps() != null) {
      result.addInput(supportData.getUsedDirectDeps());
    }

    FilesToRunProvider compilerTarget =
        ruleContext.getExecutablePrerequisite(":proto_compiler", RuleConfiguredTarget.Mode.HOST);
    if (ruleContext.hasErrors()) {
      throw new MissingPrerequisiteException();
    }

    result
        .useParameterFile(ParameterFile.ParameterFileType.UNQUOTED)
        .addOutputs(outputs)
        .setResources(GENPROTO_RESOURCE_SET)
        .useDefaultShellEnvironment()
        .setExecutable(compilerTarget)
        .setCommandLine(
            createCommandLineFromToolchains(
                toolchainInvocations,
                supportData,
                allowServices,
                ruleContext.getFragment(ProtoConfiguration.class).protocOpts()))
        .setProgressMessage("Generating " + flavorName + " proto_library " + ruleContext.getLabel())
        .setMnemonic(MNEMONIC);

    ruleContext.registerAction(result.build(ruleContext));
  }

  /**
   * Constructs command-line arguments to execute proto-compiler.
   *
   * <ul>
   *   <li>Each toolchain contributes a command-line, formatted from its commandLine() method.
   *   <li>$(OUT) is replaced with the outReplacement field of ToolchainInvocation.
   *   <li>$(PLUGIN_out) is replaced with PLUGIN_<key>_out where 'key' is the key of
   *       toolchainInvocations. The key thus allows multiple plugins in one command-line.
   *   <li>If a toolchain's {@code plugin()} is non-null, we point at it by emitting
   *       --plugin=protoc-gen-PLUGIN_<key>=<location of plugin>.
   * </ul>
   *
   * @return a command-line to pass to proto-compiler.
   */
  @VisibleForTesting
  static CustomCommandLine createCommandLineFromToolchains(
      Map<String, ToolchainInvocation> toolchainInvocations,
      SupportData supportData,
      boolean allowServices,
      ImmutableList<String> protocOpts) {
    CustomCommandLine.Builder cmdLine = CustomCommandLine.builder();

    cmdLine.add(protocOpts);

    // Add include maps
    cmdLine.add(new ProtoCommandLineArgv(supportData.getTransitiveImports()));

    for (Artifact src : supportData.getDirectProtoSources()) {
      cmdLine.addPath(src.getRootRelativePath());
    }

    if (!allowServices) {
      cmdLine.add("--disallow_services");
    }

    for (Map.Entry<String, ToolchainInvocation> entry : toolchainInvocations.entrySet()) {
      String pluginSuffix = entry.getKey();
      ToolchainInvocation invocation = entry.getValue();
      ProtoLangToolchainProvider toolchain = invocation.toolchain;

      cmdLine.add(
          new LazyCommandLineExpansion(
              toolchain.commandLine(),
              ImmutableMap.of(
                  "OUT",
                  invocation.outReplacement,
                  "PLUGIN_OUT",
                  String.format("PLUGIN_%s_out", pluginSuffix))));

      if (toolchain.pluginExecutable() != null) {
        cmdLine.add(
            String.format(
                "--plugin=protoc-gen-%s=%s",
                String.format("PLUGIN_%s", pluginSuffix),
                toolchain.pluginExecutable().getExecutable().getExecPathString()));
      }
    }
    return cmdLine.build();
  }

  /**
   * Describes a toolchain and the value to replace for a $(OUT) that might appear in its
   * commandLine() (e.g., "bazel-out/foo.srcjar").
   */
  public static class ToolchainInvocation {
    public final ProtoLangToolchainProvider toolchain;
    final CharSequence outReplacement;

    public ToolchainInvocation(ProtoLangToolchainProvider toolchain, CharSequence outReplacement) {
      this.toolchain = toolchain;
      this.outReplacement = outReplacement;
    }
  }
}
