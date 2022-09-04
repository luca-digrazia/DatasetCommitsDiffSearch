// Copyright 2014 The Bazel Authors. All rights reserved.
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
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.cmdline.LabelSyntaxException;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.concurrent.ThreadSafety.ThreadSafe;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec.VisibleForSerialization;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Support for license and distribution checking. */
@Immutable
@ThreadSafe
@AutoCodec
public final class License {
  private final ImmutableSet<LicenseType> licenseTypes;
  private final ImmutableSet<Label> exceptions;

  /**
   * The error that's thrown if a build file contains an invalid license string.
   */
  public static class LicenseParsingException extends Exception {
    public LicenseParsingException(String s) {
      super(s);
    }
  }

  /**
   * LicenseType is the basis of the License lattice - stricter licenses should
   * be declared before less-strict licenses in the enum.
   *
   * <p>Note that the order is important for the purposes of finding the least
   * restrictive license.
   */
  public enum LicenseType {
    BY_EXCEPTION_ONLY,
    RESTRICTED,
    RESTRICTED_IF_STATICALLY_LINKED,
    RECIPROCAL,
    NOTICE,
    PERMISSIVE,
    UNENCUMBERED,
    NONE
  }

  /**
   * Gets the least restrictive license type from the list of licenses declared
   * for a target. For the purposes of license checking, the license type set of
   * a declared license can be reduced to its least restrictive member.
   *
   * @param types a collection of license types
   * @return the least restrictive license type
   */
  @VisibleForTesting
  static LicenseType leastRestrictive(Collection<LicenseType> types) {
    return types.isEmpty() ? LicenseType.BY_EXCEPTION_ONLY : Collections.max(types);
  }

  /**
   * An instance of LicenseType.None with no exceptions, used for packages outside of third_party
   * which have no license clause in their BUILD files.
   */
  public static final License NO_LICENSE =
      new License(ImmutableSet.of(LicenseType.NONE), ImmutableSet.of());

  /**
   * A default instance of Distributions which is used for packages which
   * have no "distribs" declaration. If nothing is declared, we opt for the
   * most permissive kind of distribution, which is the internal-only distrib.
   */
  public static final Set<DistributionType> DEFAULT_DISTRIB =
      Collections.singleton(DistributionType.INTERNAL);

  /**
   * The types of distribution that are supported.
   */
  public enum DistributionType {
    INTERNAL,
    WEB,
    CLIENT,
    EMBEDDED
  }

  /**
   * Parses a set of strings declaring distribution types.
   *
   * @param distStrings strings containing distribution declarations from BUILD
   *        files
   * @return a new, unmodifiable set of DistributionTypes
   * @throws LicenseParsingException
   */
  public static Set<DistributionType> parseDistributions(Collection<String> distStrings)
      throws LicenseParsingException {
    if (distStrings.isEmpty()) {
      return Collections.unmodifiableSet(EnumSet.of(DistributionType.INTERNAL));
    } else {
      Set<DistributionType> result = EnumSet.noneOf(DistributionType.class);
      for (String distStr : distStrings) {
        try {
          DistributionType dist = DistributionType.valueOf(distStr.toUpperCase(Locale.ENGLISH));
          result.add(dist);
        } catch (IllegalArgumentException e) {
          throw new LicenseParsingException("Invalid distribution type '" + distStr + "'");
        }
      }
      return Collections.unmodifiableSet(result);
    }
  }

  private static final Object MARKER = new Object();

  /**
   * The license incompatibility set. This contains the set of
   * (Distribution,License) pairs that should generate errors.
   */
  private static Table<DistributionType, LicenseType, Object> LICENSE_INCOMPATIBILIES =
      createLicenseIncompatibilitySet();

  private static Table<DistributionType, LicenseType, Object> createLicenseIncompatibilitySet() {
    Table<DistributionType, LicenseType, Object> result = HashBasedTable.create();
    result.put(DistributionType.CLIENT, LicenseType.RESTRICTED, MARKER);
    result.put(DistributionType.EMBEDDED, LicenseType.RESTRICTED, MARKER);
    result.put(DistributionType.INTERNAL, LicenseType.BY_EXCEPTION_ONLY, MARKER);
    result.put(DistributionType.CLIENT, LicenseType.BY_EXCEPTION_ONLY, MARKER);
    result.put(DistributionType.WEB, LicenseType.BY_EXCEPTION_ONLY, MARKER);
    result.put(DistributionType.EMBEDDED, LicenseType.BY_EXCEPTION_ONLY, MARKER);
    return ImmutableTable.copyOf(result);
  }

  /**
   * The license warning set. This contains the set of
   * (Distribution,License) pairs that should generate warnings when the user
   * requests verbose license checking.
   */
  private static Table<DistributionType, LicenseType, Object> LICENSE_WARNINGS =
      createLicenseWarningsSet();

  private static Table<DistributionType, LicenseType, Object> createLicenseWarningsSet() {
    Table<DistributionType, LicenseType, Object> result = HashBasedTable.create();
    result.put(DistributionType.CLIENT, LicenseType.RECIPROCAL, MARKER);
    result.put(DistributionType.EMBEDDED, LicenseType.RECIPROCAL, MARKER);
    result.put(DistributionType.CLIENT, LicenseType.NOTICE, MARKER);
    result.put(DistributionType.EMBEDDED, LicenseType.NOTICE, MARKER);
    return ImmutableTable.copyOf(result);
  }

  @AutoCodec.Instantiator
  @VisibleForSerialization
  License(ImmutableSet<LicenseType> licenseTypes, ImmutableSet<Label> exceptions) {
    // Defensive copy is done in .of()
    this.licenseTypes = licenseTypes;
    this.exceptions = exceptions;
  }

