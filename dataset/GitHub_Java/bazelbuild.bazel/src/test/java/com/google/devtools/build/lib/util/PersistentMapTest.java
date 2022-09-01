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
package com.google.devtools.build.lib.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.util.FsApparatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for the {@link PersistentMap}.
 */
@RunWith(JUnit4.class)
public class PersistentMapTest {
  public static class PersistentStringMap extends PersistentMap<String, String> {
    boolean updateJournal = true;
    boolean keepJournal = false;

    public PersistentStringMap(Map<String, String> map, Path mapFile,
        Path journalFile) throws IOException {
      super(0x0, map, mapFile, journalFile);
      load();
    }

    @Override
    protected String readKey(DataInputStream in) throws IOException {
      return in.readUTF();
    }
    @Override
    protected String readValue(DataInputStream in) throws IOException {
      return in.readUTF();
    }
    @Override
    protected void writeKey(String key, DataOutputStream out)
        throws IOException {
      out.writeUTF(key);
    }
    @Override
    protected void writeValue(String value, DataOutputStream out)
        throws IOException {
      out.writeUTF(value);
    }
    @Override
    protected boolean updateJournal() {
      return updateJournal;
    }
    @Override
    protected boolean keepJournal() {
      return keepJournal;
    }
  }

  private FsApparatus scratch = FsApparatus.newInMemory();

  private PersistentStringMap map;
  private Path mapFile;
  private Path journalFile;

  @Before
  public void setUp() throws Exception {
    mapFile = scratch.fs().getPath("/tmp/map.txt");
    journalFile = scratch.fs().getPath("/tmp/journal.txt");
    createMap();
  }

  private void createMap() throws Exception {
    Map<String, String> map = new HashMap<>();
    this.map = new PersistentStringMap(map, mapFile, journalFile);
  }

  @Test
  public void map() throws Exception {
    createMap();
    map.put("foo", "bar");
    map.put("baz", "bang");
    assertEquals("bar", map.get("foo"));
    assertEquals("bang", map.get("baz"));
    assertEquals(2, map.size());
    long size = map.save();
    assertEquals(mapFile.getFileSize(), size);
    assertEquals("bar", map.get("foo"));
    assertEquals("bang", map.get("baz"));
    assertEquals(2, map.size());

    createMap(); // create a new map
    assertEquals("bar", map.get("foo"));
    assertEquals("bang", map.get("baz"));
    assertEquals(2, map.size());
  }

  @Test
  public void remove() throws Exception {
    createMap();
    map.put("foo", "bar");
    map.put("baz", "bang");
    long size = map.save();
    assertEquals(mapFile.getFileSize(), size);
    assertFalse(journalFile.exists());
    map.remove("foo");
    assertEquals(1, map.size());
    assertTrue(journalFile.exists());
    createMap(); // create a new map
    assertEquals(1, map.size());
  }

  @Test
  public void clear() throws Exception {
    createMap();
    map.put("foo", "bar");
    map.put("baz", "bang");
    map.save();
    assertTrue(mapFile.exists());
    assertFalse(journalFile.exists());
    map.clear();
    assertEquals(0, map.size());
    assertTrue(mapFile.exists());
    assertFalse(journalFile.exists());
    createMap(); // create a new map
    assertEquals(0, map.size());
  }

  @Test
  public void noUpdateJournal() throws Exception {
    createMap();
    map.put("foo", "bar");
    map.put("baz", "bang");
    map.save();
    assertFalse(journalFile.exists());
    // prevent updating the journal
    map.updateJournal = false;
    // remove an entry
    map.remove("foo");
    assertEquals(1, map.size());
    // no journal file written
    assertFalse(journalFile.exists());
    createMap(); // create a new map
    // both entries are still in the map on disk
    assertEquals(2, map.size());
  }

  @Test
  public void keepJournal() throws Exception {
    createMap();
    map.put("foo", "bar");
    map.put("baz", "bang");
    map.save();
    assertFalse(journalFile.exists());

    // Keep the journal through the save.
    map.updateJournal = false;
    map.keepJournal = true;

    // remove an entry
    map.remove("foo");
    assertEquals(1, map.size());
    // no journal file written
    assertFalse(journalFile.exists());

    long size = map.save();
    assertEquals(1, map.size());
    // The journal must be serialzed on save(), even if !updateJournal.
    assertTrue(journalFile.exists());
    assertEquals(journalFile.getFileSize() + mapFile.getFileSize(), size);

    map.load();
    assertEquals(1, map.size());
    assertTrue(journalFile.exists());

    createMap(); // create a new map
    assertEquals(1, map.size());

    map.keepJournal = false;
    map.save();
    assertEquals(1, map.size());
    assertFalse(journalFile.exists());
  }

  @Test
  public void multipleJournalUpdates() throws Exception {
    createMap();
    map.put("foo", "bar");
    map.save();
    assertFalse(journalFile.exists());
    // add an entry
    map.put("baz", "bang");
    assertEquals(2, map.size());
    // journal file written
    assertTrue(journalFile.exists());
    createMap(); // create a new map
    // both entries are still in the map on disk
    assertEquals(2, map.size());
    // add another entry
    map.put("baz2", "bang2");
    assertEquals(3, map.size());
    // journal file written
    assertTrue(journalFile.exists());
    createMap(); // create a new map
    // all three entries are still in the map on disk
    assertEquals(3, map.size());
  }
}
