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
package com.google.devtools.build.lib.vfs;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.devtools.build.lib.concurrent.ThreadSafety.ThreadSafe;
import com.google.devtools.build.lib.profiler.Profiler;
import com.google.devtools.build.lib.profiler.ProfilerTask;
import com.google.devtools.build.lib.unix.ErrnoFileStatus;
import com.google.devtools.build.lib.unix.FilesystemUtils;
import com.google.devtools.build.lib.unix.FilesystemUtils.Dirents;
import com.google.devtools.build.lib.unix.FilesystemUtils.ReadTypes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class implements the FileSystem interface using direct calls to the
 * UNIX filesystem.
 */
// Not final only for testing.
@ThreadSafe
public class UnixFileSystem extends AbstractFileSystem {

  public static final UnixFileSystem INSTANCE = new UnixFileSystem();
  /**
   * Eager implementation of FileStatus for file systems that have an atomic
   * stat(2) syscall. A proxy for {@link com.google.devtools.build.lib.unix.FileStatus}.
   * Note that isFile and getLastModifiedTime have slightly different meanings
   * between UNIX and VFS.
   */
  @VisibleForTesting
  protected static class UnixFileStatus implements FileStatus {

    private final com.google.devtools.build.lib.unix.FileStatus status;

    UnixFileStatus(com.google.devtools.build.lib.unix.FileStatus status) {
      this.status = status;
    }

    @Override
    public boolean isFile() { return !isDirectory() && !isSymbolicLink(); }

    @Override
    public boolean isDirectory() { return status.isDirectory(); }

    @Override
    public boolean isSymbolicLink() { return status.isSymbolicLink(); }

    @Override
    public long getSize() { return status.getSize(); }

    @Override
    public long getLastModifiedTime() {
      return (status.getLastModifiedTime() * 1000)
          + (status.getFractionalLastModifiedTime() / 1000000);
    }

    @Override
    public long getLastChangeTime() {
      return (status.getLastChangeTime() * 1000)
          + (status.getFractionalLastChangeTime() / 1000000);
    }

    @Override
    public long getNodeId() {
      // Note that we may want to include more information in this id number going forward,
      // especially the device number.
      return status.getInodeNumber();
    }

    int getPermissions() { return status.getPermissions(); }

    @Override
    public String toString() { return status.toString(); }
  }

  @Override
  protected Collection<Path> getDirectoryEntries(Path path) throws IOException {
    String name = path.getPathString();
    String[] entries;
    long startTime = Profiler.nanoTimeMaybe();
    try {
      entries = FilesystemUtils.readdir(name);
    } finally {
      profiler.logSimpleTask(startTime, ProfilerTask.VFS_DIR, name);
    }
    Collection<Path> result = new ArrayList<>(entries.length);
    for (String entry : entries) {
      result.add(path.getChild(entry));
    }
    return result;
  }

  @Override
  protected PathFragment resolveOneLink(Path path) throws IOException {
    // Beware, this seemingly simple code belies the complex specification of
    // FileSystem.resolveOneLink().
    return stat(path, false).isSymbolicLink()
        ? readSymbolicLink(path)
        : null;
  }

  /**
   * Converts from {@link com.google.devtools.build.lib.unix.FilesystemUtils.Dirents.Type} to
   * {@link com.google.devtools.build.lib.vfs.Dirent.Type}.
   */
  private static Dirent.Type convertToDirentType(Dirents.Type type) {
    switch (type) {
      case FILE:
        return Dirent.Type.FILE;
      case DIRECTORY:
        return Dirent.Type.DIRECTORY;
      case SYMLINK:
        return Dirent.Type.SYMLINK;
      case UNKNOWN:
        return Dirent.Type.UNKNOWN;
      default:
        throw new IllegalArgumentException("Unknown type " + type);
    }
  }

  @Override
  protected Collection<Dirent> readdir(Path path, boolean followSymlinks) throws IOException {
    String name = path.getPathString();
    long startTime = Profiler.nanoTimeMaybe();
    try {
      Dirents unixDirents = FilesystemUtils.readdir(name,
          followSymlinks ? ReadTypes.FOLLOW : ReadTypes.NOFOLLOW);
      Preconditions.checkState(unixDirents.hasTypes());
      List<Dirent> dirents = Lists.newArrayListWithCapacity(unixDirents.size());
      for (int i = 0; i < unixDirents.size(); i++) {
        dirents.add(new Dirent(unixDirents.getName(i),
            convertToDirentType(unixDirents.getType(i))));
      }
      return dirents;
    } finally {
      profiler.logSimpleTask(startTime, ProfilerTask.VFS_DIR, name);
    }
  }

