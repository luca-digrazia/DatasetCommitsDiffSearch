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

package com.google.devtools.build.lib.events;

import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;

import java.io.Serializable;

/**
 * A Location is a range of characters within a file.
 *
 * The start and end locations may be the same, in which case the Location
 * denotes a point in the file, not a range.  The path may be null, indicating
 * an unknown file.
 *
 * Implementations of Location should be optimised for speed of construction,
 * not speed of attribute access, as far more Locations are created during
 * parsing than are ever used to display error messages.
 */
public abstract class Location implements Serializable {

  @Immutable
  private static final class LocationWithPathAndStartColumn extends Location {
    private final PathFragment path;
    private final LineAndColumn startLineAndColumn;

    private LocationWithPathAndStartColumn(Path path, int startOffSet, int endOffSet,
        LineAndColumn startLineAndColumn) {
      super(startOffSet, endOffSet);
      this.path = path != null ? path.asFragment() : null;
      this.startLineAndColumn = startLineAndColumn;
    }

    @Override
    public PathFragment getPath() { return path; }

    @Override
    public LineAndColumn getStartLineAndColumn() {
      return startLineAndColumn;
    }
  }

  protected final int startOffset;
  protected final int endOffset;

  /**
   * Returns a Location with a given Path, start and end offset and start line and column info. 
   */
  public static Location fromPathAndStartColumn(Path path,  int startOffSet, int endOffSet,
      LineAndColumn startLineAndColumn) {
    return new LocationWithPathAndStartColumn(path, startOffSet, endOffSet, startLineAndColumn);
  }

  /**
   * Returns a Location relating to file 'path', but not to any specific part
   * region within the file.  Try to use a more specific location if possible.
   */
  public static Location fromFile(Path path) {
    return fromFileAndOffsets(path, 0, 0);
  }

  /**
   * Returns a Location relating to the subset of file 'path', starting at
   * 'startOffset' and ending at 'endOffset'.
   */
  public static Location fromFileAndOffsets(final Path path,
                                            int startOffset,
                                            int endOffset) {
    return new LocationWithPathAndStartColumn(path, startOffset, endOffset, null);
  }

  protected Location(int startOffset, int endOffset) {
    this.startOffset = startOffset;
    this.endOffset = endOffset;
  }

  /**
   * Returns the start offset relative to the beginning of the file the object
   * resides in.
   */
  public final int getStartOffset() {
    return startOffset;
  }

  /**
   * Returns the end offset relative to the beginning of the file the object
   * resides in.
   *
   * <p>The end offset is one position past the actual end position, making this method
   * behave in a compatible fashion with {@link String#substring(int, int)}.
   *
   * <p>To compute the length of this location, use {@code getEndOffset() - getStartOffset()}.
   */
  public final int getEndOffset() {
    return endOffset;
  }

  /**
   * Returns the path of the file to which the start/end offsets refer.  May be
   * null if the file name information is not available.
   *
   * This method is intentionally abstract, as a space optimisation.  Some
   * subclass instances implement sharing of common data (e.g. tables for
   * convering offsets into line numbers) and this enables them to share the
   * Path value in the same way.
   */
  public abstract PathFragment getPath();

  /**
   * Returns a (line, column) pair corresponding to the position denoted by
   * getStartOffset.  Returns null if this information is not available.
   */
  public LineAndColumn getStartLineAndColumn() {
    return null;
  }

  /**
   * Returns a (line, column) pair corresponding to the position denoted by
   * getEndOffset.  Returns null if this information is not available.
   */
  public LineAndColumn getEndLineAndColumn() {
    return null;
  }

  /**
   * A default implementation of toString() that formats the location in the
   * following ways based on the amount of information available:
   * <pre>
   *    "foo.cc:23:2"
   *    "23:2"
   *    "foo.cc:char offsets 123--456"
   *    "char offsets 123--456"
   * </pre>
   */
  public String print() {
    return printWithPath(getPath());
  }

  private String printWithPath(PathFragment path) {
    StringBuilder buf = new StringBuilder();
    if (path != null) {
      buf.append(path).append(':');
    }
    LineAndColumn start = getStartLineAndColumn();
    if (start == null) {
      if (getStartOffset() == 0 && getEndOffset() == 0) {
        buf.append("1"); // i.e. line 1 (special case: no information at all)
      } else {
        buf.append("char offsets ").
            append(getStartOffset()).append("--").append(getEndOffset());
      }
    } else {
      buf.append(start.getLine()).append(':').append(start.getColumn());
    }
    return buf.toString();
  }

  /**
   * A default implementation of toString() that formats the location in the following ways based on
   * the amount of information available:
   *
   * <pre>
   *   "foo.cc:23:2"
   *   "23:2"
   *   "foo.cc:char offsets 123--456"
   *   "char offsets 123--456"
   *</pre>
   *
   * <p>This version replace the package's path with the relative package path. I.e., if {@code
   * packagePath} is equivalent to "/absolute/path/to/workspace/pack/age" and {@code
   * relativePackage} is equivalent to "pack/age" then the result for the 2nd character of the 23rd
   * line of the "foo/bar.cc" file in "pack/age" would be "pack/age/foo/bar.cc:23:2" whereas with
   * {@link #print()} the result would be "/absolute/path/to/workspace/pack/age/foo/bar.cc:23:2".
   * 
   * <p>If {@code packagePath} is not a parent of the location path, then the result of this
   * function is the same as the result of {@link #print()}.
   */
  public String print(PathFragment packagePath, PathFragment relativePackage) {
    PathFragment path = getPath();
    if (path == null) {
      return printWithPath(null);
    } else if (path.startsWith(packagePath)) {
      return printWithPath(relativePackage.getRelative(path.relativeTo(packagePath)));
    } else {
      return printWithPath(path);
    }
  }

  /**
   * Prints the object in a sort of reasonable way. This should never be used in user-visible
   * places, only for debugging and testing.
   */
  @Override
  public String toString() {
    return print();
  }

  /**
   * A value class that describes the line and column of an offset in a file.
   */
  @Immutable
  public static final class LineAndColumn {
    private final int line;
    private final int column;

    public LineAndColumn(int line, int column) {
      this.line = line;
      this.column = column;
    }

    public int getLine() {
      return line;
    }

    public int getColumn() {
      return column;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof LineAndColumn)) {
        return false;
      }
      LineAndColumn lac = (LineAndColumn) o;
      return lac.line == line && lac.column == column;
    }

    @Override
    public int hashCode() {
      return line * 81 + column;
    }
  }
}
