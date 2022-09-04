// Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.collect.nestedset;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.build.lib.skyframe.serialization.DeserializationContext;
import com.google.devtools.build.lib.skyframe.serialization.SerializationConstants;
import com.google.devtools.build.lib.skyframe.serialization.SerializationContext;
import com.google.devtools.build.lib.skyframe.serialization.SerializationException;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;

/**
 * Supports association between fingerprints and NestedSet contents. A single NestedSetStore
 * instance should be globally available across a single process.
 *
 * <p>Maintains the fingerprint -> contents side of the bimap by decomposing nested Object[]'s.
 *
 * <p>For example, suppose the NestedSet A can be drawn as:
 *
 * <pre>
 *         A
 *       /  \
 *      B   C
 *     / \
 *    D  E
 * </pre>
 *
 * <p>Then, in memory, A = [[D, E], C]. To store the NestedSet, we would rely on the fingerprint
 * value FPb = fingerprint([D, E]) and write
 *
 * <pre>A -> fingerprint(FPb, C)</pre>
 *
 * <p>On retrieval, A will be reconstructed by first retrieving A using its fingerprint, and then
 * recursively retrieving B using its fingerprint.
 */
public class NestedSetStore {
  /** Stores fingerprint -> NestedSet associations. */
  public interface NestedSetStorageEndpoint {
    /**
     * Associates a fingerprint with the serialized representation of some NestedSet contents.
     * Returns a future that completes when the write completes.
     */
    ListenableFuture<Void> put(ByteString fingerprint, byte[] serializedBytes) throws IOException;

    /**
     * Retrieves the serialized bytes for the NestedSet contents associated with this fingerprint.
     */
    ListenableFuture<byte[]> get(ByteString fingerprint) throws IOException;
  }

  /** An in-memory {@link NestedSetStorageEndpoint} */
  @VisibleForTesting
  static class InMemoryNestedSetStorageEndpoint implements NestedSetStorageEndpoint {
    private final ConcurrentHashMap<ByteString, byte[]> fingerprintToContents =
        new ConcurrentHashMap<>();

    @Override
    public ListenableFuture<Void> put(ByteString fingerprint, byte[] serializedBytes) {
      fingerprintToContents.put(fingerprint, serializedBytes);
      return Futures.immediateFuture(null);
    }

    @Override
    public ListenableFuture<byte[]> get(ByteString fingerprint) {
      return Futures.immediateFuture(fingerprintToContents.get(fingerprint));
    }
  }

  /** An in-memory cache for fingerprint <-> NestedSet associations. */
  @VisibleForTesting
  static class NestedSetCache {
    private final Cache<ByteString, ListenableFuture<Object[]>> fingerprintToContents =
        CacheBuilder.newBuilder()
            .concurrencyLevel(SerializationConstants.DESERIALIZATION_POOL_SIZE)
            .weakValues()
            .build();

    /** Object/Object[] contents to fingerprint. Maintained for fast fingerprinting. */
    private final Cache<Object[], FingerprintComputationResult> contentsToFingerprint =
        CacheBuilder.newBuilder()
            .concurrencyLevel(SerializationConstants.DESERIALIZATION_POOL_SIZE)
            .weakKeys()
            .build();

    /**
     * Returns the NestedSet contents associated with the given fingerprint. Returns null if the
     * fingerprint is not known.
     */
    @Nullable
    public ListenableFuture<Object[]> contentsForFingerprint(ByteString fingerprint) {
      return fingerprintToContents.getIfPresent(fingerprint);
    }

    /**
     * Retrieves the fingerprint associated with the given NestedSet contents, or null if the given
     * contents are not known.
     */
    @Nullable
    public FingerprintComputationResult fingerprintForContents(Object[] contents) {
      return contentsToFingerprint.getIfPresent(contents);
    }