  @Override
  protected FileStatus stat(Path path, boolean followSymlinks) throws IOException {
    return statInternal(path, followSymlinks);
  }

  @VisibleForTesting
  protected UnixFileStatus statInternal(Path path, boolean followSymlinks) throws IOException {
    String name = path.getPathString();
    long startTime = Profiler.nanoTimeMaybe();
    try {
      return new UnixFileStatus(followSymlinks
                                      ? FilesystemUtils.stat(name)
                                      : FilesystemUtils.lstat(name));
    } finally {
      profiler.logSimpleTask(startTime, ProfilerTask.VFS_STAT, name);
    }
  }

  // Like stat(), but returns null instead of throwing.
  // This is a performance optimization in the case where clients
  // catch and don't re-throw.
  @Override
  protected FileStatus statNullable(Path path, boolean followSymlinks) {
    String name = path.getPathString();
    long startTime = Profiler.nanoTimeMaybe();
    try {
      ErrnoFileStatus stat = followSymlinks
          ? FilesystemUtils.errnoStat(name)
          : FilesystemUtils.errnoLstat(name);
      return stat.hasError() ? null : new UnixFileStatus(stat);
    } finally {
      profiler.logSimpleTask(startTime, ProfilerTask.VFS_STAT, name);
    }
  }

  @Override
  protected boolean exists(Path path, boolean followSymlinks) {
    return statNullable(path, followSymlinks) != null;
  }

  /**
   * Return true iff the {@code stat} of {@code path} resulted in an {@code ENOENT}
   * or {@code ENOTDIR} error.
   */
  @Override
  protected FileStatus statIfFound(Path path, boolean followSymlinks) throws IOException {
    String name = path.getPathString();
    long startTime = Profiler.nanoTimeMaybe();
    try {
      ErrnoFileStatus stat = followSymlinks
          ? FilesystemUtils.errnoStat(name)
          : FilesystemUtils.errnoLstat(name);
      if (!stat.hasError()) {
        return new UnixFileStatus(stat);
      }
      int errno = stat.getErrno();
      if (errno == ErrnoFileStatus.ENOENT || errno == ErrnoFileStatus.ENOTDIR) {
        return null;
      }
      // This should not return -- we are calling stat here just to throw the proper exception.
      // However, since there may be transient IO errors, we cannot guarantee that an exception will
      // be thrown.
      // TODO(bazel-team): Extract the exception-construction code and make it visible separately in
      // FilesystemUtils to avoid having to do a duplicate stat call.
      return stat(path, followSymlinks);
    } finally {
      profiler.logSimpleTask(startTime, ProfilerTask.VFS_STAT, name);
    }
  }

  @Override
  protected boolean isDirectory(Path path, boolean followSymlinks) {
    FileStatus stat = statNullable(path, followSymlinks);
    return stat != null && stat.isDirectory();
  }

  @Override
  protected boolean isFile(Path path, boolean followSymlinks) {
    // Note, FileStatus.isFile means *regular* file whereas Path.isFile may
    // mean special file too, so we don't return FileStatus.isFile here.
    FileStatus status = statNullable(path, followSymlinks);
    return status != null && !(status.isSymbolicLink() || status.isDirectory());
  }

  @Override
  protected boolean isReadable(Path path) throws IOException {
    return (statInternal(path, true).getPermissions() & 0400) != 0;
  }

  @Override
  protected boolean isWritable(Path path) throws IOException {
    return (statInternal(path, true).getPermissions() & 0200) != 0;
  }

  @Override
  protected boolean isExecutable(Path path) throws IOException {
    return (statInternal(path, true).getPermissions() & 0100) != 0;
  }

  /**
   * Adds or remove the bits specified in "permissionBits" to the permission
   * mask of the file specified by {@code path}. If the argument {@code add} is
   * true, the specified permissions are added, otherwise they are removed.
   *
   * @throws IOException if there was an error writing the file's metadata
   */
  private void modifyPermissionBits(Path path, int permissionBits, boolean add)
    throws IOException {
    synchronized (path) {
      int oldMode = statInternal(path, true).getPermissions();
      int newMode = add ? (oldMode | permissionBits) : (oldMode & ~permissionBits);
      FilesystemUtils.chmod(path.toString(), newMode);
    }
  }

  @Override
  protected void setReadable(Path path, boolean readable) throws IOException {
    modifyPermissionBits(path, 0400, readable);
  }

