// Copyright 2015 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.rules.android;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.ParameterFile.ParameterFileType;
import com.google.devtools.build.lib.analysis.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.analysis.RuleConfiguredTargetBuilder;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.TransitiveInfoCollection;
import com.google.devtools.build.lib.analysis.actions.CustomCommandLine;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.packages.BuildType;
import com.google.devtools.build.lib.rules.java.JavaUtil;
import com.google.devtools.build.lib.rules.java.ProguardSpecProvider;
import com.google.devtools.build.lib.syntax.Type;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Helper class for Android IDL processing.
 */
public class AndroidIdlHelper {

  private final RuleContext ruleContext;
  private final AndroidIdlProvider androidIdlProvider;
  private final Map<Artifact, Artifact> translatedIdlSources;
  private final Artifact idlClassJar;
  private final Artifact idlSourceJar;

  /**
   * Creates a new AndroidIdlHelper wrapping the given {@code ruleContext}.
   *
   * @param ruleContext The rule context whose idl attributes will be used to collect .aidl files.
   * @param baseArtifact An artifact used to calculate the paths for the IDL class and source jars.
   */
  public AndroidIdlHelper(RuleContext ruleContext, Artifact baseArtifact) {
    this.ruleContext = ruleContext;

    checkIdlRootImport(ruleContext);

    Collection<Artifact> idls = getIdlSrcs(ruleContext);

    if (!idls.isEmpty() && !ruleContext.hasErrors()) {
      translatedIdlSources = generateTranslatedIdlArtifacts(ruleContext, idls);
      idlClassJar = createIdlJar(baseArtifact, "-idl.jar");
      idlSourceJar = createIdlJar(baseArtifact, "-idl.srcjar");
    } else {
      translatedIdlSources = ImmutableMap.of();
      idlClassJar = null;
      idlSourceJar = null;
    }

    androidIdlProvider = createAndroidIdlProvider(
        ruleContext, idlClassJar, idlSourceJar);
  }

  /**
   * Adds the necessary providers to the {@code builder}.
   *
   * Adds an {@link AndroidIdlProvider} to the target, and adds the transitive generated IDL jars to
   * the IDL_JARS_OUTPUT_GROUP. This also generates the actions to compile the .aidl files to .java,
   * as well as the .jar and .srcjar files consisting of only the IDL-generated source and class
   * files.
   *
   * @param builder The target builder to add the providers to.
   * @param classJar The class jar to be separated into the IDL class jar.
   * @param manifestProtoOutput The manifest generated by JavaBuilder, for identifying IDL-generated
   *     class files in the class jar.
   */
  public void addTransitiveInfoProviders(RuleConfiguredTargetBuilder builder,
      Artifact classJar, Artifact manifestProtoOutput) {
    if (!translatedIdlSources.isEmpty()) {
      generateAndroidIdlCompilationActions(
          ruleContext, androidIdlProvider, translatedIdlSources);
      createIdlClassJarAction(ruleContext, classJar, translatedIdlSources.values(),
          manifestProtoOutput, idlClassJar, idlSourceJar);
    }
    builder
        .add(AndroidIdlProvider.class, androidIdlProvider)
        .addOutputGroup(
            AndroidSemantics.IDL_JARS_OUTPUT_GROUP, androidIdlProvider.getTransitiveIdlJars());
  }

  /**
   * Returns the root directory under which idl_srcs and idl_parcelables are located in this rule.
   */
  public String getIdlImportRoot() {
    return hasExplicitlySpecifiedIdlImportRoot(ruleContext) ? getIdlImportRoot(ruleContext) : null;
  }

  /**
   * Returns the raw (non-processed) idl_srcs, not including parcelable marker files.
   */
  public Collection<Artifact> getIdlSources() {
    return translatedIdlSources.keySet();
  }

  /**
   * Returns the idl_parcelables, consisting of parcelable marker files defined on this rule.
   */
  public Collection<Artifact> getIdlParcelables() {
    return getIdlParcelables(ruleContext);
  }

  /**
   * Returns the generated Java sources created from the idl_srcs.
   */
  public Collection<Artifact> getIdlGeneratedJavaSources() {
    return translatedIdlSources.values();
  }

  /**
   * Returns the jar containing class files derived from the .aidl files.
   *
   * <p>Will be null if there are no idl_srcs.
   */
  @Nullable
  public Artifact getIdlClassJar() {
    return idlClassJar;
  }

  /**
   * Returns the jar containing source files derived from the .aidl files.
   *
   * <p>Will be null if there are no idl_srcs.
   */
  @Nullable
  public Artifact getIdlSourceJar() {
    return idlSourceJar;
  }