    /** Associates the provided fingerprint and NestedSet contents. */
    public void put(
        FingerprintComputationResult fingerprintComputationResult,
        ListenableFuture<Object[]> contents) {
      contents.addListener(
          () -> {
            try {
              contentsToFingerprint.put(Futures.getDone(contents), fingerprintComputationResult);
            } catch (ExecutionException e) {
              throw new AssertionError(
                  "Expected write for "
                      + fingerprintComputationResult.fingerprint()
                      + " to be complete",
                  e.getCause());
            }
          },
          MoreExecutors.directExecutor());
      fingerprintToContents.put(fingerprintComputationResult.fingerprint(), contents);
    }

    public void put(FingerprintComputationResult fingerprintComputationResult, Object[] contents) {
      contentsToFingerprint.put(contents, fingerprintComputationResult);
      fingerprintToContents.put(
          fingerprintComputationResult.fingerprint(), Futures.immediateFuture(contents));
    }
  }

  /** The result of a fingerprint computation, including the status of its storage. */
  @VisibleForTesting
  @AutoValue
  public abstract static class FingerprintComputationResult {
    static FingerprintComputationResult create(
        ByteString fingerprint, ListenableFuture<Void> writeStatus) {
      return new AutoValue_NestedSetStore_FingerprintComputationResult(fingerprint, writeStatus);
    }

    abstract ByteString fingerprint();

    @VisibleForTesting
    public abstract ListenableFuture<Void> writeStatus();
  }

  private final NestedSetCache nestedSetCache;
  private final NestedSetStorageEndpoint nestedSetStorageEndpoint;
  private final Executor executor;

  /** Creates a NestedSetStore with the provided {@link NestedSetStorageEndpoint} as a backend. */
  public NestedSetStore(NestedSetStorageEndpoint nestedSetStorageEndpoint) {
    this(nestedSetStorageEndpoint, new NestedSetCache(), MoreExecutors.directExecutor());
  }

  /**
   * Creates a NestedSetStore with the provided {@link NestedSetStorageEndpoint} and executor for
   * deserialization.
   */
  public NestedSetStore(NestedSetStorageEndpoint nestedSetStorageEndpoint, Executor executor) {
    this(nestedSetStorageEndpoint, new NestedSetCache(), executor);
  }

  @VisibleForTesting
  public NestedSetStore(
      NestedSetStorageEndpoint nestedSetStorageEndpoint,
      NestedSetCache nestedSetCache,
      Executor executor) {
    this.nestedSetStorageEndpoint = nestedSetStorageEndpoint;
    this.nestedSetCache = nestedSetCache;
    this.executor = executor;
  }

  /** Creates a NestedSetStore with an in-memory storage backend. */
  public static NestedSetStore inMemory() {
    return new NestedSetStore(new InMemoryNestedSetStorageEndpoint());
  }