  @Override
  protected void setWritable(Path path, boolean writable) throws IOException {
    modifyPermissionBits(path, 0200, writable);
  }

  @Override
  protected void setExecutable(Path path, boolean executable) throws IOException {
    modifyPermissionBits(path, 0111, executable);
  }

  @Override
  protected void chmod(Path path, int mode) throws IOException {
    synchronized (path) {
      FilesystemUtils.chmod(path.toString(), mode);
    }
  }

  @Override
  public boolean supportsModifications() {
    return true;
  }

  @Override
  public boolean supportsSymbolicLinks() {
    return true;
  }

  @Override
  protected boolean createDirectory(Path path) throws IOException {
    synchronized (path) {
      // Note: UNIX mkdir(2), FilesystemUtils.mkdir() and createDirectory all
      // have different ways of representing failure!
      if (FilesystemUtils.mkdir(path.toString(), 0777)) {
        return true; // successfully created
      }

      // false => EEXIST: something is already in the way (file/dir/symlink)
      if (isDirectory(path, false)) {
        return false; // directory already existed
      } else {
        throw new IOException(path + " (File exists)");
      }
    }
  }

  @Override
  protected void createSymbolicLink(Path linkPath, PathFragment targetFragment)
      throws IOException {
    synchronized (linkPath) {
      FilesystemUtils.symlink(targetFragment.toString(), linkPath.toString());
    }
  }

  @Override
  protected PathFragment readSymbolicLink(Path path) throws IOException {
    String name = path.toString();
    long startTime = Profiler.nanoTimeMaybe();
    try {
      return new PathFragment(FilesystemUtils.readlink(name));
    } catch (IOException e) {
      // EINVAL => not a symbolic link.  Anything else is a real error.
      throw e.getMessage().endsWith("(Invalid argument)") ? new NotASymlinkException(path) : e;
    } finally {
      profiler.logSimpleTask(startTime, ProfilerTask.VFS_READLINK, name);
    }
  }

  @Override
  protected void renameTo(Path sourcePath, Path targetPath) throws IOException {
    synchronized (sourcePath) {
      FilesystemUtils.rename(sourcePath.toString(), targetPath.toString());
    }
  }

  @Override
  protected long getFileSize(Path path, boolean followSymlinks) throws IOException {
    return stat(path, followSymlinks).getSize();
  }

  @Override
  protected boolean delete(Path path) throws IOException {
    String name = path.toString();
    long startTime = Profiler.nanoTimeMaybe();
    synchronized (path) {
      try {
        return FilesystemUtils.remove(name);
      } finally {
        profiler.logSimpleTask(startTime, ProfilerTask.VFS_DELETE, name);
      }
    }
  }

  @Override
  protected long getLastModifiedTime(Path path, boolean followSymlinks) throws IOException {
    return stat(path, followSymlinks).getLastModifiedTime();
  }

  @Override
  protected boolean isSymbolicLink(Path path) {
    FileStatus stat = statNullable(path, false);
    return stat != null && stat.isSymbolicLink();
  }

  @Override
  protected void setLastModifiedTime(Path path, long newTime) throws IOException {
    synchronized (path) {
      if (newTime == -1L) { // "now"
        FilesystemUtils.utime(path.toString(), true, 0, 0);
      } else {
        // newTime > MAX_INT => -ve unixTime
        int unixTime = (int) (newTime / 1000);
        FilesystemUtils.utime(path.toString(), false, unixTime, unixTime);
      }
    }
  }

  @Override
  protected byte[] getxattr(Path path, String name, boolean followSymlinks) throws IOException {
    String pathName = path.toString();
    long startTime = Profiler.nanoTimeMaybe();
    try {
      return followSymlinks
          ? FilesystemUtils.getxattr(pathName, name) : FilesystemUtils.lgetxattr(pathName, name);
    } catch (UnsupportedOperationException e) {
      // getxattr() syscall is not supported by the underlying filesystem (it returned ENOTSUP).
      // Per method contract, treat this as ENODATA.
      return null;
    } finally {
      profiler.logSimpleTask(startTime, ProfilerTask.VFS_XATTR, pathName);
    }
  }

  @Override
  protected byte[] getMD5Digest(Path path) throws IOException {
    String name = path.toString();
    long startTime = Profiler.nanoTimeMaybe();
    try {
      return FilesystemUtils.md5sum(name).asBytes();
    } finally {
      profiler.logSimpleTask(startTime, ProfilerTask.VFS_MD5, name);
    }
  }
}
