/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.stetho.inspector.domstorage;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

public class SharedPreferencesHelper {
  private static final String PREFS_SUFFIX = ".xml";

  private SharedPreferencesHelper() {
  }

  public static List<String> getSharedPreferenceTags(Context context) {
    ArrayList<String> tags = new ArrayList<String>();

    String rootPath = context.getApplicationInfo().dataDir + "/shared_prefs";
    File root = new File(rootPath);
    if (root.exists()) {
      for (File file : root.listFiles()) {
        String fileName = file.getName();
        if (fileName.endsWith(PREFS_SUFFIX)) {
          tags.add(fileName.substring(0, fileName.length() - PREFS_SUFFIX.length()));
        }
      }
    }

    Collections.sort(tags);

    return tags;
  }

  public static Set<Entry<String, ?>> getSharedPreferenceEntriesSorted(SharedPreferences preferences) {
    TreeSet<Entry<String, ?>> entries = new TreeSet<>(new Comparator<Entry<String, ?>>() {
      @Override
      public int compare(Entry<String, ?> lhs, Entry<String, ?> rhs) {
        return lhs.getKey().compareTo(rhs.getKey());
      }
    });
    entries.addAll(preferences.getAll().entrySet());
    return entries;
  }

  public static String valueToString(Object value) {
    if (value != null) {
      if (value instanceof Set) {
        JSONArray array = new JSONArray();
        for (String entry : (Set<String>)value) {
          array.put(entry);
        }
        return array.toString();
      } else {
        return value.toString();
      }
    } else {
      return null;
    }
  }

  @Nullable
  public static Object valueFromString(String newValue, Object existingValue)
      throws IllegalArgumentException {
    if (existingValue instanceof Integer) {
      return Integer.parseInt(newValue);
    } else if (existingValue instanceof Long) {
      return Long.parseLong(newValue);
    } else if (existingValue instanceof Float) {
      return Float.parseFloat(newValue);
    } else if (existingValue instanceof Boolean) {
      return parseBoolean(newValue);
    } else if (existingValue instanceof String) {
      return newValue;
    } else if (existingValue instanceof Set) {
      try {
        JSONArray obj = new JSONArray(newValue);
        int objN = obj.length();
        HashSet<String> set = new HashSet<String>(objN);
        for (int i = 0; i < objN; i++) {
          set.add(obj.getString(i));
        }
        return set;
      } catch (JSONException e) {
        throw new IllegalArgumentException(e);
      }
    } else {
      throw new IllegalArgumentException(
          "Unsupported type: " + existingValue.getClass().getName());
    }
  }

  private static Boolean parseBoolean(String s) throws IllegalArgumentException {
    if ("1".equals(s) || "true".equalsIgnoreCase(s)) {
      return Boolean.TRUE;
    } else if ("0".equals(s) || "false".equalsIgnoreCase(s)) {
      return Boolean.FALSE;
    }
    throw new IllegalArgumentException("Expected boolean, got " + s);
  }
}
