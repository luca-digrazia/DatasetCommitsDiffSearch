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

package com.google.devtools.build.lib.packages;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.Constants;
import com.google.devtools.build.lib.collect.CollectionUtils;
import com.google.devtools.build.lib.collect.ImmutableSortedKeyMap;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.packages.AttributeMap.AcceptsLabelAttribute;
import com.google.devtools.build.lib.packages.License.DistributionType;
import com.google.devtools.build.lib.packages.PackageDeserializer.PackageDeserializationException;
import com.google.devtools.build.lib.packages.PackageFactory.Globber;
import com.google.devtools.build.lib.syntax.FuncallExpression;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.lib.syntax.Label.SyntaxException;
import com.google.devtools.build.lib.util.Pair;
import com.google.devtools.build.lib.vfs.Canonicalizer;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A package, which is a container of {@link Rule}s, each of
 * which contains a dictionary of named attributes.
 *
 * <p>Package instances are intended to be immutable and for all practical
 * purposes can be treated as such. Note, however, that some member variables
 * exposed via the public interface are not strictly immutable, so until their
 * types are guaranteed immutable we're not applying the {@code @Immutable}
 * annotation here.
 */
public class Package implements Serializable {

  /**
   * Common superclass for all name-conflict exceptions.
   */
  public static class NameConflictException extends Exception {
    protected NameConflictException(String message) {
      super(message);
    }
  }

  /**
   * The repository identifier for this package.
   */
  private final PackageIdentifier packageIdentifier;

  /**
   * The name of the package, e.g. "foo/bar".
   */
  protected final String name;

  /**
   * Like name, but in the form of a PathFragment.
   */
  private final PathFragment nameFragment;

  /**
   * The filename of this package's BUILD file.
   */
  protected Path filename;

  /**
   * The directory in which this package's BUILD file resides.  All InputFile
   * members of the packages are located relative to this directory.
   */
  private Path packageDirectory;

  /**
   * The name of the workspace this package is in. Used as a prefix for the runfiles directory.
   * This can be set in the WORKSPACE file. This must be a valid target name.
   */
  protected String workspaceName = Constants.DEFAULT_RUNFILES_PREFIX;

  /**
   * The root of the source tree in which this package was found. It is an invariant that
   * {@code sourceRoot.getRelative(name).equals(packageDirectory)}.
   */
  private Path sourceRoot;

  /**
   * The "Make" environment of this package, containing package-local
   * definitions of "Make" variables.
   */
  private MakeEnvironment makeEnv;

  /**
   * The collection of all targets defined in this package, indexed by name.
   */
  protected Map<String, Target> targets;

  /**
   * Default visibility for rules that do not specify it. null is interpreted
   * as VISIBILITY_PRIVATE.
   */
  private RuleVisibility defaultVisibility;
  private boolean defaultVisibilitySet;

  /**
   * Default package-level 'obsolete' value for rules that do not specify it.
   */
  private boolean defaultObsolete = false;

  /**
   * Default package-level 'testonly' value for rules that do not specify it.
   */
  private boolean defaultTestOnly = false;

  /**
   * Default package-level 'deprecation' value for rules that do not specify it.
   */
  private String defaultDeprecation;

  /**
   * Default header strictness checking for rules that do not specify it.
   */
  private String defaultHdrsCheck;

  /**
   * Default copts for cc_* rules.  The rules' individual copts will append to
   * this value.
   */
  private ImmutableList<String> defaultCopts;

  /**
   * The InputFile target corresponding to this package's BUILD file.
   */
  private InputFile buildFile;

  /**
   * True iff this package's BUILD files contained lexical or grammatical
   * errors, or experienced errors during evaluation, or semantic errors during
   * the construction of any rule.
   *
   * <p>Note: A package containing errors does not necessarily prevent a build;
   * if all the rules needed for a given build were constructed prior to the
   * first error, the build may proceed.
   */
  private boolean containsErrors;

  /**
   * True iff this package contains errors that were caused by temporary conditions (e.g. an I/O
   * error). If this is true, {@link #containsErrors} is also true.
   */
  private boolean containsTemporaryErrors;

  /**
   * The set of labels subincluded by this package.
   */
  private Set<Label> subincludes;

  /**
   * The list of transitive closure of the Skylark file dependencies.
   */
  private ImmutableList<Label> skylarkFileDependencies;

  /**
   * The package's default "licenses" and "distribs" attributes, as specified
   * in calls to licenses() and distribs() in the BUILD file.
   */
  // These sets contain the values specified by the most recent licenses() or
  // distribs() declarations encountered during package parsing:
  private License defaultLicense;
  private Set<License.DistributionType> defaultDistributionSet;


  /**
   * The names of the package() attributes that declare default values for rule
   * {@link RuleClass#COMPATIBLE_ENVIRONMENT_ATTR} and {@link RuleClass#RESTRICTED_ENVIRONMENT_ATTR}
   * values when not explicitly specified.
   */
  public static final String DEFAULT_COMPATIBLE_WITH_ATTRIBUTE = "default_compatible_with";
  public static final String DEFAULT_RESTRICTED_TO_ATTRIBUTE = "default_restricted_to";

  private Set<Label> defaultCompatibleWith = ImmutableSet.of();
  private Set<Label> defaultRestrictedTo = ImmutableSet.of();

  private ImmutableSet<String> features;

  private ImmutableList<Event> events;

  // Hack to avoid having to copy every attribute. See #readObject and #readResolve.
  // This will always be null for externally observable instances.
  private Package deserializedPkg = null;

  /**
   * Package initialization, part 1 of 3: instantiates a new package with the
   * given name.
   *
   * <p>As part of initialization, {@link Builder} constructs {@link InputFile}
   * and {@link PackageGroup} instances that require a valid Package instance where
   * {@link Package#getNameFragment()} is accessible. That's why these settings are
   * applied here at the start.
   *
   * @precondition {@code name} must be a suffix of
   * {@code filename.getParentDirectory())}.
   */
  protected Package(PackageIdentifier packageId) {
    this.packageIdentifier = packageId;
    this.nameFragment = Canonicalizer.fragments().intern(packageId.getPackageFragment());
    this.name = nameFragment.getPathString();
  }

