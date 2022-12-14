// Copyright 2019 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.android.desugar.nest.functional;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.flags.Flag;
import com.google.common.flags.FlagSpec;
import com.google.common.flags.Flags;
import com.google.devtools.build.android.desugar.DesugarRule;
import com.google.devtools.build.android.desugar.DesugarRule.LoadClass;
import com.google.testing.junit.junit4.api.TestArgs;
import com.google.testing.testsize.MediumTest;
import com.google.testing.testsize.MediumTestAttribute;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for accessing private methods from another class within a nest. */
@RunWith(JUnit4.class)
@MediumTest(MediumTestAttribute.FILE)
public final class MethodInNestTest {

  @FlagSpec(
      help = "The root directory of source root directory for desugar testing.",
      name = "test_jar_method")
  private static final Flag<String> testJar = Flag.nullString();

  @Rule
  public final DesugarRule desugarRule =
      DesugarRule.builder(this, MethodHandles.lookup())
          .addRuntimeInputs(testJar.getNonNull())
          .addCommandOptions("desugar_nest_based_private_access", "true")
          .build();

  private final MethodHandles.Lookup lookup = MethodHandles.lookup();

  @LoadClass(value = "simpleunit.method.MethodNest$MethodOwnerMate")
  private Class<?> mate;

  @LoadClass("simpleunit.method.MethodNest$SubMate")
  private Class<?> subClassMate;

  @LoadClass("simpleunit.method.MethodNest")
  private Class<?> invoker;

  private Object mateInstance;
  private Object invokerInstance;

  @BeforeClass
  public static void setUpFlags() throws Exception {
    Flags.parse(TestArgs.get());
  }

  @Before
  public void loadClassesInNest() throws Exception {
    mateInstance = mate.getConstructor().newInstance();
    invokerInstance = invoker.getDeclaredConstructor().newInstance();
  }

  @Test
  public void methodBridgeGeneration() throws Exception {
    List<String> bridgeMethodNames =
        Arrays.stream(mate.getDeclaredMethods())
            .map(Method::getName)
            .filter(name -> !name.startsWith("$jacoco"))
            .collect(Collectors.toList());
    assertThat(bridgeMethodNames)
        .containsExactly(
            "staticMethod",
            "instanceMethod",
            "privateStaticMethod",
            "privateInstanceMethod",
            "inClassBoundStaticMethod",
            "inClassBoundInstanceMethod",
            "privateStaticMethod$bridge",
            "privateInstanceMethod$bridge");
  }

  @Test
  public void invokePrivateStaticMethod_staticInitializer() throws Throwable {
    MethodHandle lookupGetter =
        lookup.findStaticGetter(invoker, "populatedFromInvokePrivateStaticMethod", long.class);
    assertThat(lookupGetter.invoke()).isEqualTo(385L); // 128L + 256 + 1
  }

  @Test
  public void invokePrivateInstanceMethod_instanceInitializer() throws Throwable {
    MethodHandle lookupGetter =
        lookup.findGetter(invoker, "populatedFromInvokePrivateInstanceMethod", long.class);
    assertThat(lookupGetter.invoke(invokerInstance)).isEqualTo(768L); // 128L + 256 + 1
  }

  @Test
  public void invokePrivateStaticMethod() throws Throwable {
    long x = 1L;
    int y = 2;

    MethodHandle methodHandle =
        lookup.findStatic(
            invoker,
            "invokePrivateStaticMethod",
            MethodType.methodType(long.class, long.class, int.class));

    long result = (long) methodHandle.invokeExact(x, y);
    assertThat(result).isEqualTo(x + y);
  }

  @Test
  public void invokePrivateInstanceMethod() throws Throwable {
    long x = 2L;
    int y = 3;

    MethodHandle methodHandle =
        lookup.findStatic(
            invoker,
            "invokePrivateInstanceMethod",
            MethodType.methodType(long.class, mate, long.class, int.class));
    long result = (long) methodHandle.invoke(mateInstance, x, y);
    assertThat(result).isEqualTo(x + y);
  }

  @Test
  public void invokeStaticMethod() throws Exception {
    long x = 3L;
    int y = 4;
    assertThat(invoker.getMethod("invokeStaticMethod", long.class, int.class).invoke(null, x, y))
        .isEqualTo(x + y);
  }

  @Test
  public void invokeInstanceMethod() throws Exception {
    long x = 4L;
    int y = 5;

    assertThat(
            invoker
                .getMethod("invokeInstanceMethod", mate, long.class, int.class)
                .invoke(null, mateInstance, x, y))
        .isEqualTo(x + y);
  }

  @Test
  public void invokeSuperAccessPrivateInstanceMethod() throws Exception {
    assertThat(
            invoker
                .getDeclaredMethod(
                    "invokeSuperAccessPrivateInstanceMethod", subClassMate, long.class, int.class)
                .invoke(null, subClassMate.getConstructor().newInstance(), 7L, 8))
        .isEqualTo(16L); // 15 + 1
  }

  @Test
  public void invokeCastAccessPrivateInstanceMethod() throws Exception {
    assertThat(
            invoker
                .getDeclaredMethod(
                    "invokeCastAccessPrivateInstanceMethod", subClassMate, long.class, int.class)
                .invoke(null, subClassMate.getConstructor().newInstance(), 9L, 10))
        .isEqualTo(21L); // 19 + 2
  }
}
