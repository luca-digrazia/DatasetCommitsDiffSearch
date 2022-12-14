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

package com.google.devtools.build.lib.skyframe;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.analysis.platform.DeclaredToolchainInfo;
import com.google.devtools.build.lib.analysis.platform.PlatformInfo;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.packages.NoSuchThingException;
import com.google.devtools.build.lib.skyframe.ConfiguredTargetFunction.ConfiguredValueCreationException;
import com.google.devtools.build.lib.skyframe.RegisteredToolchainsFunction.InvalidTargetException;
import com.google.devtools.build.lib.skyframe.ToolchainResolutionValue.ToolchainResolutionKey;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyFunctionException;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;
import javax.annotation.Nullable;

/** {@link SkyFunction} which performs toolchain resolution for a class of rules. */
public class ToolchainResolutionFunction implements SkyFunction {

  @Nullable
  @Override
  public SkyValue compute(SkyKey skyKey, Environment env)
      throws SkyFunctionException, InterruptedException {
    ToolchainResolutionKey key = (ToolchainResolutionKey) skyKey.argument();

    // Get all toolchains.
    RegisteredToolchainsValue toolchains;
    try {
      toolchains =
          (RegisteredToolchainsValue)
              env.getValueOrThrow(
                  RegisteredToolchainsValue.key(key.configuration()),
                  ConfiguredValueCreationException.class,
                  InvalidTargetException.class,
                  EvalException.class);
      if (toolchains == null) {
        return null;
      }
    } catch (ConfiguredValueCreationException e) {
      throw new ToolchainResolutionFunctionException(e);
    } catch (InvalidTargetException e) {
      throw new ToolchainResolutionFunctionException(e);
    } catch (EvalException e) {
      throw new ToolchainResolutionFunctionException(e);
    }

    // Find the right one.
    DeclaredToolchainInfo toolchain =
        resolveConstraints(
            key.toolchainType(),
            key.targetPlatform(),
            key.execPlatform(),
            toolchains.registeredToolchains());
    return ToolchainResolutionValue.create(toolchain.toolchainLabel());
  }

  // TODO(katre): Implement real resolution.
  private DeclaredToolchainInfo resolveConstraints(
      Label toolchainType,
      PlatformInfo targetPlatform,
      PlatformInfo execPlatform,
      ImmutableList<DeclaredToolchainInfo> toolchains)
      throws ToolchainResolutionFunctionException {
    for (DeclaredToolchainInfo toolchain : toolchains) {
      if (toolchain.toolchainType().equals(toolchainType)) {
        return toolchain;
      }
    }
    throw new ToolchainResolutionFunctionException(new NoToolchainFoundException(toolchainType));
  }

  @Nullable
  @Override
  public String extractTag(SkyKey skyKey) {
    return null;
  }

  /** Used to indicate that a toolchain was not found for the current request. */
  public static final class NoToolchainFoundException extends NoSuchThingException {
    private final Label missingToolchainType;

    public NoToolchainFoundException(Label missingToolchainType) {
      super(String.format("no matching toolchain found for %s", missingToolchainType));
      this.missingToolchainType = missingToolchainType;
    }

    public Label missingToolchainType() {
      return missingToolchainType;
    }
  }

  /** Used to indicate errors during the computation of an {@link ToolchainResolutionValue}. */
  private static final class ToolchainResolutionFunctionException extends SkyFunctionException {
    public ToolchainResolutionFunctionException(NoToolchainFoundException e) {
      super(e, Transience.PERSISTENT);
    }

    public ToolchainResolutionFunctionException(ConfiguredValueCreationException e) {
      super(e, Transience.PERSISTENT);
    }

    public ToolchainResolutionFunctionException(InvalidTargetException e) {
      super(e, Transience.PERSISTENT);
    }

    public ToolchainResolutionFunctionException(EvalException e) {
      super(e, Transience.PERSISTENT);
    }
  }
}
