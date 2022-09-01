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
package com.google.devtools.build.importdeps;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Representation of a class. It maintains the internal name, declared members, as well as the super
 * classes.
 */
@AutoValue
public abstract class ClassInfo {

  public static ClassInfo create(
      String internalName,
      ImmutableList<ClassInfo> superClasses,
      ImmutableSet<MemberInfo> declaredMembers) {
    return new AutoValue_ClassInfo(internalName, superClasses, declaredMembers);
  }

  public abstract String internalName();

  /**
   * Returns all the available super classes. There may be more super classes (super class or
   * interfaces), but those do not exist on the classpath.
   */
  public abstract ImmutableList<ClassInfo> superClasses();

  public abstract ImmutableSet<MemberInfo> declaredMembers();

  public final boolean containsMember(MemberInfo memberInfo) {
    if (declaredMembers().contains(memberInfo)) {
      return true;
    }
    for (ClassInfo superClass : superClasses()) {
      if (superClass.containsMember(memberInfo)) {
        return true;
      }
    }
    return false;
  }

  /** A member is either a method or a field. */
  @AutoValue
  public abstract static class MemberInfo implements Comparable<MemberInfo> {

    public static MemberInfo create(String owner, String memberName, String descriptor) {
      checkArgument(!Strings.isNullOrEmpty(owner), "Empty owner name: %s", owner);
      checkArgument(!Strings.isNullOrEmpty(memberName), "Empty method name: %s", memberName);
      checkArgument(!Strings.isNullOrEmpty(descriptor), "Empty descriptor: %s", descriptor);
      return new AutoValue_ClassInfo_MemberInfo(owner, memberName, descriptor);
    }

    /** The declaring class of this member. */
    public abstract String owner();

    /** The name of the member. */
    public abstract String memberName();

    /** The descriptor of the member. */
    public abstract String descriptor();

    @Memoized
    @Override
    public abstract int hashCode();

    @Override
    public int compareTo(MemberInfo other) {
      return ComparisonChain.start()
          .compare(this.owner(), other.owner())
          .compare(this.memberName(), other.memberName())
          .compare(this.descriptor(), other.descriptor())
          .result();
    }
  }
}