  public static License of(Collection<LicenseType> licenses, Collection<Label> exceptions) {
    ImmutableSet<LicenseType> licenseSet = ImmutableSet.copyOf(licenses);
    ImmutableSet<Label> exceptionSet = ImmutableSet.copyOf(exceptions);

    if (exceptionSet.isEmpty() && licenseSet.equals(ImmutableSet.of(LicenseType.NONE))) {
      return License.NO_LICENSE;
    }

    return new License(licenseSet, exceptionSet);
  }
  /**
   * Computes a license which can be used to check if a package is compatible
   * with some kinds of distribution. The list of licenses is scanned for the
   * least restrictive, and the exceptions are added.
   *
   * @param licStrings the list of license strings declared for the package
   * @throws LicenseParsingException if there are any parsing problems
   */
  public static License parseLicense(List<String> licStrings) throws LicenseParsingException {
    /*
     * The semantics of comparison for licenses depends on a stable iteration
     * order for both license types and exceptions. For licenseTypes, it will be
     * the comparison order from the enumerated types; for exceptions, it will
     * be lexicographic order achieved using TreeSets.
     */
    Set<LicenseType> licenseTypes = EnumSet.noneOf(LicenseType.class);
    Set<Label> exceptions = Sets.newTreeSet();
    for (String str : licStrings) {
      if (str.startsWith("exception=")) {
        try {
          Label label = Label.parseAbsolute(str.substring("exception=".length()));
          exceptions.add(label);
        } catch (LabelSyntaxException e) {
          throw new LicenseParsingException(e.getMessage());
        }
      } else {
        try {
          licenseTypes.add(LicenseType.valueOf(str.toUpperCase(Locale.ENGLISH)));
        } catch (IllegalArgumentException e) {
          throw new LicenseParsingException("invalid license type: '" + str + "'");
        }
      }
    }

    return License.of(licenseTypes, exceptions);
  }

  /**
   * Checks if this license is compatible with distributing a particular target
   * in some set of distribution modes.
   *
   * @param dists the modes of distribution
   * @param target the target which is being checked, and which will be used for
   *        checking exceptions
   * @param licensedTarget the target which declared the license being checked.
   * @param eventHandler a reporter where any licensing issues discovered should be
   *        reported
   * @param staticallyLinked whether the target is statically linked under this command
   * @return true if the license is compatible with the distributions
   */
  public boolean checkCompatibility(Set<DistributionType> dists,
      Target target, Label licensedTarget, EventHandler eventHandler,
      boolean staticallyLinked) {
    Location location = (target instanceof Rule) ? ((Rule) target).getLocation() : null;

    LicenseType leastRestrictiveLicense;
    if (licenseTypes.contains(LicenseType.RESTRICTED_IF_STATICALLY_LINKED)) {
      Set<LicenseType> tempLicenses = EnumSet.copyOf(licenseTypes);
      tempLicenses.remove(LicenseType.RESTRICTED_IF_STATICALLY_LINKED);
      if (staticallyLinked) {
        tempLicenses.add(LicenseType.RESTRICTED);
      } else {
        tempLicenses.add(LicenseType.UNENCUMBERED);
      }
      leastRestrictiveLicense = leastRestrictive(tempLicenses);
    } else {
      leastRestrictiveLicense = leastRestrictive(licenseTypes);
    }
    for (DistributionType dt : dists) {
      if (LICENSE_INCOMPATIBILIES.contains(dt, leastRestrictiveLicense)) {
        if (!exceptions.contains(target.getLabel())) {
          eventHandler.handle(Event.error(location, "Build target '" + target.getLabel()
              + "' is not compatible with license '" + this + "' from target '"
                  + licensedTarget + "'"));
          return false;
        }
      } else if (LICENSE_WARNINGS.contains(dt, leastRestrictiveLicense)) {
        eventHandler.handle(
            Event.warn(location, "Build target '" + target
                + "' has a potential licensing issue "
                + "with a '" + this + "' license from target '" + licensedTarget + "'"));
      }
    }
    return true;
  }

  /**
   * @return an immutable set of {@link LicenseType}s contained in this {@code
   *         License}
   */
  public Set<LicenseType> getLicenseTypes() {
    return licenseTypes;
  }

  /**
   * @return an immutable set of {@link Label}s that describe exceptions to the
   *         {@code License}
   */
  public Set<Label> getExceptions() {
    return exceptions;
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isSpecified() {
    return this != License.NO_LICENSE;
  }

  /**
   * A simple toString implementation which generates a canonical form of the
   * license. (The order of license types is guaranteed to be canonical by
   * EnumSet, and the order of exceptions is guaranteed to be lexicographic
   * order by TreeSet.)
   */
  @Override
  public String toString() {
    if (exceptions.isEmpty()) {
      return licenseTypes.toString().toLowerCase();
    } else {
      return licenseTypes.toString().toLowerCase() + " with exceptions " + exceptions;
    }
  }

  /**
   * A simple equals implementation leveraging the support built into Set that
   * delegates to its contents.
   */
  @Override
  public boolean equals(Object o) {
    return o == this || (o instanceof License
        && ((License) o).licenseTypes.equals(this.licenseTypes)
        && ((License) o).exceptions.equals(this.exceptions));
  }

  /**
   * A simple hashCode implementation leveraging the support built into Set that
   * delegates to its contents.
   */
  @Override
  public int hashCode() {
    return licenseTypes.hashCode() * 43 + exceptions.hashCode();
  }
}
