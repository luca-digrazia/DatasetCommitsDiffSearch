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
package com.google.devtools.build.lib.skyframe;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.cmdline.ResolvedTargets;
import com.google.devtools.build.lib.cmdline.ResolvedTargets.Builder;
import com.google.devtools.build.lib.cmdline.TargetParsingException;
import com.google.devtools.build.lib.cmdline.TargetPattern;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.concurrent.ThreadSafety.ThreadSafe;
import com.google.devtools.build.lib.pkgcache.FilteringPolicy;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.lib.syntax.Label.SyntaxException;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A value referring to a computed set of resolved targets. This is used for the results of target
 * pattern parsing.
 */
@Immutable
@ThreadSafe
public final class TargetPatternValue implements SkyValue {

  private ResolvedTargets<Label> targets;

  TargetPatternValue(ResolvedTargets<Label> targets) {
    this.targets = Preconditions.checkNotNull(targets);
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    List<String> ts = new ArrayList<>();
    List<String> filteredTs = new ArrayList<>();
    for (Label target : targets.getTargets()) {
      ts.add(target.toString());
    }
    for (Label target : targets.getFilteredTargets()) {
      filteredTs.add(target.toString());
    }

    out.writeObject(ts);
    out.writeObject(filteredTs);
  }

  private Label labelFromString(String labelString) {
    try {
      return Label.parseAbsolute(labelString);
    } catch (SyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    List<String> ts = (List<String>) in.readObject();
    List<String> filteredTs = (List<String>) in.readObject();

    Builder<Label> builder = ResolvedTargets.<Label>builder();
    for (String labelString : ts) {
      builder.add(labelFromString(labelString));
    }

    for (String labelString : filteredTs) {
      builder.remove(labelFromString(labelString));
    }
    this.targets = builder.build();
  }

  @SuppressWarnings("unused")
  private void readObjectNoData() {
    throw new IllegalStateException();
  }

  /**
   * Create a target pattern {@link SkyKey}. Throws {@link TargetParsingException} if the provided
   * {@code pattern} cannot be parsed.
   *
   * @param pattern The pattern, eg "-foo/biz...". If the first character is "-", the pattern is
   *     treated as a negative pattern.
   * @param policy The filtering policy, eg "only return test targets"
   * @param offset The offset to apply to relative target patterns.
   */
  @ThreadSafe
  public static SkyKey key(String pattern, FilteringPolicy policy, String offset)
      throws TargetParsingException {
    return Iterables.getOnlyElement(keys(ImmutableList.of(pattern), policy, offset)).getSkyKey();
  }

  /**
   * Returns an iterable of {@link TargetPatternSkyKeyOrException}, with {@link TargetPatternKey}
   * arguments. If a provided pattern fails to parse, an element in the returned iterable will
   * throw when its {@link TargetPatternSkyKeyOrException#getSkyKey} method is called and will
   * return the failing pattern when its {@link
   * TargetPatternSkyKeyOrException#getOriginalPattern} method is called.
   *
   * <p>There may be fewer returned elements than patterns provided as input. This function may
   * combine patterns to return an iterable of SkyKeys that is equivalent but more efficient to
   * evaluate.
   *
   * @param patterns The list of patterns, eg "-foo/biz...". If a pattern's first character is "-",
   *     it is treated as a negative pattern.
   * @param policy The filtering policy, eg "only return test targets"
   * @param offset The offset to apply to relative target patterns.
   */
  @ThreadSafe
  public static Iterable<TargetPatternSkyKeyOrException> keys(List<String> patterns,
      FilteringPolicy policy, String offset) {
    TargetPattern.Parser parser = new TargetPattern.Parser(offset);
    AggregatedPatterns aggregatedPatterns = new AggregatedPatterns(policy, offset);
    ImmutableList.Builder<TargetPatternSkyKeyOrException> builder = ImmutableList.builder();
    for (String pattern : patterns) {
      boolean positive = !pattern.startsWith("-");
      String absoluteValueOfPattern = positive ? pattern : pattern.substring(1);
      try {
        aggregatedPatterns.addPattern(
            new SignedPattern(positive, parser.parse(absoluteValueOfPattern)));
      } catch (TargetParsingException e) {
        builder.add(new TargetPatternSkyKeyException(e, absoluteValueOfPattern));
      }
    }

    for (TargetPatternKey patternKey : aggregatedPatterns.build()) {
      builder.add(
          new TargetPatternSkyKeyValue(new SkyKey(SkyFunctions.TARGET_PATTERN, patternKey)));
    }

    return builder.build();
  }

  public ResolvedTargets<Label> getTargets() {
    return targets;
  }

  /**
   * A TargetPatternKey is a tuple of pattern (eg, "foo/..."), filtering policy, a relative pattern
   * offset, whether it is a positive or negative match, and a set of excluded subdirectories.
   */
  @ThreadSafe
  public static class TargetPatternKey implements Serializable {

    private final TargetPattern parsedPattern;
    private final FilteringPolicy policy;
    private final boolean isNegative;

    private final String offset;
    private final ImmutableSet<String> excludedSubdirectories;

    public TargetPatternKey(TargetPattern parsedPattern, FilteringPolicy policy,
        boolean isNegative, String offset, ImmutableSet<String> excludedSubdirectories) {
      this.parsedPattern = Preconditions.checkNotNull(parsedPattern);
      this.policy = Preconditions.checkNotNull(policy);
      this.isNegative = isNegative;
      this.offset = offset;
      this.excludedSubdirectories = Preconditions.checkNotNull(excludedSubdirectories);
    }

    public String getPattern() {
      return parsedPattern.getOriginalPattern();
    }

    public TargetPattern getParsedPattern() {
      return parsedPattern;
    }

    public boolean isNegative() {
      return isNegative;
    }

    public FilteringPolicy getPolicy() {
      return policy;
    }

    public String getOffset() {
      return offset;
    }

    public ImmutableSet<String> getExcludedSubdirectories() {
      return excludedSubdirectories;
    }

    @Override
    public String toString() {
      return (isNegative ? "-" : "") + parsedPattern.getOriginalPattern();
    }

    @Override
    public int hashCode() {
      return Objects.hash(parsedPattern, isNegative, policy, offset,
          excludedSubdirectories);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof TargetPatternKey)) {
        return false;
      }
      TargetPatternKey other = (TargetPatternKey) obj;

      return other.isNegative == this.isNegative && other.parsedPattern.equals(this.parsedPattern)
          && other.offset.equals(this.offset) && other.policy.equals(this.policy)
          && other.excludedSubdirectories.equals(this.excludedSubdirectories);
    }
  }

  /**
   * Wrapper for a target pattern {@link SkyKey} or the {@link TargetParsingException} thrown when
   * trying to compute it.
   */
  public interface TargetPatternSkyKeyOrException {

    /**
     * Returns the stored {@link SkyKey} or throws {@link TargetParsingException} if one was thrown
     * when computing the key.
     */
    SkyKey getSkyKey() throws TargetParsingException;

    /**
     * Returns the pattern that resulted in the stored {@link SkyKey} or {@link
     * TargetParsingException}.
     */
    String getOriginalPattern();
  }

  private static final class TargetPatternSkyKeyValue implements TargetPatternSkyKeyOrException {

    private final SkyKey value;

    private TargetPatternSkyKeyValue(SkyKey value) {
      this.value = value;
    }

    @Override
    public SkyKey getSkyKey() throws TargetParsingException {
      return value;
    }

    @Override
    public String getOriginalPattern() {
      return ((TargetPatternKey) value.argument()).getPattern();
    }
  }

  private static final class TargetPatternSkyKeyException implements
      TargetPatternSkyKeyOrException {

    private final TargetParsingException exception;
    private final String originalPattern;

    private TargetPatternSkyKeyException(TargetParsingException exception, String originalPattern) {
      this.exception = exception;
      this.originalPattern = originalPattern;
    }

    @Override
    public SkyKey getSkyKey() throws TargetParsingException {
      throw exception;
    }

    @Override
    public String getOriginalPattern() {
      return originalPattern;
    }
  }
}
