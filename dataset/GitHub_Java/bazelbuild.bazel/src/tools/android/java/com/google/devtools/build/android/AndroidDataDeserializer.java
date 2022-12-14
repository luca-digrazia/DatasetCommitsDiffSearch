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
package com.google.devtools.build.android;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.google.devtools.build.android.ParsedAndroidData.KeyValueConsumer;
import com.google.devtools.build.android.proto.SerializeFormat;
import com.google.devtools.build.android.proto.SerializeFormat.Header;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Deserializes {@link DataKey}, {@link DataValue} entries from a binary file. */
public class AndroidDataDeserializer {
  private static final Logger logger = Logger.getLogger(AndroidDataDeserializer.class.getName());

  public static AndroidDataDeserializer create() {
    return new AndroidDataDeserializer();
  }

  private AndroidDataDeserializer() {}

  /**
   * Reads the serialized {@link DataKey} and {@link DataValue} to the {@link KeyValueConsumers}.
   *
   * @param inPath The path to the serialized protocol buffer.
   * @param consumers The {@link KeyValueConsumers} for the entries {@link DataKey} -&gt; {@link
   *     DataValue}.
   * @throws DeserializationException Raised for an IOException or when the inPath is not a valid
   *     proto buffer.
   */
  public void read(Path inPath, KeyValueConsumers consumers) {
    Stopwatch timer = Stopwatch.createStarted();
    try (InputStream in = Files.newInputStream(inPath, StandardOpenOption.READ)) {
      FileSystem currentFileSystem = inPath.getFileSystem();
      Header header = Header.parseDelimitedFrom(in);
      if (header == null) {
        throw new DeserializationException("No Header found in " + inPath);
      }
      readEntriesSegment(consumers, in, currentFileSystem, header);
    } catch (IOException e) {
      throw new DeserializationException(e);
    } finally {
      logger.fine(
          String.format("Deserialized in merged in %sms", timer.elapsed(TimeUnit.MILLISECONDS)));
    }
  }

  private void readEntriesSegment(
      KeyValueConsumers consumers,
      InputStream in,
      FileSystem currentFileSystem,
      Header header)
      throws IOException {
    int numberOfEntries = header.getEntryCount();
    Map<DataKey, KeyValueConsumer<DataKey, ? extends DataValue>> keys =
        Maps.newLinkedHashMapWithExpectedSize(numberOfEntries);
    for (int i = 0; i < numberOfEntries; i++) {
      SerializeFormat.DataKey protoKey = SerializeFormat.DataKey.parseDelimitedFrom(in);
      if (protoKey.hasResourceType()) {
        FullyQualifiedName resourceName = FullyQualifiedName.fromProto(protoKey);
        keys.put(
            resourceName,
            FullyQualifiedName.isOverwritable(resourceName)
                ? consumers.overwritingConsumer
                : consumers.combiningConsumer);
      } else {
        keys.put(RelativeAssetPath.fromProto(protoKey, currentFileSystem), consumers.assetConsumer);
      }
    }

    // Read back the sources table.
    DataSourceTable sourceTable = DataSourceTable.read(in, currentFileSystem, header);

    // TODO(corysmith): Make this a lazy read of the values.
    for (Entry<DataKey, KeyValueConsumer<DataKey, ?>> entry : keys.entrySet()) {
      SerializeFormat.DataValue protoValue = SerializeFormat.DataValue.parseDelimitedFrom(in);
      DataSource source = sourceTable.sourceFromId(protoValue.getSourceId());
      if (protoValue.hasXmlValue()) {
        // TODO(corysmith): Figure out why the generics are wrong.
        // If I use Map<DataKey, KeyValueConsumer<DataKey, ? extends DataValue>>, I can put
        // consumers into the map, but I can't call consume.
        // If I use Map<DataKey, KeyValueConsumer<DataKey, ? super DataValue>>, I can consume
        // but I can't put.
        // Same for below.
        @SuppressWarnings("unchecked")
        KeyValueConsumer<DataKey, DataValue> value =
            (KeyValueConsumer<DataKey, DataValue>) entry.getValue();
        value.consume(entry.getKey(), DataResourceXml.from(protoValue, source));
      } else {
        @SuppressWarnings("unchecked")
        KeyValueConsumer<DataKey, DataValue> value =
            (KeyValueConsumer<DataKey, DataValue>) entry.getValue();
        value.consume(entry.getKey(), DataValueFile.of(source));
      }
    }
  }
}
