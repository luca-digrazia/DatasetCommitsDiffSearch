// Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.remote;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.ActionInputHelper;
import com.google.devtools.build.lib.clock.JavaClock;
import com.google.devtools.build.lib.remote.blobstore.ConcurrentMapBlobStore;
import com.google.devtools.build.lib.vfs.FileSystem;
import com.google.devtools.build.lib.vfs.FileSystem.HashFunction;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.inmemoryfs.InMemoryFileSystem;
import com.google.devtools.remoteexecution.v1test.ActionResult;
import com.google.devtools.remoteexecution.v1test.Digest;
import com.google.devtools.remoteexecution.v1test.Directory;
import com.google.devtools.remoteexecution.v1test.DirectoryNode;
import com.google.devtools.remoteexecution.v1test.FileNode;
import com.google.devtools.remoteexecution.v1test.Tree;
import io.grpc.Context;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link SimpleBlobStoreActionCache}. */
@RunWith(JUnit4.class)
public class SimpleBlobStoreActionCacheTest {
  private static final DigestUtil DIGEST_UTIL = new DigestUtil(HashFunction.SHA256);

  private FileSystem fs;
  private Path execRoot;
  private FakeActionInputFileCache fakeFileCache;

  @Before
  public final void setUp() throws Exception {
    Chunker.setDefaultChunkSizeForTesting(1000); // Enough for everything to be one chunk.
    fs = new InMemoryFileSystem(new JavaClock(), HashFunction.SHA256);
    execRoot = fs.getPath("/exec/root");
    FileSystemUtils.createDirectoryAndParents(execRoot);
    fakeFileCache = new FakeActionInputFileCache(execRoot);

    Path stdout = fs.getPath("/tmp/stdout");
    Path stderr = fs.getPath("/tmp/stderr");
    FileSystemUtils.createDirectoryAndParents(stdout.getParentDirectory());
    FileSystemUtils.createDirectoryAndParents(stderr.getParentDirectory());
    Context withEmptyMetadata =
        TracingMetadataUtils.contextWithMetadata(
            "none", "none", DIGEST_UTIL.asActionKey(Digest.getDefaultInstance()));
    withEmptyMetadata.attach();
  }

  private SimpleBlobStoreActionCache newClient() {
    return newClient(new ConcurrentHashMap<>());
  }

  private SimpleBlobStoreActionCache newClient(ConcurrentMap<String, byte[]> map) {
    return new SimpleBlobStoreActionCache(new ConcurrentMapBlobStore(map), DIGEST_UTIL);
  }

  @Test
  public void testDownloadEmptyBlob() throws Exception {
    SimpleBlobStoreActionCache client = newClient();
    Digest emptyDigest = DIGEST_UTIL.compute(new byte[0]);
    // Will not call the mock Bytestream interface at all.
    assertThat(client.downloadBlob(emptyDigest)).isEmpty();
  }

  @Test
  public void testDownloadBlob() throws Exception {
    final ConcurrentMap<String, byte[]> map = new ConcurrentHashMap<>();
    Digest digest = DIGEST_UTIL.computeAsUtf8("abcdefg");
    map.put(digest.getHash(), "abcdefg".getBytes(Charsets.UTF_8));
    final SimpleBlobStoreActionCache client = newClient(map);
    assertThat(new String(client.downloadBlob(digest), UTF_8)).isEqualTo("abcdefg");
  }

  @Test
  public void testDownloadAllResults() throws Exception {
    Digest fooDigest = DIGEST_UTIL.computeAsUtf8("foo-contents");
    Digest barDigest = DIGEST_UTIL.computeAsUtf8("bar-contents");
    Digest emptyDigest = DIGEST_UTIL.compute(new byte[0]);

    final ConcurrentMap<String, byte[]> map = new ConcurrentHashMap<>();
    map.put(fooDigest.getHash(), "foo-contents".getBytes(Charsets.UTF_8));
    map.put(barDigest.getHash(), "bar-contents".getBytes(Charsets.UTF_8));
    SimpleBlobStoreActionCache client = newClient(map);

    ActionResult.Builder result = ActionResult.newBuilder();
    result.addOutputFilesBuilder().setPath("a/foo").setDigest(fooDigest);
    result.addOutputFilesBuilder().setPath("b/empty").setDigest(emptyDigest);
    result.addOutputFilesBuilder().setPath("a/bar").setDigest(barDigest).setIsExecutable(true);
    client.download(result.build(), execRoot, null);
    assertThat(DIGEST_UTIL.compute(execRoot.getRelative("a/foo"))).isEqualTo(fooDigest);
    assertThat(DIGEST_UTIL.compute(execRoot.getRelative("b/empty"))).isEqualTo(emptyDigest);
    assertThat(DIGEST_UTIL.compute(execRoot.getRelative("a/bar"))).isEqualTo(barDigest);
    assertThat(execRoot.getRelative("a/bar").isExecutable()).isTrue();
  }

