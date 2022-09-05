// Copyright 2015 Google Inc. All rights reserved.
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
package com.google.devtools.build.lib.rules.android;

import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.FilesToRunProvider;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;

/**
 * Description of the tools Blaze needs from an Android SDK.
 */
@Immutable
public final class AndroidSdkProvider implements TransitiveInfoProvider {
  private final Artifact frameworkAidl;
  private final Artifact androidJar;
  private final Artifact shrinkedAndroidJar;
  private final Artifact annotationsJar;
  private final Artifact mainDexClasses;
  private final FilesToRunProvider adb;
  private final FilesToRunProvider dx;
  private final FilesToRunProvider mainDexListCreator;
  private final FilesToRunProvider aidl;
  private final FilesToRunProvider aapt;
  private final FilesToRunProvider apkBuilder;
  private final FilesToRunProvider proguard;
  private final FilesToRunProvider zipalign;

  public AndroidSdkProvider(
      Artifact frameworkAidl, Artifact androidJar, Artifact shrinkedAndroidJar,
      Artifact annotationsJar, Artifact mainDexClasses,
      FilesToRunProvider adb, FilesToRunProvider dx,
      FilesToRunProvider mainDexListCreator,
      FilesToRunProvider aidl, FilesToRunProvider aapt, FilesToRunProvider apkBuilder,
      FilesToRunProvider proguard, FilesToRunProvider zipalign) {
    this.frameworkAidl = frameworkAidl;
    this.androidJar = androidJar;
    this.shrinkedAndroidJar = shrinkedAndroidJar;
    this.annotationsJar = annotationsJar;
    this.mainDexClasses = mainDexClasses;
    this.adb = adb;
    this.dx = dx;
    this.mainDexListCreator = mainDexListCreator;
    this.aidl = aidl;
    this.aapt = aapt;
    this.apkBuilder = apkBuilder;
    this.proguard = proguard;
    this.zipalign = zipalign;
  }

  public Artifact getFrameworkAidl() {
    return frameworkAidl;
  }

  public Artifact getAndroidJar() {
    return androidJar;
  }

  public Artifact getShrinkedAndroidJar() {
    return shrinkedAndroidJar;
  }

  public Artifact getAnnotationsJar() {
    return annotationsJar;
  }

  public Artifact getMainDexClasses() {
    return mainDexClasses;
  }

  public FilesToRunProvider getAdb() {
    return adb;
  }

  public FilesToRunProvider getDx() {
    return dx;
  }

  public FilesToRunProvider getMainDexListCreator() {
    return mainDexListCreator;
  }

  public FilesToRunProvider getAidl() {
    return aidl;
  }

  public FilesToRunProvider getAapt() {
    return aapt;
  }

  public FilesToRunProvider getApkBuilder() {
    return apkBuilder;
  }

  public FilesToRunProvider getProguard() {
    return proguard;
  }

  public FilesToRunProvider getZipalign() {
    return zipalign;
  }
}