  private void writeObject(ObjectOutputStream out) {
    com.google.devtools.build.lib.query2.proto.proto2api.Build.Package pb =
        PackageSerializer.serializePackage(this);
    try {
      pb.writeDelimitedTo(out);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void readObject(ObjectInputStream in) throws IOException {
    com.google.devtools.build.lib.query2.proto.proto2api.Build.Package pb =
        com.google.devtools.build.lib.query2.proto.proto2api.Build.Package.parseDelimitedFrom(in);
    Package pkg;
    try {
      pkg = new PackageDeserializer(null, null).deserialize(pb);
    } catch (PackageDeserializationException e) {
      throw new IllegalStateException(e);
    }
    deserializedPkg = pkg;
  }

  protected Object readResolve() {
    // This method needs to be protected so serialization works for subclasses.
    return deserializedPkg;
  }

  // See: http://docs.oracle.com/javase/6/docs/platform/serialization/spec/input.html#6053
  @SuppressWarnings("unused")
  private void readObjectNoData() {
    throw new IllegalStateException();
  }

  /** Returns this packages' identifier. */
  public PackageIdentifier getPackageIdentifier() {
    return packageIdentifier;
  }

  /**
   * Package initialization: part 2 of 3: sets this package's default header
   * strictness checking.
   *
   * <p>This is needed to support C++-related rule classes
   * which accesses {@link #getDefaultHdrsCheck} from the still-under-construction
   * package.
   */
  protected void setDefaultHdrsCheck(String defaultHdrsCheck) {
    this.defaultHdrsCheck = defaultHdrsCheck;
  }

  /**
   * Set the default 'obsolete' value for this package.
   */
  protected void setDefaultObsolete(boolean obsolete) {
    defaultObsolete = obsolete;
  }

  /**
   * Set the default 'testonly' value for this package.
   */
  protected void setDefaultTestOnly(boolean testOnly) {
    defaultTestOnly = testOnly;
  }

  /**
   * Set the default 'deprecation' value for this package.
   */
  protected void setDefaultDeprecation(String deprecation) {
    defaultDeprecation = deprecation;
  }

  /**
   * Sets the default value to use for a rule's {@link RuleClass#COMPATIBLE_ENVIRONMENT_ATTR}
   * attribute when not explicitly specified by the rule.
   */
  protected void setDefaultCompatibleWith(Set<Label> environments) {
    defaultCompatibleWith = environments;
  }

  /**
   * Sets the default value to use for a rule's {@link RuleClass#RESTRICTED_ENVIRONMENT_ATTR}
   * attribute when not explicitly specified by the rule.
   */
  protected void setDefaultRestrictedTo(Set<Label> environments) {
    defaultRestrictedTo = environments;
  }

  public static Path getSourceRoot(Path buildFile, PathFragment nameFragment) {
    Path current = buildFile.getParentDirectory();
    for (int i = 0, len = nameFragment.segmentCount(); i < len && current != null; i++) {
      current = current.getParentDirectory();
    }
    return current;
  }

  /**
   * Package initialization: part 3 of 3: applies all other settings and completes
   * initialization of the package.
   *
   * <p>Only after this method is called can this package be considered "complete"
   * and be shared publicly.
   */
  protected void finishInit(AbstractBuilder<?, ?> builder) {
    // If any error occurred during evaluation of this package, consider all
    // rules in the package to be "in error" also (even if they were evaluated
    // prior to the error).  This behaviour is arguably stricter than need be,
    // but stopping a build only for some errors but not others creates user
    // confusion.
    if (builder.containsErrors) {
      for (Rule rule : builder.getTargets(Rule.class)) {
        rule.setContainsErrors();
      }
    }
    this.filename = builder.filename;
    this.packageDirectory = filename.getParentDirectory();

    this.sourceRoot = getSourceRoot(filename, nameFragment);
    if ((sourceRoot == null
        || !sourceRoot.getRelative(nameFragment).equals(packageDirectory))
        && !filename.getBaseName().equals("WORKSPACE")) {
      throw new IllegalArgumentException(
          "Invalid BUILD file name for package '" + name + "': " + filename);
    }

    this.makeEnv = builder.makeEnv.build();
    this.targets = ImmutableSortedKeyMap.copyOf(builder.targets);
    this.defaultVisibility = builder.defaultVisibility;
    this.defaultVisibilitySet = builder.defaultVisibilitySet;
    if (builder.defaultCopts == null) {
      this.defaultCopts = ImmutableList.of();
    } else {
      this.defaultCopts = ImmutableList.copyOf(builder.defaultCopts);
    }
    this.buildFile = builder.buildFile;
    this.containsErrors = builder.containsErrors;
    this.containsTemporaryErrors = builder.containsTemporaryErrors;
    this.subincludes = builder.subincludes.keySet();
    this.skylarkFileDependencies = builder.skylarkFileDependencies;
    this.defaultLicense = builder.defaultLicense;
    this.defaultDistributionSet = builder.defaultDistributionSet;
    this.features = ImmutableSortedSet.copyOf(builder.features);
    this.events = ImmutableList.copyOf(builder.events);
  }

  /**
   * Returns the list of subincluded labels on which the validity of this package depends.
   */
  public Set<Label> getSubincludeLabels() {
    return subincludes;
  }

  /**
   * Returns the list of transitive closure of the Skylark file dependencies of this package.
   */
  public ImmutableList<Label> getSkylarkFileDependencies() {
    return skylarkFileDependencies;
  }

  /**
   * Returns the filename of the BUILD file which defines this package. The
   * parent directory of the BUILD file is the package directory.
   */
  public Path getFilename() {
    return filename;
  }

  /**
   * Returns the source root (a directory) beneath which this package's BUILD file was found.
   *
   * Assumes invariant:
   * {@code getSourceRoot().getRelative(getName()).equals(getPackageDirectory())}
   */
  public Path getSourceRoot() {
    return sourceRoot;
  }

  /**
   * Returns the directory containing the package's BUILD file.
   */
  public Path getPackageDirectory() {
    return packageDirectory;
  }

  /**
   * Returns the name of this package. If this build is using external repositories then this name
   * may not be unique!
   */
  public String getName() {
    return name;
  }

  /**
   * Like {@link #getName}, but has type {@code PathFragment}.
   */
  public PathFragment getNameFragment() {
    return nameFragment;
  }

  /**
   * Returns the "Make" value from the package's make environment whose name
   * is "varname", or null iff the variable is not defined in the environment.
   */
  public String lookupMakeVariable(String varname, String platform) {
    return makeEnv.lookup(varname, platform);
  }

  /**
   * Returns the make environment. This should only ever be used for serialization -- how the
   * make variables are implemented is an implementation detail.
   */
  MakeEnvironment getMakeEnvironment() {
    return makeEnv;
  }

  /**
   * Returns the label of this package's BUILD file.
   *
   * Typically <code>getBuildFileLabel().getName().equals("BUILD")</code> --
   * though not necessarily: data in a subdirectory of a test package may use a
   * different filename to avoid inadvertently creating a new package.
   */
  Label getBuildFileLabel() {
    return buildFile.getLabel();
  }

  /**
   * Returns the InputFile target for this package's BUILD file.
   */
  public InputFile getBuildFile() {
    return buildFile;
  }

  /**
   * Returns true if errors were encountered during evaluation of this package.
   * (The package may be incomplete and its contents should not be relied upon
   * for critical operations. However, any Rules belonging to the package are
   * guaranteed to be intact, unless their <code>containsErrors()</code> flag
   * is set.)
   */
  public boolean containsErrors() {
    return containsErrors;
  }

  /**
   * True iff this package contains errors that were caused by temporary conditions (e.g. an I/O
   * error). If this is true, {@link #containsErrors()} also returns true.
   */
  public boolean containsTemporaryErrors() {
    return containsTemporaryErrors;
  }

  public List<Event> getEvents() {
    return events;
  }

  /**
   * Returns an (immutable, unordered) view of all the targets belonging to this package.
   */
  public Collection<Target> getTargets() {
    return getTargets(targets);
  }

  /**
   * Common getTargets implementation, accessible by both {@link Package} and
   * {@link Package.AbstractBuilder}.
   */
  private static Collection<Target> getTargets(Map<String, Target> targetMap) {
    return Collections.unmodifiableCollection(targetMap.values());
  }

  /**
   * Returns a (read-only, unordered) iterator of all the targets belonging
   * to this package which are instances of the specified class.
   */
  public <T extends Target> Iterable<T> getTargets(Class<T> targetClass) {
    return getTargets(targets, targetClass);
  }

  /**
   * Common getTargets implementation, accessible by both {@link Package} and
   * {@link Package.AbstractBuilder}.
   */
  private static <T extends Target> Iterable<T> getTargets(Map<String, Target> targetMap,
      Class<T> targetClass) {
    return Iterables.filter(targetMap.values(), targetClass);
  }

  /**
   * Returns a (read-only, unordered) iterator over the rules in this package.
   */
  @VisibleForTesting // Legacy.  Production code should use getTargets(Class) instead
  Iterable<? extends Rule> getRules() {
    return getTargets(Rule.class);
  }

  /**
   * Returns a (read-only, unordered) iterator over the files in this package.
   */
  @VisibleForTesting // Legacy.  Production code should use getTargets(Class) instead
  Iterable<? extends FileTarget> getFiles() {
    return getTargets(FileTarget.class);
  }

  /**
   * Returns the rule that corresponds to a particular BUILD target name. Useful
   * for walking through the dependency graph of a target.
   * Fails if the target is not a Rule.
   */
  @VisibleForTesting
  Rule getRule(String targetName) {
    return (Rule) targets.get(targetName);
  }

  /**
   * Returns this package's workspace name.
   *
   * <p>Package-private to encourage callers to get their workspace name from a rule, not a
   * package.</p>
   */
  String getWorkspaceName() {
    return workspaceName;
  }

  /**
   * Returns the features specified in the <code>package()</code> declaration.
   */
  public ImmutableSet<String> getFeatures() {
    return features;
  }

  /**
   * Returns the target (a member of this package) whose name is "targetName".
   * First rules are searched, then output files, then input files.  The target
   * name must be valid, as defined by {@code LabelValidator#validateTargetName}.
   *
   * @throws NoSuchTargetException if the specified target was not found.
   */
  public Target getTarget(String targetName) throws NoSuchTargetException {
    Target target = targets.get(targetName);
    if (target != null) {
      return target;
    }

    // No such target.

    // If there's a file on the disk that's not mentioned in the BUILD file,
    // produce a more informative error.  NOTE! this code path is only executed
    // on failure, which is (relatively) very rare.  In the common case no
    // stat(2) is executed.
    Path filename = getPackageDirectory().getRelative(targetName);
    String suffix;
    if (!new PathFragment(targetName).isNormalized()) {
      // Don't check for file existence in this case because the error message
      // would be confusing and wrong. If the targetName is "foo/bar/.", and
      // there is a directory "foo/bar", it doesn't mean that "//pkg:foo/bar/."
      // is a valid label.
      suffix = "";
    } else if (filename.isDirectory()) {
      suffix = "; however, a source directory of this name exists.  (Perhaps add "
          + "'exports_files([\"" + targetName + "\"])' to " + name + "/BUILD, or define a "
          + "filegroup?)";
    } else if (filename.exists()) {
      suffix = "; however, a source file of this name exists.  (Perhaps add "
          + "'exports_files([\"" + targetName + "\"])' to " + name + "/BUILD?)";
    } else {
      suffix = "";
    }

    try {
      throw new NoSuchTargetException(createLabel(targetName), "target '" + targetName
          + "' not declared in package '" + name + "'" + suffix + " defined by "
          + this.filename);
    } catch (Label.SyntaxException e) {
      throw new IllegalArgumentException(targetName);
    }
  }

  /**
   * Creates a label for a target inside this package.
   *
   * @throws SyntaxException if the {@code targetName} is invalid
   */
  public Label createLabel(String targetName) throws SyntaxException {
    return Label.create(packageIdentifier, targetName);
  }

  /**
   * Returns the default visibility for this package.
   */
  public RuleVisibility getDefaultVisibility() {
    if (defaultVisibility != null) {
      return defaultVisibility;
    } else {
      return ConstantRuleVisibility.PRIVATE;
    }
  }

  /**
   * Returns the default obsolete value.
   */
  public Boolean getDefaultObsolete() {
    return defaultObsolete;
  }

  /**
   * Returns the default testonly value.
   */
  public Boolean getDefaultTestOnly() {
    return defaultTestOnly;
  }

  /**
   * Returns the default obsolete value.
   */
  public String getDefaultDeprecation() {
    return defaultDeprecation;
  }

  /**
   * Gets the default header checking mode.
   */
  public String getDefaultHdrsCheck() {
    return defaultHdrsCheck != null ? defaultHdrsCheck : "loose";
  }

  /**
   * Returns the default copts value, to which rules should append their
   * specific copts.
   */
  public ImmutableList<String> getDefaultCopts() {
    return defaultCopts;
  }

  /**
   * Returns whether the default header checking mode has been set or it is the
   * default value.
   */
  public boolean isDefaultHdrsCheckSet() {
    return defaultHdrsCheck != null;
  }

  public boolean isDefaultVisibilitySet() {
    return defaultVisibilitySet;
  }

  /**
   * Gets the parsed license object for the default license
   * declared by this package.
   */
  public License getDefaultLicense() {
    return defaultLicense;
  }

  /**
   * Returns the parsed set of distributions declared as the default for this
   * package.
   */
  public Set<License.DistributionType> getDefaultDistribs() {
    return defaultDistributionSet;
  }

  /**
   * Returns the default value to use for a rule's {@link RuleClass#COMPATIBLE_ENVIRONMENT_ATTR}
   * attribute when not explicitly specified by the rule.
   */
  public Set<Label> getDefaultCompatibleWith() {
    return defaultCompatibleWith;
  }

  /**
   * Returns the default value to use for a rule's {@link RuleClass#RESTRICTED_ENVIRONMENT_ATTR}
   * attribute when not explicitly specified by the rule.
   */
  public Set<Label> getDefaultRestrictedTo() {
    return defaultRestrictedTo;
  }

  @Override
  public String toString() {
    return "Package(" + name + ")=" + (targets != null ? getRules() : "initializing...");
  }

  /**
   * Dumps the package for debugging. Do not depend on the exact format/contents of this debugging
   * output.
   */
  public void dump(PrintStream out) {
    out.println("  Package " + getName() + " (" + getFilename() + ")");

    // Rules:
    out.println("    Rules");
    for (Rule rule : getTargets(Rule.class)) {
      out.println("      " + rule.getTargetKind() + " " + rule.getLabel());
      for (Attribute attr : rule.getAttributes()) {
        for (Object possibleValue : AggregatingAttributeMapper.of(rule)
            .visitAttribute(attr.getName(), attr.getType())) {
          out.println("        " + attr.getName() + " = " + possibleValue);
        }
      }
    }

    // Files:
    out.println("    Files");
    for (FileTarget file : getTargets(FileTarget.class)) {
      out.print("      " + file.getTargetKind() + " " + file.getLabel());
      if (file instanceof OutputFile) {
        out.println(" (generated by " + ((OutputFile) file).getGeneratingRule().getLabel() + ")");
      } else {
        out.println();
      }
    }

    // TODO(bazel-team): (2009) perhaps dump also:
    // - subincludes
    // - globs
    // - containsErrors
    // - makeEnv
  }

  /**
   * Builder class for {@link Package}.
   *
   * <p>Should only be used by the package loading and the package deserialization machineries.
   */
  static class Builder extends AbstractBuilder<Package, Builder> {
    Builder(PackageIdentifier packageId) {
      super(new Package(packageId));
    }

    @Override
    protected Builder self() {
      return this;
    }
  }

  /** Builder class for {@link Package} that does its own globbing. */
  public static class LegacyBuilder extends AbstractBuilder<Package, LegacyBuilder> {

    private Globber globber = null;

    LegacyBuilder(PackageIdentifier packageId) {
      super(AbstractBuilder.newPackage(packageId));
    }

    @Override
    protected LegacyBuilder self() {
      return this;
    }

    /**
     * Sets the globber used for this package's glob expansions.
     */
    LegacyBuilder setGlobber(Globber globber) {
      this.globber = globber;
      return this;
    }

    /**
     * Removes a target from the {@link Package} under construction. Intended to be used only by
     * {@link PackageFunction} to remove targets whose labels cross subpackage boundaries.
     */
    public void removeTarget(Target target) {
      if (target.getPackage() == pkg) {
        this.targets.remove(target.getName());
      }
    }

    /**
     * Returns the glob patterns requested by {@link PackageFactory} during evaluation of this
     * package's BUILD file. Intended to be used only by {@link PackageFunction} to mark the
     * appropriate Skyframe dependencies after the fact.
     */
    public Set<Pair<String, Boolean>> getGlobPatterns() {
      return globber.getGlobPatterns();
    }
  }

  abstract static class AbstractBuilder<P extends Package, B extends AbstractBuilder<P, B>> {
    /**
     * The output instance for this builder. Needs to be instantiated and
     * available with name info throughout initialization. All other settings
     * are applied during {@link #build}. See {@link Package#Package(String)}
     * and {@link Package#finishInit} for details.
     */
    protected P pkg;

    protected Path filename = null;
    private Label buildFileLabel = null;
    private InputFile buildFile = null;
    private MakeEnvironment.Builder makeEnv = null;
    private RuleVisibility defaultVisibility = null;
    private boolean defaultVisibilitySet;
    private List<String> defaultCopts = null;
    private List<String> features = new ArrayList<>();
    private List<Event> events = Lists.newArrayList();
    private boolean containsErrors = false;
    private boolean containsTemporaryErrors = false;

    private License defaultLicense = License.NO_LICENSE;
    private Set<License.DistributionType> defaultDistributionSet = License.DEFAULT_DISTRIB;

    protected Map<String, Target> targets = new HashMap<>();
    protected Map<Label, EnvironmentGroup> environmentGroups = new HashMap<>();

    protected Map<Label, Path> subincludes = null;
    protected ImmutableList<Label> skylarkFileDependencies = ImmutableList.of();

    /**
     * True iff the "package" function has already been called in this package.
     */
    private boolean packageFunctionUsed;

    /**
     * The collection of the prefixes of every output file. Maps every prefix
     * to an output file whose prefix it is.
     *
     * <p>This is needed to make the output file prefix conflict check be
     * reasonably fast. However, since it can potentially take a lot of memory and
     * is useless after the package has been loaded, it isn't passed to the
     * package itself.
     */
    private Map<String, OutputFile> outputFilePrefixes = new HashMap<>();

    private boolean alreadyBuilt = false;

    private EventHandler builderEventHandler = new EventHandler() {
      @Override
      public void handle(Event event) {
        addEvent(event);
      }
    };

    protected AbstractBuilder(P pkg) {
      this.pkg = pkg;
      if (pkg.getName().startsWith("javatests/")) {
        setDefaultTestonly(true);
      }
    }

    protected static Package newPackage(PackageIdentifier packageId) {
      return new Package(packageId);
    }

    protected abstract B self();

    protected PackageIdentifier getPackageIdentifier() {
      return pkg.getPackageIdentifier();
    }

    /**
     * Sets the name of this package's BUILD file.
     */
    B setFilename(Path filename) {
      this.filename = filename;
      try {
        buildFileLabel = createLabel(filename.getBaseName());
        addInputFile(buildFileLabel, Location.fromFile(filename));
      } catch (Label.SyntaxException e) {
        // This can't actually happen.
        throw new AssertionError("Package BUILD file has an illegal name: " + filename);
      }
      return self();
    }

    public Label getBuildFileLabel() {
      return buildFileLabel;
    }

    Path getFilename() {
      return filename;
    }

    /**
     * Sets this package's Make environment.
     */
    B setMakeEnv(MakeEnvironment.Builder makeEnv) {
      this.makeEnv = makeEnv;
      return self();
    }

    /**
     * Sets the default visibility for this package. Called at most once per
     * package from PackageFactory.
     */
    B setDefaultVisibility(RuleVisibility visibility) {
      this.defaultVisibility = visibility;
      this.defaultVisibilitySet = true;
      return self();
    }

    /**
     * Sets whether the default visibility is set in the BUILD file.
     */
    B setDefaultVisibilitySet(boolean defaultVisibilitySet) {
      this.defaultVisibilitySet = defaultVisibilitySet;
      return self();
    }

    /**
     * Sets the default value of 'obsolete'. Rule-level 'obsolete' will override this.
     */
    B setDefaultObsolete(boolean defaultObsolete) {
      pkg.setDefaultObsolete(defaultObsolete);
      return self();
    }

    /** Sets the default value of 'testonly'. Rule-level 'testonly' will override this. */
    B setDefaultTestonly(boolean defaultTestonly) {
      pkg.setDefaultTestOnly(defaultTestonly);
      return self();
    }

    /**
     * Sets the default value of 'deprecation'. Rule-level 'deprecation' will append to this.
     */
    B setDefaultDeprecation(String defaultDeprecation) {
      pkg.setDefaultDeprecation(defaultDeprecation);
      return self();
    }

    /**
     * Uses the workspace name from {@code //external} to set this package's workspace name.
     */
    B setWorkspaceName(String workspaceName) {
      pkg.workspaceName = workspaceName;
      return self();
    }

    /**
     * Returns whether the "package" function has been called yet
     */
    public boolean isPackageFunctionUsed() {
      return packageFunctionUsed;
    }

    public void setPackageFunctionUsed() {
      packageFunctionUsed = true;
    }

    /**
     * Sets the default header checking mode.
     */
    public B setDefaultHdrsCheck(String hdrsCheck) {
      // Note that this setting is propagated directly to the package because
      // other code needs the ability to read this info directly from the
      // under-construction package. See {@link Package#setDefaultHdrsCheck}.
      pkg.setDefaultHdrsCheck(hdrsCheck);
      return self();
    }

    /**
     * Sets the default value of copts. Rule-level copts will append to this.
     */
    public B setDefaultCopts(List<String> defaultCopts) {
      this.defaultCopts = defaultCopts;
      return self();
    }

    public B addFeatures(Iterable<String> features) {
      Iterables.addAll(this.features, features);
      return self();
    }

    /**
     * Declares that errors were encountering while loading this package.
     */
    public B setContainsErrors() {
      containsErrors = true;
      return self();
    }

    public boolean containsErrors() {
      return containsErrors;
    }

    B setContainsTemporaryErrors() {
      setContainsErrors();
      containsTemporaryErrors = true;
      return self();
    }

    public B addEvents(Iterable<Event> events) {
      for (Event event : events) {
        addEvent(event);
      }
      return self();
    }

    public B addEvent(Event event) {
      this.events.add(event);
      return self();
    }

    B setSkylarkFileDependencies(ImmutableList<Label> skylarkFileDependencies) {
      this.skylarkFileDependencies = skylarkFileDependencies;
      return self();
    }

    /**
     * Sets the default license for this package.
     */
    void setDefaultLicense(License license) {
      this.defaultLicense = license;
    }

    License getDefaultLicense() {
      return defaultLicense;
    }

    /**
     * Initializes the default set of distributions for targets in this package.
     *
     * TODO(bazel-team): (2011) consider moving the license & distribs info into Metadata--maybe
     * even in the Build language.
     */
    void setDefaultDistribs(Set<DistributionType> dists) {
      this.defaultDistributionSet = dists;
    }

    Set<DistributionType> getDefaultDistribs() {
      return defaultDistributionSet;
    }

    /**
     * Sets the default value to use for a rule's {@link RuleClass#COMPATIBLE_ENVIRONMENT_ATTR}
     * attribute when not explicitly specified by the rule. Records a package error if
     * any labels are duplicated.
     */
    void setDefaultCompatibleWith(List<Label> environments, String attrName, Location location) {
      if (!checkForDuplicateLabels(environments, "package " + pkg.getName(), attrName, location,
          builderEventHandler)) {
        setContainsErrors();
      }
      pkg.setDefaultCompatibleWith(ImmutableSet.copyOf(environments));
    }

    /**
     * Sets the default value to use for a rule's {@link RuleClass#RESTRICTED_ENVIRONMENT_ATTR}
     * attribute when not explicitly specified by the rule. Records a package error if
     * any labels are duplicated.
     */
    void setDefaultRestrictedTo(List<Label> environments, String attrName, Location location) {
      if (!checkForDuplicateLabels(environments, "package " + pkg.getName(), attrName, location,
          builderEventHandler)) {
        setContainsErrors();
      }

      pkg.setDefaultRestrictedTo(ImmutableSet.copyOf(environments));
    }

    /**
     * Returns a new Rule belonging to this package instance, and uses the given Label.
     *
     * <p>Useful for RuleClass instantiation, where the rule name is checked by trying to create a
     * Label. This label can then be used again here.
     */
    Rule newRuleWithLabel(Label label, RuleClass ruleClass, FuncallExpression ast,
        Location location) {
      return new Rule(pkg, label, ruleClass, ast, location);
    }

    /**
     * Called by the parser when a "mocksubinclude" is encountered, to record the
     * mappings from labels to absolute paths upon which that the validity of
     * this package depends.
     */
    void addSubinclude(Label label, Path resolvedPath) {
      if (subincludes == null) {
        // This is a TreeMap because the order needs to be deterministic.
        subincludes = Maps.newTreeMap();
      }

      Path oldResolvedPath = subincludes.put(label, resolvedPath);
      if (oldResolvedPath != null && !oldResolvedPath.equals(resolvedPath)){
        // The same label should have been resolved to the same path
        throw new IllegalStateException("Ambiguous subinclude path");
      }
    }

    public Set<Label> getSubincludeLabels() {
      return subincludes == null ? Sets.<Label>newHashSet() : subincludes.keySet();
    }

    public Map<Label, Path> getSubincludes() {
      return subincludes == null ? Maps.<Label, Path>newHashMap() : subincludes;
    }

    public Collection<Target> getTargets() {
      return Package.getTargets(targets);
    }

    /**
     * Returns an (immutable, unordered) view of all the targets belonging to
     * this package which are instances of the specified class.
     */
    <T extends Target> Iterable<T> getTargets(Class<T> targetClass) {
      return Package.getTargets(targets, targetClass);
    }

    /**
     * An input file name conflicts with an existing package member.
     */
    static class GeneratedLabelConflict extends NameConflictException {
      private GeneratedLabelConflict(String message) {
        super(message);
      }
    }

    /**
     * Creates an input file target in this package with the specified name.
     *
     * @param targetName name of the input file.  This must be a valid target
     *   name as defined by {@link
     *   com.google.devtools.build.lib.cmdline.LabelValidator#validateTargetName}.
     * @return the newly-created InputFile, or the old one if it already existed.
     * @throws GeneratedLabelConflict if the name was already taken by a Rule or
     *     an OutputFile target.
     * @throws IllegalArgumentException if the name is not a valid label
     */
    InputFile createInputFile(String targetName, Location location)
        throws GeneratedLabelConflict {
      Target existing = targets.get(targetName);
      if (existing == null) {
        try {
          return addInputFile(createLabel(targetName), location);
        } catch (Label.SyntaxException e) {
          throw new IllegalArgumentException("FileTarget in package " + pkg.getName()
                                             + " has illegal name: " + targetName);
        }
      } else if (existing instanceof InputFile) {
        return (InputFile) existing; // idempotent
      } else {
        throw new GeneratedLabelConflict("generated label '//" + pkg.getName() + ":"
            + targetName + "' conflicts with existing "
            + existing.getTargetKind());
      }
    }

    /**
     * Sets the visibility and license for an input file. The input file must already exist as
     * a member of this package.
     * @throws IllegalArgumentException if the input file doesn't exist in this
     *     package's target map.
     */
    void setVisibilityAndLicense(InputFile inputFile, RuleVisibility visibility, License license) {
      String filename = inputFile.getName();
      Target cacheInstance = targets.get(filename);
      if (cacheInstance == null || !(cacheInstance instanceof InputFile)) {
        throw new IllegalArgumentException("Can't set visibility for nonexistent FileTarget "
                                           + filename + " in package " + pkg.getName() + ".");
      }
      if (!((InputFile) cacheInstance).isVisibilitySpecified()
          || cacheInstance.getVisibility() != visibility
          || cacheInstance.getLicense() != license) {
        targets.put(filename, new InputFile(
            pkg, cacheInstance.getLabel(), cacheInstance.getLocation(), visibility, license));
      }
    }

    /**
     * Creates a label for a target inside this package.
     *
     * @throws SyntaxException if the {@code targetName} is invalid
     */
    Label createLabel(String targetName) throws SyntaxException {
      return Label.create(pkg.getPackageIdentifier(), targetName);
    }

    /**
     * Adds a package group to the package.
     */
    void addPackageGroup(String name, Collection<String> packages, Collection<Label> includes,
        EventHandler eventHandler, Location location)
        throws NameConflictException, Label.SyntaxException {
      PackageGroup group =
          new PackageGroup(createLabel(name), pkg, packages, includes, eventHandler, location);
      Target existing = targets.get(group.getName());
      if (existing != null) {
        throw nameConflict(group, existing);
      }

      targets.put(group.getName(), group);

      if (group.containsErrors()) {
        setContainsErrors();
      }
    }

    /**
     * Checks if any labels in the given list appear multiple times and reports an appropriate
     * error message if so. Returns true if no duplicates were found, false otherwise.
     *
     * TODO(bazel-team): apply this to all build functions (maybe automatically?), possibly
     * integrate with RuleClass.checkForDuplicateLabels.
     */
    private static boolean checkForDuplicateLabels(Collection<Label> labels, String owner,
        String attrName, Location location, EventHandler eventHandler) {
      Set<Label> dupes = CollectionUtils.duplicatedElementsOf(labels);
      for (Label dupe : dupes) {
        eventHandler.handle(Event.error(location, String.format(
            "label '%s' is duplicated in the '%s' list of '%s'", dupe, attrName, owner)));
      }
      return dupes.isEmpty();
    }

    /**
     * Adds an environment group to the package.
     */
    void addEnvironmentGroup(String name, List<Label> environments, List<Label> defaults,
        EventHandler eventHandler, Location location)
        throws NameConflictException, SyntaxException {

      if (!checkForDuplicateLabels(environments, name, "environments", location, eventHandler)
          || !checkForDuplicateLabels(defaults, name, "defaults", location, eventHandler)) {
        setContainsErrors();
        return;
      }

      EnvironmentGroup group = new EnvironmentGroup(createLabel(name), pkg, environments,
          defaults, location);
      Target existing = targets.get(group.getName());
      if (existing != null) {
        throw nameConflict(group, existing);
      }

      targets.put(group.getName(), group);
      Collection<Event> membershipErrors = group.validateMembership();
      if (!membershipErrors.isEmpty()) {
        for (Event error : membershipErrors) {
          eventHandler.handle(error);
        }
        setContainsErrors();
        return;
      }

      // For each declared environment, make sure it doesn't also belong to some other group.
      for (Label environment : group.getEnvironments()) {
        EnvironmentGroup otherGroup = environmentGroups.get(environment);
        if (otherGroup != null) {
          eventHandler.handle(Event.error(location, "environment " + environment + " belongs to"
              + " both " + group.getLabel() + " and " + otherGroup.getLabel()));
          setContainsErrors();
        } else {
          environmentGroups.put(environment, group);
        }
      }
    }

    void addRule(Rule rule) throws NameConflictException {
      checkForConflicts(rule);
      // Now, modify the package:
      for (OutputFile outputFile : rule.getOutputFiles()) {
        targets.put(outputFile.getName(), outputFile);
        PathFragment outputFileFragment = new PathFragment(outputFile.getName());
        for (int i = 1; i < outputFileFragment.segmentCount(); i++) {
          String prefix = outputFileFragment.subFragment(0, i).toString();
          if (!outputFilePrefixes.containsKey(prefix)) {
            outputFilePrefixes.put(prefix, outputFile);
          }
        }
      }
      targets.put(rule.getName(), rule);
      if (rule.containsErrors()) {
        this.setContainsErrors();
      }
    }

    private B beforeBuild() {
      Preconditions.checkNotNull(pkg);
      Preconditions.checkNotNull(filename);
      Preconditions.checkNotNull(buildFileLabel);
      Preconditions.checkNotNull(makeEnv);
      // Freeze subincludes.
      subincludes = (subincludes == null)
          ? Collections.<Label, Path>emptyMap()
          : Collections.unmodifiableMap(subincludes);

      // We create the original BUILD InputFile when the package filename is set; however, the
      // visibility may be overridden with an exports_files directive, so we need to obtain the
      // current instance here.
      buildFile = (InputFile) Preconditions.checkNotNull(targets.get(buildFileLabel.getName()));

      List<Rule> rules = Lists.newArrayList(getTargets(Rule.class));

      // All labels mentioned in a rule that refer to an unknown target in the
      // current package are assumed to be InputFiles, so let's create them:
      for (final Rule rule : rules) {
        AggregatingAttributeMapper.of(rule).visitLabels(new AcceptsLabelAttribute() {
          @Override
          public void acceptLabelAttribute(Label label, Attribute attribute) {
            createInputFileMaybe(label, rule.getAttributeLocation(attribute.getName()));
          }
        });
      }

      // "test_suite" rules have the idiosyncratic semantics of implicitly
      // depending on all tests in the package, iff tests=[] and suites=[].
      // Note, we implement this here when the Package is fully constructed,
      // since clearly this information isn't available at Rule construction
      // time, as forward references are permitted.
      List<Label> allTests = new ArrayList<>();
      for (Rule rule : rules) {
        if (TargetUtils.isTestRule(rule) && !TargetUtils.hasManualTag(rule)
            && !TargetUtils.isObsolete(rule)) {
          allTests.add(rule.getLabel());
        }
      }
      for (Rule rule : rules) {
        AttributeMap attributes = NonconfigurableAttributeMapper.of(rule);
        if (rule.getRuleClass().equals("test_suite")
            && attributes.get("tests", Type.LABEL_LIST).isEmpty()
            && attributes.get("suites", Type.LABEL_LIST).isEmpty()) {
          rule.setAttributeValueByName("$implicit_tests", allTests);
        }
      }
      return self();
    }

    /** Intended to be used only by {@link PackageFunction}. */
    public B buildPartial() {
      if (alreadyBuilt) {
        return self();
      }
      return beforeBuild();
    }

    /** Intended to be used only by {@link PackageFunction}. */
    public P finishBuild() {
      if (alreadyBuilt) {
        return pkg;
      }
      // Freeze targets and distributions.
      targets = ImmutableMap.copyOf(targets);
      defaultDistributionSet =
          Collections.unmodifiableSet(defaultDistributionSet);

      // Now all targets have been loaded, so we can check all declared environments in an
      // environment group exist.
      for (EnvironmentGroup envGroup : ImmutableSet.copyOf(environmentGroups.values())) {
        Collection<Event> errors = envGroup.checkEnvironmentsExist(targets);
        if (!errors.isEmpty()) {
          addEvents(errors);
          setContainsErrors();
        }
      }

      // Build the package.
      pkg.finishInit(this);
      alreadyBuilt = true;
      return pkg;
    }

    public P build() {
      if (alreadyBuilt) {
        return pkg;
      }
      beforeBuild();
      return finishBuild();
    }

    /**
     * If "label" refers to a non-existent target in the current package, create
     * an InputFile target.
     */
    void createInputFileMaybe(Label label, Location location) {
      if (label != null && label.getPackageFragment().equals(pkg.getNameFragment())) {
        if (!targets.containsKey(label.getName())) {
          addInputFile(label, location);
        }
      }
    }

    private InputFile addInputFile(Label label, Location location) {
      InputFile inputFile = new InputFile(pkg, label, location);
      Target prev = targets.put(label.getName(), inputFile);
      Preconditions.checkState(prev == null);
      return inputFile;
    }

    /**
     * Precondition check for addRule.  We must maintain these invariants of the
     * package:
     * - Each name refers to at most one target.
     * - No rule with errors is inserted into the package.
     * - The generating rule of every output file in the package must itself be
     *   in the package.
     */
    private void checkForConflicts(Rule rule) throws NameConflictException {
      String name = rule.getName();
      Target existing = targets.get(name);
      if (existing != null) {
        throw nameConflict(rule, existing);
      }
      Map<String, OutputFile> outputFiles = new HashMap<>();

      for (OutputFile outputFile : rule.getOutputFiles()) {
        String outputFileName = outputFile.getName();
        if (outputFiles.put(outputFileName, outputFile) != null) { // dups within a single rule:
          throw duplicateOutputFile(outputFile, outputFile);
        }
        existing = targets.get(outputFileName);
        if (existing != null) {
          throw duplicateOutputFile(outputFile, existing);
        }

        // Check if this output file is the prefix of an already existing one
        if (outputFilePrefixes.containsKey(outputFileName)) {
          throw conflictingOutputFile(outputFile, outputFilePrefixes.get(outputFileName));
        }

        // Check if a prefix of this output file matches an already existing one
        PathFragment outputFileFragment = new PathFragment(outputFileName);
        for (int i = 1; i < outputFileFragment.segmentCount(); i++) {
          String prefix = outputFileFragment.subFragment(0, i).toString();
          if (outputFiles.containsKey(prefix)) {
            throw conflictingOutputFile(outputFile, outputFiles.get(prefix));
          }
          if (targets.containsKey(prefix)
              && targets.get(prefix) instanceof OutputFile) {
            throw conflictingOutputFile(outputFile, (OutputFile) targets.get(prefix));
          }

          if (!outputFilePrefixes.containsKey(prefix)) {
            outputFilePrefixes.put(prefix, outputFile);
          }
        }
      }

      checkForInputOutputConflicts(rule, outputFiles.keySet());
    }

    /**
     * A utility method that checks for conflicts between
     * input file names and output file names for a rule from a build
     * file.
     * @param rule the rule whose inputs and outputs are
     *       to be checked for conflicts.
     * @param outputFiles a set containing the names of output
     *       files to be generated by the rule.
     * @throws NameConflictException if a conflict is found.
     */
    private void checkForInputOutputConflicts(Rule rule, Set<String> outputFiles)
        throws NameConflictException {
      PathFragment packageFragment = rule.getLabel().getPackageFragment();
      for (Label inputLabel : rule.getLabels()) {
        if (packageFragment.equals(inputLabel.getPackageFragment())
            && outputFiles.contains(inputLabel.getName())) {
          throw inputOutputNameConflict(rule, inputLabel.getName());
        }
      }
    }

    /** An output file conflicts with another output file or the BUILD file. */
    private NameConflictException duplicateOutputFile(OutputFile duplicate, Target existing) {
      return new NameConflictException(duplicate.getTargetKind() + " '" + duplicate.getName()
          + "' in rule '" + duplicate.getGeneratingRule().getName() + "' "
          + conflictsWith(existing));
    }

    /** The package contains two targets with the same name. */
    private NameConflictException nameConflict(Target duplicate, Target existing) {
      return new NameConflictException(duplicate.getTargetKind() + " '" + duplicate.getName()
          + "' in package '" + duplicate.getLabel().getPackageName() + "' "
          + conflictsWith(existing));
    }

    /** A a rule has a input/output name conflict. */
    private NameConflictException inputOutputNameConflict(Rule rule, String conflictingName) {
      return new NameConflictException("rule '" + rule.getName() + "' has file '"
          + conflictingName + "' as both an input and an output");
    }

    private static NameConflictException conflictingOutputFile(
        OutputFile added, OutputFile existing) {
      if (added.getGeneratingRule() == existing.getGeneratingRule()) {
        return new NameConflictException(String.format(
            "rule '%s' has conflicting output files '%s' and '%s'", added.getGeneratingRule()
                .getName(), added.getName(), existing.getName()));
      } else {
        return new NameConflictException(String.format(
            "output file '%s' of rule '%s' conflicts with output file '%s' of rule '%s'", added
                .getName(), added.getGeneratingRule().getName(), existing.getName(), existing
                .getGeneratingRule().getName()));
      }
    }

    /**
     * Utility function for generating exception messages.
     */
    private static String conflictsWith(Target target) {
      String message = "conflicts with existing ";
      if (target instanceof OutputFile) {
        return message + "generated file from rule '"
          + ((OutputFile) target).getGeneratingRule().getName()
          + "'";
      } else {
        return message + target.getTargetKind();
      }
    }
  }
}