  @Test
  public void testDownloadDirectory() throws Exception {
    Digest fooDigest = DIGEST_UTIL.computeAsUtf8("foo-contents");
    Digest quxDigest = DIGEST_UTIL.computeAsUtf8("qux-contents");
    Tree barTreeMessage =
        Tree.newBuilder()
            .setRoot(
                Directory.newBuilder()
                    .addFiles(
                        FileNode.newBuilder()
                            .setName("qux")
                            .setDigest(quxDigest)
                            .setIsExecutable(true)))
            .build();
    Digest barTreeDigest = DIGEST_UTIL.compute(barTreeMessage);

    final ConcurrentMap<String, byte[]> map = new ConcurrentHashMap<>();
    map.put(fooDigest.getHash(), "foo-contents".getBytes(Charsets.UTF_8));
    map.put(barTreeDigest.getHash(), barTreeMessage.toByteArray());
    map.put(quxDigest.getHash(), "qux-contents".getBytes(Charsets.UTF_8));
    SimpleBlobStoreActionCache client = newClient(map);

    ActionResult.Builder result = ActionResult.newBuilder();
    result.addOutputFilesBuilder().setPath("a/foo").setDigest(fooDigest);
    result.addOutputDirectoriesBuilder().setPath("a/bar").setTreeDigest(barTreeDigest);
    client.download(result.build(), execRoot, null);

    assertThat(DIGEST_UTIL.compute(execRoot.getRelative("a/foo"))).isEqualTo(fooDigest);
    assertThat(DIGEST_UTIL.compute(execRoot.getRelative("a/bar/qux"))).isEqualTo(quxDigest);
    assertThat(execRoot.getRelative("a/bar/qux").isExecutable()).isTrue();
  }

  @Test
  public void testDownloadDirectoryEmpty() throws Exception {
    Tree barTreeMessage = Tree.newBuilder().setRoot(Directory.newBuilder()).build();
    Digest barTreeDigest = DIGEST_UTIL.compute(barTreeMessage);

    final ConcurrentMap<String, byte[]> map = new ConcurrentHashMap<>();
    map.put(barTreeDigest.getHash(), barTreeMessage.toByteArray());
    SimpleBlobStoreActionCache client = newClient(map);

    ActionResult.Builder result = ActionResult.newBuilder();
    result.addOutputDirectoriesBuilder().setPath("a/bar").setTreeDigest(barTreeDigest);
    client.download(result.build(), execRoot, null);

    assertThat(execRoot.getRelative("a/bar").isDirectory()).isTrue();
  }

  @Test
  public void testDownloadDirectoryNested() throws Exception {
    Digest fooDigest = DIGEST_UTIL.computeAsUtf8("foo-contents");
    Digest quxDigest = DIGEST_UTIL.computeAsUtf8("qux-contents");
    Directory wobbleDirMessage =
        Directory.newBuilder()
            .addFiles(FileNode.newBuilder().setName("qux").setDigest(quxDigest))
            .build();
    Digest wobbleDirDigest = DIGEST_UTIL.compute(wobbleDirMessage);
    Tree barTreeMessage =
        Tree.newBuilder()
            .setRoot(
                Directory.newBuilder()
                    .addFiles(
                        FileNode.newBuilder()
                            .setName("qux")
                            .setDigest(quxDigest)
                            .setIsExecutable(true))
                    .addDirectories(
                        DirectoryNode.newBuilder().setName("wobble").setDigest(wobbleDirDigest)))
            .addChildren(wobbleDirMessage)
            .build();
    Digest barTreeDigest = DIGEST_UTIL.compute(barTreeMessage);

    final ConcurrentMap<String, byte[]> map = new ConcurrentHashMap<>();
    map.put(fooDigest.getHash(), "foo-contents".getBytes(Charsets.UTF_8));
    map.put(barTreeDigest.getHash(), barTreeMessage.toByteArray());
    map.put(quxDigest.getHash(), "qux-contents".getBytes(Charsets.UTF_8));
    SimpleBlobStoreActionCache client = newClient(map);

    ActionResult.Builder result = ActionResult.newBuilder();
    result.addOutputFilesBuilder().setPath("a/foo").setDigest(fooDigest);
    result.addOutputDirectoriesBuilder().setPath("a/bar").setTreeDigest(barTreeDigest);
    client.download(result.build(), execRoot, null);

    assertThat(DIGEST_UTIL.compute(execRoot.getRelative("a/foo"))).isEqualTo(fooDigest);
    assertThat(DIGEST_UTIL.compute(execRoot.getRelative("a/bar/wobble/qux"))).isEqualTo(quxDigest);
    assertThat(execRoot.getRelative("a/bar/wobble/qux").isExecutable()).isFalse();
  }

  @Test
  public void testUploadBlob() throws Exception {
    final Digest digest = DIGEST_UTIL.computeAsUtf8("abcdefg");

    final ConcurrentMap<String, byte[]> map = new ConcurrentHashMap<>();
    final SimpleBlobStoreActionCache client = newClient(map);

    byte[] bytes = "abcdefg".getBytes(UTF_8);
    assertThat(client.uploadBlob(bytes)).isEqualTo(digest);
    assertThat(map.keySet()).containsExactly(digest.getHash());
    assertThat(map.entrySet()).hasSize(1);
    assertThat(map.get(digest.getHash())).isEqualTo(bytes);
  }