  public static boolean hasIdlSrcs(RuleContext ruleContext) {
    return ruleContext.getRule().isAttrDefined("idl_srcs", BuildType.LABEL_LIST);
  }

  /**
   * Returns a new list with the idl libs added to the given list if necessary, or the same list.
   */
  public static ImmutableList<TransitiveInfoCollection> addSupportLibs(RuleContext ruleContext,
      ImmutableList<TransitiveInfoCollection> deps) {
    TransitiveInfoCollection aidlLib = AndroidSdkProvider.fromRuleContext(ruleContext).getAidlLib();
    if (aidlLib == null) {
      return deps;
    }
    return ImmutableList.<TransitiveInfoCollection>builder()
        .addAll(deps)
        .add(aidlLib)
        .build();
  }

  public static void addSupportLibProguardConfigs(RuleContext ruleContext,
      NestedSetBuilder<Artifact> proguardConfigsbuilder) {
    TransitiveInfoCollection aidlLib = AndroidSdkProvider.fromRuleContext(ruleContext).getAidlLib();
    if (aidlLib != null) {
      proguardConfigsbuilder.addTransitive(
          aidlLib.getProvider(ProguardSpecProvider.class).getTransitiveProguardSpecs());
    }
  }

  /**
   * Generates an artifact by replacing the extension of the input with the suffix.
   */
  private Artifact createIdlJar(Artifact baseArtifact, String suffix) {
    return ruleContext.getDerivedArtifact(
        FileSystemUtils.replaceExtension(baseArtifact.getRootRelativePath(), suffix),
        baseArtifact.getRoot());
  }

  /**
   * Returns the idl_parcelables defined on the given rule.
   */
  private static ImmutableList<Artifact> getIdlParcelables(RuleContext ruleContext) {
    return ruleContext.getRule().isAttrDefined("idl_parcelables", BuildType.LABEL_LIST)
        ? ImmutableList.copyOf(ruleContext.getPrerequisiteArtifacts(
        "idl_parcelables", Mode.TARGET).filter(AndroidRuleClasses.ANDROID_IDL).list())
        : ImmutableList.<Artifact>of();
  }

  /**
   * Returns the idl_srcs defined on the given rule.
   */
  private static Collection<Artifact> getIdlSrcs(RuleContext ruleContext) {
    if (!hasIdlSrcs(ruleContext)) {
      return ImmutableList.of();
    }
    checkIdlSrcsSamePackage(ruleContext);
    return ruleContext.getPrerequisiteArtifacts(
        "idl_srcs", Mode.TARGET).filter(AndroidRuleClasses.ANDROID_IDL).list();
  }

  /**
   * Checks that all of the idl_srcs in the given rule are in the same package as the rule itself.
   */
  private static void checkIdlSrcsSamePackage(RuleContext ruleContext) {
    PathFragment packageName = ruleContext.getLabel().getPackageFragment();
    Collection<Artifact> idls = ruleContext
        .getPrerequisiteArtifacts("idl_srcs", Mode.TARGET)
        .filter(AndroidRuleClasses.ANDROID_IDL)
        .list();
    for (Artifact idl : idls) {
      Label idlLabel = idl.getOwner();
      if (!packageName.equals(idlLabel.getPackageFragment())) {
        ruleContext.attributeError("idl_srcs", "do not import '" + idlLabel + "' directly. "
            + "You should either move the file to this package or depend on "
            + "an appropriate rule there");
      }
    }
  }

  /**
   * Generates matching .java sources for the given .aidl sources.
   *
   * @return A mapping from .aidl input to .java output.
   */
  private static ImmutableMap<Artifact, Artifact> generateTranslatedIdlArtifacts(
      RuleContext ruleContext, Collection<Artifact> idls) {
    ImmutableMap.Builder<Artifact, Artifact> outputJavaSources = ImmutableMap.builder();
    String ruleName = ruleContext.getRule().getName();
    // for each aidl file use aggregated preprocessed files to generate Java code
    for (Artifact idl : idls) {
      // Reconstruct the package tree under <rule>_aidl to avoid a name conflict
      // if the same AIDL files are used in multiple targets.
      PathFragment javaOutputPath = FileSystemUtils.replaceExtension(
          new PathFragment(ruleName + "_aidl").getRelative(idl.getRootRelativePath()),
          ".java");
      Artifact output = ruleContext.getGenfilesArtifact(javaOutputPath.getPathString());
      outputJavaSources.put(idl, output);
    }
    return outputJavaSources.build();
  }

