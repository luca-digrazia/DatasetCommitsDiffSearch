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

package com.google.devtools.build.lib.bazel.repository;

import com.google.devtools.build.lib.bazel.rules.workspace.HttpArchiveRule;
import com.google.devtools.build.lib.bazel.rules.workspace.HttpJarRule;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.utils.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Creates decompressors to use on archive.  Use {@link DecompressorFactory#create} to get the
 * correct type of decompressor for the input archive, then call
 * {@link Decompressor#decompress} to decompress it.
 */
public abstract class DecompressorFactory {

  public static Decompressor create(String targetKind, String targetName, Path archivePath)
      throws DecompressorException {
    String baseName = archivePath.getBaseName();

    if (targetKind.startsWith(HttpJarRule.NAME + " ")) {
      if (baseName.endsWith(".jar")) {
        return new JarDecompressor(targetKind, targetName, archivePath);
      } else {
        throw new DecompressorException(
            "Expected " + HttpJarRule.NAME + " " + targetName
                + " to create file with a .jar suffix (got " + archivePath + ")");
      }
    }

    if (targetKind.startsWith(HttpArchiveRule.NAME + " ")) {
      if (baseName.endsWith(".zip") || baseName.endsWith(".jar")) {
        return new ZipDecompressor(archivePath);
      } else {
        throw new DecompressorException(
            "Expected " + HttpArchiveRule.NAME + " " + targetName
                + " to create file with a .zip or .jar suffix (got " + archivePath + ")");
      }
    }

    throw new DecompressorException(
        "No decompressor found for " + targetKind + " rule " + targetName
            + " (got " + archivePath + ")");
  }

  /**
   * General decompressor for an archive. Should be overridden for each specific archive type.
   */
  public abstract static class Decompressor {
    protected final Path archiveFile;

    private Decompressor(Path archiveFile) {
      this.archiveFile = archiveFile;
    }

    /**
     * This is overridden by archive-specific decompression logic.  Often this logic will create
     * files and directories under the {@link Decompressor#archiveFile}'s parent directory.
     *
     * @return the path to the repository directory. That is, the returned path will be a directory
     * containing a WORKSPACE file.
     */
    public abstract Path decompress() throws DecompressorException;
  }

  static class JarDecompressor extends Decompressor {
    private final String targetKind;
    private final String targetName;

    public JarDecompressor(String targetKind, String targetName, Path archiveFile) {
      super(archiveFile);
      this.targetKind = targetKind;
      this.targetName = targetName;
    }

    /**
     * The .jar can be used compressed, so this just exposes it in a way Bazel can use.
     *
     * <p>It moves the jar from some-name/foo.jar to some-name/repository/jar/foo.jar and creates a
     * BUILD file containing one entry: a .jar.
     */
    @Override
    public Path decompress() throws DecompressorException {
      Path destinationDirectory = archiveFile.getParentDirectory().getRelative("repository");
      // Example: archiveFile is .external-repository/some-name/foo.jar.
      String baseName = archiveFile.getBaseName();

      try {
        FileSystemUtils.createDirectoryAndParents(destinationDirectory);
        // .external-repository/some-name/repository/WORKSPACE.
        Path workspaceFile = destinationDirectory.getRelative("WORKSPACE");
        FileSystemUtils.writeContent(workspaceFile, Charset.forName("UTF-8"),
            "# DO NOT EDIT: automatically generated WORKSPACE file for " + targetKind
                + " rule " + targetName);
        // .external-repository/some-name/repository/jar.
        Path jarDirectory = destinationDirectory.getRelative("jar");
        FileSystemUtils.createDirectoryAndParents(jarDirectory);
        // .external-repository/some-name/repository/jar/foo.jar is a symbolic link to the jar in
        // .external-repository/some-name.
        Path jarSymlink = jarDirectory.getRelative(baseName);
        if (!jarSymlink.exists()) {
          jarSymlink.createSymbolicLink(archiveFile);
        }
        // .external-repository/some-name/repository/jar/BUILD defines the //jar target.
        Path buildFile = jarDirectory.getRelative("BUILD");
        FileSystemUtils.writeLinesAs(buildFile, Charset.forName("UTF-8"),
            "# DO NOT EDIT: automatically generated BUILD file for " + targetKind + " rule "
                + targetName,
            "java_import(",
            "    name = 'jar',",
            "    jars = ['" + baseName + "'],",
            "    visibility = ['//visibility:public']",
            ")");
      } catch (IOException e) {
        throw new DecompressorException(e.getMessage());
      }
      return destinationDirectory;
    }
  }

  private static class ZipDecompressor extends Decompressor {
    public ZipDecompressor(Path archiveFile) {
      super(archiveFile);
    }

    /**
     * This unzips the zip file to a sibling directory of {@link Decompressor#archiveFile}. The
     * zip file is expected to have the WORKSPACE file at the top level, e.g.:
     *
     * <pre>
     * $ unzip -lf some-repo.zip
     * Archive:  ../repo.zip
     *  Length      Date    Time    Name
     * ---------  ---------- -----   ----
     *        0  2014-11-20 15:50   WORKSPACE
     *        0  2014-11-20 16:10   foo/
     *      236  2014-11-20 15:52   foo/BUILD
     *      ...
     * </pre>
     */
    @Override
    public Path decompress() throws DecompressorException {
      Path destinationDirectory = archiveFile.getParentDirectory().getRelative("repository");
      try (InputStream is = new FileInputStream(archiveFile.getPathString())) {
        ArchiveInputStream in = new ArchiveStreamFactory().createArchiveInputStream(
            ArchiveStreamFactory.ZIP, is);
        ZipArchiveEntry entry = (ZipArchiveEntry) in.getNextEntry();
        while (entry != null) {
          extractZipEntry(in, entry, destinationDirectory);
          entry = (ZipArchiveEntry) in.getNextEntry();
        }
      } catch (IOException | ArchiveException e) {
        throw new DecompressorException(
            "Error extracting " + archiveFile + " to " + destinationDirectory + ": "
                + e.getMessage());
      }
      return destinationDirectory;
    }

    private void extractZipEntry(
        ArchiveInputStream in, ZipArchiveEntry entry, Path destinationDirectory)
        throws IOException, DecompressorException {
      PathFragment relativePath = new PathFragment(entry.getName());
      if (relativePath.isAbsolute()) {
        throw new DecompressorException("Failed to extract " + relativePath
            + ", zipped paths cannot be absolute");
      }
      Path outputPath = destinationDirectory.getRelative(relativePath);
      FileSystemUtils.createDirectoryAndParents(outputPath.getParentDirectory());
      if (entry.isDirectory()) {
        FileSystemUtils.createDirectoryAndParents(outputPath);
      } else {
        try (OutputStream out = new FileOutputStream(new File(outputPath.getPathString()))) {
          IOUtils.copy(in, out);
        } catch (IOException e) {
          throw new DecompressorException("Error writing " + outputPath + " from "
              + archiveFile);
        }
      }
    }
  }

  /**
   * Exceptions thrown when something goes wrong decompressing an archive.
   */
  public static class DecompressorException extends Exception {
    public DecompressorException(String message) {
      super(message);
    }
  }
}