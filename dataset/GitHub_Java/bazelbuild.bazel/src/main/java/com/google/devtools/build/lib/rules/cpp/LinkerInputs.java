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

package com.google.devtools.build.lib.rules.cpp;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.collect.CollectionUtils;
import com.google.devtools.build.lib.concurrent.ThreadSafety;
import com.google.devtools.build.lib.util.Preconditions;

/**
 * Factory for creating new {@link LinkerInput} objects.
 */
public abstract class LinkerInputs {
  /**
   * An opaque linker input that is not a library, for example a linker script or an individual
   * object file.
   */
  @ThreadSafety.Immutable
  public static class SimpleLinkerInput implements LinkerInput {
    private final Artifact artifact;
    private final ArtifactCategory category;

    public SimpleLinkerInput(Artifact artifact, ArtifactCategory category) {
      String basename = artifact.getFilename();
      switch (category) {
        case STATIC_LIBRARY:
          Preconditions.checkState(Link.ARCHIVE_LIBRARY_FILETYPES.matches(basename));
          break;

        case DYNAMIC_LIBRARY:
          Preconditions.checkState(Link.SHARED_LIBRARY_FILETYPES.matches(basename));
          break;

        case OBJECT_FILE:
          // We skip file extension checks for TreeArtifacts because they represent directory
          // artifacts without a file extension.
          Preconditions.checkState(
              artifact.isTreeArtifact() || Link.OBJECT_FILETYPES.matches(basename));
          break;

        default:
          throw new IllegalStateException();
      }
      this.artifact = Preconditions.checkNotNull(artifact);
      this.category = category;
    }

    @Override
    public ArtifactCategory getArtifactCategory() {
      return category;
    }

    @Override
    public Artifact getArtifact() {
      return artifact;
    }

    @Override
    public Artifact getOriginalLibraryArtifact() {
      return artifact;
    }

    @Override
    public boolean containsObjectFiles() {
      return false;
    }

    @Override
    public boolean isFake() {
      return false;
    }

    @Override
    public Iterable<Artifact> getObjectFiles() {
      throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object that) {
      if (this == that) {
        return true;
      }

      if (!(that instanceof SimpleLinkerInput)) {
        return false;
      }

      SimpleLinkerInput other = (SimpleLinkerInput) that;
      return artifact.equals(other.artifact) && isFake() == other.isFake();
    }

    @Override
    public int hashCode() {
      return artifact.hashCode();
    }

    @Override
    public String toString() {
      return "SimpleLinkerInput(" + artifact + ")";
    }
  }

  /**
   * A linker input that is a fake object file generated by cc_fake_binary. The contained
   * artifact must be an object file.
   */
  @ThreadSafety.Immutable
  private static class FakeLinkerInput extends SimpleLinkerInput {
    private FakeLinkerInput(Artifact artifact) {
      super(artifact, ArtifactCategory.OBJECT_FILE);
      Preconditions.checkState(Link.OBJECT_FILETYPES.matches(artifact.getFilename()));
    }

    @Override
    public boolean isFake() {
      return true;
    }
  }

  /**
   * A library the user can link to. This is different from a simple linker input in that it also
   * has a library identifier.
   */
  public interface LibraryToLink extends LinkerInput {
    ImmutableMap<Artifact, Artifact> getLTOBitcodeFiles();

    /**
     * Return the identifier for the library. This is used for de-duplication of linker inputs: two
     * libraries should have the same identifier iff they are in fact the same library but linked
     * in a different way (e.g. static/dynamic, PIC/no-PIC)
     */
    String getLibraryIdentifier();
  }

  /**
   * This class represents a solib library symlink. Its library identifier is inherited from
   * the library that it links to.
   */
  @ThreadSafety.Immutable
  public static class SolibLibraryToLink implements LibraryToLink {
    private final Artifact solibSymlinkArtifact;
    private final Artifact libraryArtifact;
    private final String libraryIdentifier;

    private SolibLibraryToLink(Artifact solibSymlinkArtifact, Artifact libraryArtifact,
        String libraryIdentifier) {
      Preconditions.checkArgument(
          Link.SHARED_LIBRARY_FILETYPES.matches(solibSymlinkArtifact.getFilename()));
      this.solibSymlinkArtifact = solibSymlinkArtifact;
      this.libraryArtifact = libraryArtifact;
      this.libraryIdentifier = libraryIdentifier;
    }