  /**
   * Generates the actions to compile the given .aidl sources into .java sources.
   *
   * @param ruleContext The rule context in which to generate the actions.
   * @param transitiveIdlImportData A provider to supply the artifacts and import roots to give to
   *     the compiler.
   * @param translatedIdlSources A map from input .aidl to output .java of files to be compiled.
   */
  private static void generateAndroidIdlCompilationActions(
      RuleContext ruleContext,
      AndroidIdlProvider transitiveIdlImportData,
      Map<Artifact, Artifact> translatedIdlSources) {
    AndroidSdkProvider sdk = AndroidSdkProvider.fromRuleContext(ruleContext);
    List<String> preprocessedArgs = new ArrayList<>();

    // add import roots so the aidl compiler will know where to look for the imports
    for (String idlImport : transitiveIdlImportData.getTransitiveIdlImportRoots()) {
      preprocessedArgs.add("-I" + idlImport);
    }

    preprocessedArgs.add("-p" + sdk.getFrameworkAidl().getExecPathString());

    for (Entry<Artifact, Artifact> entry : translatedIdlSources.entrySet()) {
      createAndroidIdlAction(ruleContext, entry.getKey(),
          transitiveIdlImportData.getTransitiveIdlImports(),
          entry.getValue(), preprocessedArgs);
    }
  }

  /**
   * Creates an action to split out classes and source files created by aidls.
   *
   * @param ruleContext The rule context in which to generate the action.
   * @param classJar The class jar to divide into IDL class and source jars.
   * @param generatedIdlJavaFiles The source files which should be put into the source jar and used
   *     to determine the classes to take.
   * @param manifestProtoOutput The protobuf containing the manifest generated from JavaBuilder.
   * @param idlClassJar The artifact into which the IDL class jar should be written.
   * @param idlSourceJar The artifact into which the IDL source jar should be written.
   */
  private static void createIdlClassJarAction(
      RuleContext ruleContext,
      Artifact classJar,
      Iterable<Artifact> generatedIdlJavaFiles,
      Artifact manifestProtoOutput,
      Artifact idlClassJar,
      Artifact idlSourceJar) {
    String basename = FileSystemUtils.removeExtension(classJar.getExecPath().getBaseName());
    PathFragment idlTempDir = ruleContext.getConfiguration()
        .getBinDirectory(ruleContext.getRule().getRepository())
        .getExecPath()
        .getRelative(ruleContext.getUniqueDirectory("_idl"))
        .getRelative(basename + "_temp");
    ruleContext.registerAction(new SpawnAction.Builder()
        .addInput(manifestProtoOutput)
        .addInput(classJar)
        .addInputs(generatedIdlJavaFiles)
        .addOutput(idlClassJar)
        .addOutput(idlSourceJar)
        .setExecutable(ruleContext.getExecutablePrerequisite("$idlclass", Mode.HOST))
        .setCommandLine(CustomCommandLine.builder()
            .addExecPath("--manifest_proto", manifestProtoOutput)
            .addExecPath("--class_jar", classJar)
            .addExecPath("--output_class_jar", idlClassJar)
            .addExecPath("--output_source_jar", idlSourceJar)
            .add("--temp_dir").addPath(idlTempDir)
            .addExecPaths(generatedIdlJavaFiles)
            .build())
        .useParameterFile(ParameterFileType.SHELL_QUOTED)
        .setProgressMessage("Building idl jars " + idlClassJar.prettyPrint())
        .setMnemonic("AndroidIdlJars")
        .build(ruleContext));
  }

  /**
   * Creates an action to convert an .aidl source into a .java output.
   *
   * @param ruleContext The rule context in which to generate the action.
   * @param idl The .aidl file to be converted to .java.
   * @param idlImports The artifacts which should be accessible to this compilation action.
   * @param output The .java file where the .aidl file will be converted to.
   * @param importArgs The arguments defining the import roots and framework .aidl.
   */
  private static void createAndroidIdlAction(RuleContext ruleContext,
      Artifact idl, NestedSet<Artifact> idlImports,
      Artifact output, List<String> importArgs) {
    AndroidSdkProvider sdk = AndroidSdkProvider.fromRuleContext(ruleContext);
    ruleContext.registerAction(new SpawnAction.Builder()
        .setExecutable(sdk.getAidl())
        .addInput(idl)
        .addTransitiveInputs(idlImports)
        .addInput(sdk.getFrameworkAidl())
        .addOutput(output)
        .addArgument("-b") // Fail if trying to compile a parcelable.
        .addArguments(importArgs)
        .addArgument(idl.getExecPathString())
        .addArgument(output.getExecPathString())
        .setProgressMessage("Android IDL generation")
        .setMnemonic("AndroidIDLGnerate")
        .build(ruleContext));
  }