  /**
   * Computes and returns the fingerprint for the given NestedSet contents using the given {@link
   * SerializationContext}, while also associating the contents with the computed fingerprint in the
   * store. Recursively does the same for all transitive members (i.e. Object[] members) of the
   * provided contents.
   */
  @VisibleForTesting
  public FingerprintComputationResult computeFingerprintAndStore(
      Object[] contents, SerializationContext serializationContext)
      throws SerializationException, IOException {
    FingerprintComputationResult priorFingerprint = nestedSetCache.fingerprintForContents(contents);
    if (priorFingerprint != null) {
      return priorFingerprint;
    }

    // For every fingerprint computation, we need to use a new memoization table.  This is required
    // to guarantee that the same child will always have the same fingerprint - otherwise,
    // differences in memoization context could cause part of a child to be memoized in one
    // fingerprinting but not in the other.  We expect this clearing of memoization state to be a
    // major source of extra work over the naive serialization approach.  The same value may have to
    // be serialized many times across separate fingerprintings.
    SerializationContext newSerializationContext = serializationContext.getNewMemoizingContext();
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(byteArrayOutputStream);

    ImmutableList.Builder<ListenableFuture<Void>> futureBuilder = ImmutableList.builder();
    try {
      codedOutputStream.writeInt32NoTag(contents.length);
      for (Object child : contents) {
        if (child instanceof Object[]) {
          FingerprintComputationResult fingerprintComputationResult =
              computeFingerprintAndStore((Object[]) child, serializationContext);
          futureBuilder.add(fingerprintComputationResult.writeStatus());
          newSerializationContext.serialize(
              fingerprintComputationResult.fingerprint(), codedOutputStream);
        } else {
          newSerializationContext.serialize(child, codedOutputStream);
        }
      }
      codedOutputStream.flush();
    } catch (IOException e) {
      throw new SerializationException("Could not serialize NestedSet contents", e);
    }

    byte[] serializedBytes = byteArrayOutputStream.toByteArray();
    ByteString fingerprint =
        ByteString.copyFrom(Hashing.md5().hashBytes(serializedBytes).asBytes());
    futureBuilder.add(nestedSetStorageEndpoint.put(fingerprint, serializedBytes));

    // If this is a NestedSet<NestedSet>, serialization of the contents will itself have writes.
    ListenableFuture<Void> innerWriteFutures =
        newSerializationContext.createFutureToBlockWritingOn();
    if (innerWriteFutures != null) {
      futureBuilder.add(innerWriteFutures);
    }

    ListenableFuture<Void> writeFuture =
        Futures.whenAllComplete(futureBuilder.build())
            .call(() -> null, MoreExecutors.directExecutor());
    FingerprintComputationResult fingerprintComputationResult =
        FingerprintComputationResult.create(fingerprint, writeFuture);

    nestedSetCache.put(fingerprintComputationResult, contents);

    return fingerprintComputationResult;
  }

  /** Retrieves and deserializes the NestedSet contents associated with the given fingerprint. */
  public ListenableFuture<Object[]> getContentsAndDeserialize(
      ByteString fingerprint, DeserializationContext deserializationContext) throws IOException {
    ListenableFuture<Object[]> contents = nestedSetCache.contentsForFingerprint(fingerprint);
    if (contents != null) {
      return contents;
    }
    ListenableFuture<byte[]> retrieved = nestedSetStorageEndpoint.get(fingerprint);
    ListenableFuture<Object[]> result =
        Futures.transformAsync(
            retrieved,
            bytes -> {
              CodedInputStream codedIn = CodedInputStream.newInstance(bytes);
              int numberOfElements = codedIn.readInt32();
              DeserializationContext newDeserializationContext =
                  deserializationContext.getNewMemoizingContext();

              // The elements of this list are futures for the deserialized values of these
              // NestedSet contents.  For direct members, the futures complete immediately and yield
              // an Object.  For transitive members (fingerprints), the futures complete with the
              // underlying fetch, and yield Object[]s.
              List<ListenableFuture<?>> deserializationFutures = new ArrayList<>();
              for (int i = 0; i < numberOfElements; i++) {
                Object deserializedElement = newDeserializationContext.deserialize(codedIn);
                if (deserializedElement instanceof ByteString) {
                  deserializationFutures.add(
                      getContentsAndDeserialize(
                          (ByteString) deserializedElement, deserializationContext));
                } else {
                  deserializationFutures.add(Futures.immediateFuture(deserializedElement));
                }
              }

              return Futures.whenAllComplete(deserializationFutures)
                  .call(
                      () -> {
                        Object[] deserializedContents = new Object[deserializationFutures.size()];
                        for (int i = 0; i < deserializationFutures.size(); i++) {
                          deserializedContents[i] = Futures.getDone(deserializationFutures.get(i));
                        }
                        return deserializedContents;
                      },
                      executor);
            },
            executor);

    FingerprintComputationResult fingerprintComputationResult =
        FingerprintComputationResult.create(fingerprint, Futures.immediateFuture(null));
    nestedSetCache.put(fingerprintComputationResult, result);
    return result;
  }
}