    @Override
    public String toString() {
      return String.format("SolibLibraryToLink(%s -> %s",
          solibSymlinkArtifact.toString(), libraryArtifact.toString());
    }

    @Override
    public ArtifactCategory getArtifactCategory() {
      return ArtifactCategory.DYNAMIC_LIBRARY;
    }

    @Override
    public Artifact getArtifact() {
      return solibSymlinkArtifact;
    }

    @Override
    public String getLibraryIdentifier() {
      return libraryIdentifier;
    }

    @Override
    public boolean containsObjectFiles() {
      return false;
    }

    @Override
    public ImmutableMap<Artifact, Artifact> getLTOBitcodeFiles() {
      return ImmutableMap.of();
    }

    @Override
    public boolean isFake() {
      return false;
    }

    @Override
    public Iterable<Artifact> getObjectFiles() {
      throw new IllegalStateException(
          "LinkerInputs: does not support getObjectFiles: " + toString());
    }

    @Override
    public Artifact getOriginalLibraryArtifact() {
      return libraryArtifact;
    }

    @Override
    public boolean equals(Object that) {
      if (this == that) {
        return true;
      }

      if (!(that instanceof SolibLibraryToLink)) {
        return false;
      }

      SolibLibraryToLink thatSolib = (SolibLibraryToLink) that;
      return
          solibSymlinkArtifact.equals(thatSolib.solibSymlinkArtifact) &&
          libraryArtifact.equals(thatSolib.libraryArtifact);
    }

    @Override
    public int hashCode() {
      return solibSymlinkArtifact.hashCode();
    }
  }

  /**
   * This class represents a library that may contain object files.
   */
  @ThreadSafety.Immutable
  private static class CompoundLibraryToLink implements LibraryToLink {
    private final Artifact libraryArtifact;
    private final ArtifactCategory category;
    private final String libraryIdentifier;
    private final Iterable<Artifact> objectFiles;
    private final ImmutableMap<Artifact, Artifact> ltoBitcodeFiles;

    private CompoundLibraryToLink(
        Artifact libraryArtifact,
        ArtifactCategory category,
        String libraryIdentifier,
        Iterable<Artifact> objectFiles,
        ImmutableMap<Artifact, Artifact> ltoBitcodeFiles) {
      String basename = libraryArtifact.getFilename();
      switch (category) {
        case ALWAYSLINK_STATIC_LIBRARY:
          Preconditions.checkState(Link.LINK_LIBRARY_FILETYPES.matches(basename));
          break;

        case STATIC_LIBRARY:
          Preconditions.checkState(Link.ARCHIVE_FILETYPES.matches(basename));
          break;

        case DYNAMIC_LIBRARY:
          Preconditions.checkState(Link.SHARED_LIBRARY_FILETYPES.matches(basename));
          break;

        default:
          throw new IllegalStateException();
      }

      this.libraryArtifact = Preconditions.checkNotNull(libraryArtifact);
      this.category = category;
      this.libraryIdentifier = libraryIdentifier;
      this.objectFiles = objectFiles == null ? null : CollectionUtils.makeImmutable(objectFiles);
      this.ltoBitcodeFiles =
          (ltoBitcodeFiles == null) ? ImmutableMap.<Artifact, Artifact>of() : ltoBitcodeFiles;
    }

    @Override
    public String toString() {
      return String.format("CompoundLibraryToLink(%s)", libraryArtifact.toString());
    }

    @Override
    public ArtifactCategory getArtifactCategory() {
      return category;
    }

    @Override
    public Artifact getArtifact() {
      return libraryArtifact;
    }

    @Override
    public Artifact getOriginalLibraryArtifact() {
      return libraryArtifact;
    }

    @Override
    public String getLibraryIdentifier() {
      return libraryIdentifier;
    }

    @Override
    public boolean containsObjectFiles() {
      return objectFiles != null;
    }

    @Override
    public boolean isFake() {
      return false;
    }

    @Override
    public Iterable<Artifact> getObjectFiles() {
      Preconditions.checkNotNull(objectFiles);
      return objectFiles;
    }

    @Override
    public ImmutableMap<Artifact, Artifact> getLTOBitcodeFiles() {
      return ltoBitcodeFiles;
    }

    @Override
    public boolean equals(Object that) {
      if (this == that) {
        return true;
      }

      if (!(that instanceof CompoundLibraryToLink)) {
        return false;
      }

      return libraryArtifact.equals(((CompoundLibraryToLink) that).libraryArtifact);
    }