  /**
   * Returns the union of "idl_srcs" and "idl_parcelables", i.e. all .aidl files
   * provided by this library that contribute to .aidl --> .java compilation.
   */
  private static Collection<Artifact> getIdlImports(RuleContext ruleContext) {
    return ImmutableList.<Artifact>builder()
        .addAll(getIdlParcelables(ruleContext))
        .addAll(getIdlSrcs(ruleContext))
        .build();
  }

  /**
   * Collects the importable .aidl files and AIDL class/source jars from this rule and its deps.
   *
   * @param ruleContext The rule context from which to harvest .aidl sources and parcelables, as
   *     well as dependencies.
   * @param idlClassJar An artifact corresponding to an AIDL class jar for this rule, or null if one
   *     does not exist.
   * @param idlSourceJar An artifact corresponding to an AIDL source jar for this rule, or null if
   *     one does not exist.
   * @return A provider containing the collected data, suitable to be provided by this rule.
   */
  private static AndroidIdlProvider createAndroidIdlProvider(RuleContext ruleContext,
      @Nullable Artifact idlClassJar, @Nullable Artifact idlSourceJar) {
    NestedSetBuilder<String> rootsBuilder = NestedSetBuilder.naiveLinkOrder();
    NestedSetBuilder<Artifact> importsBuilder = NestedSetBuilder.naiveLinkOrder();
    NestedSetBuilder<Artifact> jarsBuilder = NestedSetBuilder.stableOrder();
    if (idlClassJar != null) {
      jarsBuilder.add(idlClassJar);
    }
    if (idlSourceJar != null) {
      jarsBuilder.add(idlSourceJar);
    }

    for (AndroidIdlProvider dep : AndroidCommon.getTransitivePrerequisites(
        ruleContext, Mode.TARGET, AndroidIdlProvider.class)) {
      rootsBuilder.addTransitive(dep.getTransitiveIdlImportRoots());
      importsBuilder.addTransitive(dep.getTransitiveIdlImports());
      jarsBuilder.addTransitive(dep.getTransitiveIdlJars());
    }

    Collection<Artifact> idlImports = getIdlImports(ruleContext);
    if (!hasExplicitlySpecifiedIdlImportRoot(ruleContext)) {
      for (Artifact idlImport : idlImports) {
        PathFragment javaRoot = JavaUtil.getJavaRoot(idlImport.getExecPath());
        if (javaRoot == null) {
          ruleContext.ruleError("Cannot determine java/javatests root for import "
              + idlImport.getExecPathString());
        } else {
          rootsBuilder.add(javaRoot.toString());
        }
      }
    } else {
      PathFragment pkgFragment = ruleContext.getLabel().getPackageFragment();
      Set<PathFragment> idlImportRoots = new HashSet<>();
      for (Artifact idlImport : idlImports) {
        idlImportRoots.add(idlImport.getRoot().getExecPath()
            .getRelative(pkgFragment)
            .getRelative(getIdlImportRoot(ruleContext)));
      }
      for (PathFragment idlImportRoot : idlImportRoots) {
        rootsBuilder.add(idlImportRoot.toString());
      }
    }
    importsBuilder.addAll(idlImports);

    return AndroidIdlProvider.create(
        rootsBuilder.build(), importsBuilder.build(), jarsBuilder.build());
  }

  /**
   * Checks that idl_import_root is only set if idl_srcs or idl_parcelables was.
   */
  private static void checkIdlRootImport(RuleContext ruleContext) {
    if (hasExplicitlySpecifiedIdlImportRoot(ruleContext)
        && !hasExplicitlySpecifiedIdlSrcsOrParcelables(ruleContext)) {
      ruleContext.attributeError("idl_import_root",
          "Neither idl_srcs nor idl_parcelables were specified, "
              + "but 'idl_import_root' attribute was set");
    }
  }

  private static boolean hasExplicitlySpecifiedIdlImportRoot(RuleContext ruleContext) {
    return ruleContext.getRule().isAttributeValueExplicitlySpecified("idl_import_root");
  }

  private static boolean hasExplicitlySpecifiedIdlSrcsOrParcelables(RuleContext ruleContext) {
    return ruleContext.getRule().isAttributeValueExplicitlySpecified("idl_srcs")
        || ruleContext.getRule().isAttributeValueExplicitlySpecified("idl_parcelables");
  }

  private static String getIdlImportRoot(RuleContext ruleContext) {
    return ruleContext.attributes().get("idl_import_root", Type.STRING);
  }
}
