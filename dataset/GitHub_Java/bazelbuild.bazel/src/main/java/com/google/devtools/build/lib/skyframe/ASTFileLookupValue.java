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

package com.google.devtools.build.lib.skyframe;

import com.google.common.base.Preconditions;
import com.google.common.collect.Interner;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.concurrent.BlazeInterners;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.build.lib.syntax.StarlarkFile;
import com.google.devtools.build.lib.vfs.Root;
import com.google.devtools.build.skyframe.NotComparableSkyValue;
import com.google.devtools.build.skyframe.SkyFunctionName;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.errorprone.annotations.FormatMethod;
import java.util.Objects;
import javax.annotation.Nullable;

// TODO(adonovan): Ensure the result is always resolved and update the docstring.
/**
 * A value that represents an AST file lookup result. There are two subclasses: one for the case
 * where the file is found, and another for the case where the file is missing (but there are no
 * other errors).
 */
// In practice, almost any change to a .bzl causes the ASTFileLookupValue to be recomputed.
// We could do better with a finer-grained notion of equality for StarlarkFile than "the source
// files differ". In particular, a trivial change such as fixing a typo in a comment should not
// cause invalidation. (Changes that are only slightly more substantial may be semantically
// significant. For example, inserting a blank line affects subsequent line numbers, which appear
// in error messages and query output.)
//
// Comparing syntax trees for equality is complex and expensive, so the most practical
// implementation of this optimization will have to wait until Starlark files are compiled,
// at which point byte-equality of the compiled representation (which is simple to compute)
// will serve. (At that point, ASTFileLookup should be renamed CompileStarlark.)
//
public abstract class ASTFileLookupValue implements NotComparableSkyValue {

  // TODO(adonovan): flatten this hierarchy into a single class.
  // It would only cost one word per Starlark file.
  // Eliminate lookupSuccessful; use getAST() != null.

  public abstract boolean lookupSuccessful();

  public abstract StarlarkFile getAST();

  public abstract byte[] getDigest();

  public abstract String getError();

  /** If the file is found, this class encapsulates the parsed AST. */
  @AutoCodec.VisibleForSerialization
  public static class ASTLookupWithFile extends ASTFileLookupValue {
    private final StarlarkFile ast;
    private final byte[] digest;

    private ASTLookupWithFile(StarlarkFile ast, byte[] digest) {
      this.ast = Preconditions.checkNotNull(ast);
      this.digest = Preconditions.checkNotNull(digest);
    }

    @Override
    public boolean lookupSuccessful() {
      return true;
    }

    @Override
    public StarlarkFile getAST() {
      return this.ast;
    }

    @Override
    public byte[] getDigest() {
      return this.digest;
    }

    @Override
    public String getError() {
      throw new IllegalStateException(
          "attempted to retrieve unsuccessful lookup reason for successful lookup");
    }
  }

  /** If the file isn't found, this class encapsulates a message with the reason. */
  @AutoCodec.VisibleForSerialization
  public static class ASTLookupNoFile extends ASTFileLookupValue {
    private final String errorMsg;

    private ASTLookupNoFile(String errorMsg) {
      this.errorMsg = Preconditions.checkNotNull(errorMsg);
    }

    @Override
    public boolean lookupSuccessful() {
      return false;
    }

    @Override
    public StarlarkFile getAST() {
      throw new IllegalStateException("attempted to retrieve AST from an unsuccessful lookup");
    }

    @Override
    public byte[] getDigest() {
      throw new IllegalStateException("attempted to retrieve digest for unsuccessful lookup");
    }

    @Override
    public String getError() {
      return this.errorMsg;
    }
  }

  /** Constructs a value from a failure before parsing a file. */
  @FormatMethod
  static ASTFileLookupValue noFile(String format, Object... args) {
    return new ASTLookupNoFile(String.format(format, args));
  }

  /** Constructs a value from a parsed file. */
  public static ASTFileLookupValue withFile(StarlarkFile ast, byte[] digest) {
    return new ASTLookupWithFile(ast, digest);
  }

  private static final Interner<Key> keyInterner = BlazeInterners.newWeakInterner();

  /** Types of bzl files we may encounter. */
  enum Kind {
    /** A regular .bzl file loaded on behalf of a BUILD or WORKSPACE file. */
    // The reason we can share a single key type for these environments is that they have the same
    // symbol names, even though their symbol definitions (particularly for the "native" object)
    // differ. (See also #11954, which aims to make even the symbol definitions the same.)
    NORMAL,

    /** A .bzl file loaded during evaluation of the {@code @builtins} pseudo-repository. */
    BUILTINS,

    /** The prelude file, whose declarations are implicitly loaded by all BUILD files. */
    PRELUDE,

    /**
     * A virtual empty file that does not correspond to a lookup in the filesystem. This is used for
     * the default prelude contents, when the real prelude's contents should be ignored (in
     * particular, when its package is missing).
     */
    EMPTY_PRELUDE,
  }

  /** SkyKey for retrieving a .bzl AST. */
  static class Key implements SkyKey {

    /** The root in which the .bzl file is to be found. Null for EMPTY_PRELUDE. */
    @Nullable final Root root;

    /** The label of the .bzl to be retrieved. Null for EMPTY_PRELUDE. */
    @Nullable final Label label;

    final Kind kind;

    private Key(Root root, Label label, Kind kind) {
      this.root = root;
      this.label = label;
      this.kind = Preconditions.checkNotNull(kind);
      if (kind != Kind.EMPTY_PRELUDE) {
        Preconditions.checkNotNull(root);
        Preconditions.checkNotNull(label);
      }
    }

    boolean isPrelude() {
      return kind == Kind.PRELUDE || kind == Kind.EMPTY_PRELUDE;
    }

    @Override
    public int hashCode() {
      // TODO(bazel-team): Consider optimizing e.g. by omitting root from the hash. Roots are not
      // interned and in the common case there's only one.
      return Objects.hash(Key.class, root, label, kind);
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (other instanceof Key) {
        Key that = (Key) other;
        // Compare roots last since that's the more expensive step.
        return this.kind == that.kind
            && Objects.equals(this.label, that.label)
            && Objects.equals(this.root, that.root);
      }
      return false;
    }

    @Override
    public SkyFunctionName functionName() {
      return SkyFunctions.AST_FILE_LOOKUP;
    }
  }

  /** Constructs a key for loading a regular (non-prelude) .bzl. */
  public static Key key(Root root, Label label) {
    return keyInterner.intern(new Key(root, label, Kind.NORMAL));
  }

  /** Constructs a key for loading a builtins .bzl. */
  // TODO(#11437): Retrieve the builtins bzl from the root given by
  // --experimental_builtins_bzl_path, instead of making the caller specify it here.
  public static Key keyForBuiltins(Root root, Label label) {
    return keyInterner.intern(new Key(root, label, Kind.BUILTINS));
  }

  /** Constructs a key for loading the prelude .bzl. */
  static Key keyForPrelude(Root root, Label label) {
    return keyInterner.intern(new Key(root, label, Kind.PRELUDE));
  }

  /** The unique SkyKey of EMPTY_PRELUDE kind. */
  static final Key EMPTY_PRELUDE_KEY = new Key(/*root=*/ null, /*label=*/ null, Kind.EMPTY_PRELUDE);
}
