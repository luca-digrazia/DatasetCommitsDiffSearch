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
package com.google.devtools.build.lib.exec;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.build.lib.actions.ActionExecutionContext;
import com.google.devtools.build.lib.actions.ExecException;
import com.google.devtools.build.lib.actions.FutureSpawn;
import com.google.devtools.build.lib.actions.Spawn;
import com.google.devtools.build.lib.actions.SpawnActionContext;
import com.google.devtools.build.lib.actions.SpawnResult;
import com.google.devtools.build.lib.events.EventHandler;
import java.util.List;

/** Proxy that looks up the right SpawnActionContext for a spawn during {@link #exec}. */
public final class ProxySpawnActionContext implements SpawnActionContext {

  private final SpawnActionContextMaps spawnActionContextMaps;

  /**
   * Creates a new {@link ProxySpawnActionContext}.
   *
   * @param spawnActionContextMaps The {@link SpawnActionContextMaps} to use to decide which {@link
   *     SpawnActionContext} should execute a given {@link Spawn} during {@link #exec}.
   */
  public ProxySpawnActionContext(SpawnActionContextMaps spawnActionContextMaps) {
    this.spawnActionContextMaps = spawnActionContextMaps;
  }

  @Override
  public List<SpawnResult> exec(Spawn spawn, ActionExecutionContext actionExecutionContext)
      throws ExecException, InterruptedException {
    return resolve(spawn, actionExecutionContext.getEventHandler())
        .exec(spawn, actionExecutionContext);
  }

  @Override
  public FutureSpawn execMaybeAsync(Spawn spawn, ActionExecutionContext actionExecutionContext)
      throws ExecException, InterruptedException {
    return resolve(spawn, actionExecutionContext.getEventHandler())
        .execMaybeAsync(spawn, actionExecutionContext);
  }

  /**
   * Returns the {@link SpawnActionContext} that should be used to execute the given spawn.
   *
   * @param spawn The spawn for which the correct {@link SpawnActionContext} should be determined.
   * @param eventHandler An event handler that can be used to print messages while resolving the
   *     correct {@link SpawnActionContext} for the given spawn.
   */
  @VisibleForTesting
  public SpawnActionContext resolve(Spawn spawn, EventHandler eventHandler) {
    return spawnActionContextMaps.getSpawnActionContext(spawn, eventHandler);
  }
}