    @Override
    public int hashCode() {
      return libraryArtifact.hashCode();
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////
  // Public factory constructors:
  //////////////////////////////////////////////////////////////////////////////////////

  /**
   * Creates linker input objects for non-library files.
   */
  public static Iterable<LinkerInput> simpleLinkerInputs(Iterable<Artifact> input,
      final ArtifactCategory category) {
    return Iterables.transform(input, new Function<Artifact, LinkerInput>() {
        @Override
        public LinkerInput apply(Artifact artifact) {
          return simpleLinkerInput(artifact, category);
        }
      });
  }

  /**
   * Creates a linker input for which we do not know what objects files it consists of.
   */
  public static LinkerInput simpleLinkerInput(Artifact artifact, ArtifactCategory category) {
    // This precondition check was in place and *most* of the tests passed with them; the only
    // exception is when you mention a generated .a file in the srcs of a cc_* rule.
    // Preconditions.checkArgument(!ARCHIVE_LIBRARY_FILETYPES.contains(artifact.getFileType()));
    return new SimpleLinkerInput(artifact, category);
  }

  /**
   * Creates a fake linker input. The artifact must be an object file.
   */
  public static LinkerInput fakeLinkerInput(Artifact artifact) {
    return new FakeLinkerInput(artifact);
  }

  /**
   * Creates input libraries for which we do not know what objects files it consists of.
   */
  public static Iterable<LibraryToLink> opaqueLibrariesToLink(
      final ArtifactCategory category, Iterable<Artifact> input) {
    return Iterables.transform(input, new Function<Artifact, LibraryToLink>() {
      @Override
      public LibraryToLink apply(Artifact artifact) {
        return precompiledLibraryToLink(artifact, category);
      }
    });
  }

  /**
   * Creates a solib library symlink from the given artifact.
   */
  public static LibraryToLink solibLibraryToLink(
      Artifact solibSymlink, Artifact original, String libraryIdentifier) {
    return new SolibLibraryToLink(solibSymlink, original, libraryIdentifier);
  }

  /**
   * Creates an input library for which we do not know what objects files it consists of.
   */
  public static LibraryToLink precompiledLibraryToLink(
      Artifact artifact, ArtifactCategory category) {
    // This precondition check was in place and *most* of the tests passed with them; the only
    // exception is when you mention a generated .a file in the srcs of a cc_* rule.
    // It was very useful for proving that this actually works, though.
    // Preconditions.checkArgument(
    //     !(artifact.getGeneratingAction() instanceof CppLinkAction) ||
    //     !Link.ARCHIVE_LIBRARY_FILETYPES.contains(artifact.getFileType()));
    return new CompoundLibraryToLink(
        artifact, category, CcLinkingOutputs.libraryIdentifierOf(artifact), null, null);
  }

  public static LibraryToLink opaqueLibraryToLink(
      Artifact artifact, ArtifactCategory category, String libraryIdentifier) {
    return new CompoundLibraryToLink(artifact, category, libraryIdentifier, null, null);
  }

  /** Creates a library to link with the specified object files. */
  public static LibraryToLink newInputLibrary(
      Artifact library,
      ArtifactCategory category,
      String libraryIdentifier,
      Iterable<Artifact> objectFiles,
      ImmutableMap<Artifact, Artifact> ltoBitcodeFiles) {
    return new CompoundLibraryToLink(
        library, category, libraryIdentifier, objectFiles, ltoBitcodeFiles);
  }

  private static final Function<LibraryToLink, Artifact> LIBRARY_TO_NON_SOLIB =
      new Function<LibraryToLink, Artifact>() {
        @Override
        public Artifact apply(LibraryToLink input) {
          return input.getOriginalLibraryArtifact();
        }
      };

  public static Iterable<Artifact> toNonSolibArtifacts(Iterable<LibraryToLink> libraries) {
    return Iterables.transform(libraries, LIBRARY_TO_NON_SOLIB);
  }

  /**
   * Returns the linker input artifacts from a collection of {@link LinkerInput} objects.
   */
  public static Iterable<Artifact> toLibraryArtifacts(Iterable<? extends LinkerInput> artifacts) {
    return Iterables.transform(artifacts, new Function<LinkerInput, Artifact>() {
      @Override
      public Artifact apply(LinkerInput input) {
        return input.getArtifact();
      }
    });
  }
}