  @Test
  public void testUploadDirectory() throws Exception {
    final Digest fooDigest =
        fakeFileCache.createScratchInput(ActionInputHelper.fromPath("a/foo"), "xyz");
    final Digest quxDigest =
        fakeFileCache.createScratchInput(ActionInputHelper.fromPath("bar/qux"), "abc");
    final Digest barDigest =
        fakeFileCache.createScratchInputDirectory(
            ActionInputHelper.fromPath("bar"),
            Tree.newBuilder()
                .setRoot(
                    Directory.newBuilder()
                        .addFiles(
                            FileNode.newBuilder()
                                .setIsExecutable(true)
                                .setName("qux")
                                .setDigest(quxDigest)
                                .build())
                        .build())
                .build());
    final Path fooFile = execRoot.getRelative("a/foo");
    final Path quxFile = execRoot.getRelative("bar/qux");
    quxFile.setExecutable(true);
    final Path barDir = execRoot.getRelative("bar");

    final ConcurrentMap<String, byte[]> map = new ConcurrentHashMap<>();
    final SimpleBlobStoreActionCache client = newClient(map);

    ActionResult.Builder result = ActionResult.newBuilder();
    client.upload(result, execRoot, ImmutableList.<Path>of(fooFile, barDir));
    ActionResult.Builder expectedResult = ActionResult.newBuilder();
    expectedResult.addOutputFilesBuilder().setPath("a/foo").setDigest(fooDigest);
    expectedResult.addOutputDirectoriesBuilder().setPath("bar").setTreeDigest(barDigest);
    assertThat(result.build()).isEqualTo(expectedResult.build());

    assertThat(map.keySet())
        .containsExactly(fooDigest.getHash(), quxDigest.getHash(), barDigest.getHash());
  }

  @Test
  public void testUploadDirectoryEmpty() throws Exception {
    final Digest barDigest =
        fakeFileCache.createScratchInputDirectory(
            ActionInputHelper.fromPath("bar"),
            Tree.newBuilder().setRoot(Directory.newBuilder().build()).build());
    final Path barDir = execRoot.getRelative("bar");

    final ConcurrentMap<String, byte[]> map = new ConcurrentHashMap<>();
    final SimpleBlobStoreActionCache client = newClient(map);

    ActionResult.Builder result = ActionResult.newBuilder();
    client.upload(result, execRoot, ImmutableList.<Path>of(barDir));
    ActionResult.Builder expectedResult = ActionResult.newBuilder();
    expectedResult.addOutputDirectoriesBuilder().setPath("bar").setTreeDigest(barDigest);
    assertThat(result.build()).isEqualTo(expectedResult.build());

    assertThat(map.keySet()).containsExactly(barDigest.getHash());
  }

  @Test
  public void testUploadDirectoryNested() throws Exception {
    final Digest wobbleDigest =
        fakeFileCache.createScratchInput(ActionInputHelper.fromPath("bar/test/wobble"), "xyz");
    final Digest quxDigest =
        fakeFileCache.createScratchInput(ActionInputHelper.fromPath("bar/qux"), "abc");
    final Directory testDirMessage =
        Directory.newBuilder()
            .addFiles(FileNode.newBuilder().setName("wobble").setDigest(wobbleDigest).build())
            .build();
    final Digest testDigest = DIGEST_UTIL.compute(testDirMessage);
    final Tree barTree =
        Tree.newBuilder()
            .setRoot(
                Directory.newBuilder()
                    .addFiles(
                        FileNode.newBuilder()
                            .setIsExecutable(true)
                            .setName("qux")
                            .setDigest(quxDigest))
                    .addDirectories(
                        DirectoryNode.newBuilder().setName("test").setDigest(testDigest)))
            .addChildren(testDirMessage)
            .build();
    final Digest barDigest =
        fakeFileCache.createScratchInputDirectory(ActionInputHelper.fromPath("bar"), barTree);

    final ConcurrentMap<String, byte[]> map = new ConcurrentHashMap<>();
    final SimpleBlobStoreActionCache client = newClient(map);

    final Path quxFile = execRoot.getRelative("bar/qux");
    quxFile.setExecutable(true);
    final Path barDir = execRoot.getRelative("bar");

    ActionResult.Builder result = ActionResult.newBuilder();
    client.upload(result, execRoot, ImmutableList.<Path>of(barDir));
    ActionResult.Builder expectedResult = ActionResult.newBuilder();
    expectedResult.addOutputDirectoriesBuilder().setPath("bar").setTreeDigest(barDigest);
    assertThat(result.build()).isEqualTo(expectedResult.build());

    assertThat(map.keySet())
        .containsExactly(wobbleDigest.getHash(), quxDigest.getHash(), barDigest.getHash());
  }
}
