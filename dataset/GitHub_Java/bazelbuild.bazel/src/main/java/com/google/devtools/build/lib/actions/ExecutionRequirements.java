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

package com.google.devtools.build.lib.actions;

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.devtools.build.lib.util.Preconditions;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strings used to express requirements on action execution environments.
 */
public class ExecutionRequirements {

  /** An execution requirement that can be split into a key and a value part using a regex. */
  @AutoValue
  public abstract static class ParseableRequirement {

    /**
     * Thrown when a {@link ParseableRequirement} feels responsible for a tag, but the {@link
     * #validator()} method returns an error.
     */
    public static class ValidationException extends Exception {
      private final String tagValue;

      /**
       * Creates a new {@link ValidationException}.
       *
       * @param tagValue the erroneous value that was parsed from the tag.
       * @param errorMsg an error message that tells the user what's wrong with the value.
       */
      public ValidationException(String tagValue, String errorMsg) {
        super(errorMsg);
        this.tagValue = tagValue;
      }

      /**
       * Returns the erroneous value of the parsed tag.
       *
       * <p>Useful to put in error messages shown to the user.
       */
      public String getTagValue() {
        return tagValue;
      }
    }

    /**
     * Create a new parseable execution requirement definition.
     *
     * <p>If a tag doesn't match the detectionPattern, it will be ignored. If a tag matches the
     * detectionPattern, but not the validationPattern, it is assumed that the value is somehow
     * wrong (e.g. the user put a float or random string where we expected an integer).
     *
     * @param userFriendlyName a human readable name of the tag and its format, e.g. "cpu:<int>"
     * @param detectionPattern a regex that will be used to detect whether a tag matches this
     *     execution requirement. It should have one capture group that grabs the value of the tag.
     *     This should be general enough to permit even wrong value types. Example: "cpu:(.+)".
     * @param validator a Function that will be used to validate the value of the tag. It should
     *     return null if the value is fine to use or a human-friendly error message describing why
     *     the value is not valid.
     */
    static ParseableRequirement create(
        String userFriendlyName, Pattern detectionPattern, Function<String, String> validator) {
      return new AutoValue_ExecutionRequirements_ParseableRequirement(
          userFriendlyName, detectionPattern, validator);
    }

    public abstract String userFriendlyName();

    public abstract Pattern detectionPattern();

    public abstract Function<String, String> validator();

    /**
     * Returns the parsed value from a tag, if this {@link ParseableRequirement} detects that it is
     * responsible for it, otherwise returns {@code null}.
     *
     * @throws ValidationException if the value parsed out of the tag doesn't pass the validator.
     */
    public String parseIfMatches(String tag) throws ValidationException {
      Matcher matcher = detectionPattern().matcher(tag);
      if (!matcher.matches()) {
        return null;
      }
      String tagValue = matcher.group(1);
      String errorMsg = validator().apply(tagValue);
      if (errorMsg != null) {
        throw new ValidationException(tagValue, errorMsg);
      }
      return tagValue;
    }
  }

  /** If an action would not successfully run other than on Darwin. */
  public static final String REQUIRES_DARWIN = "requires-darwin";

  /** Whether we should disable prefetching of inputs before running a local action. */
  public static final String DISABLE_LOCAL_PREFETCH = "disable-local-prefetch";

  /** How many hardware threads an action requires for execution. */
  public static final ParseableRequirement CPU =
      ParseableRequirement.create(
          "cpu:<int>",
          Pattern.compile("cpu:(.+)"),
          new Function<String, String>() {
            @Override
            public String apply(String s) {
              Preconditions.checkNotNull(s);

              int value;
              try {
                value = Integer.parseInt(s);
              } catch (NumberFormatException e) {
                return "can't be parsed as an integer";
              }

              // De-and-reserialize & compare to only allow canonical integer formats.
              if (!Integer.toString(value).equals(s)) {
                return "must be in canonical format (e.g. '4' instead of '+04')";
              }

              if (value < 1) {
                return "can't be zero or negative";
              }

              return null;
            }
          });
}
