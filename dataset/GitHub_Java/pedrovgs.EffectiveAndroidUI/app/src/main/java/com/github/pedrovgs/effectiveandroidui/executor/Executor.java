/*
 * Copyright (C) 2014 Pedro Vicente Gómez Sánchez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.pedrovgs.effectiveandroidui.executor;

/**
 * Execute an Interactor. Executor implementation can be based on different frameworks or
 * techniques
 * of asynchronous execution, but every implementation will execute the Interactor out of the UI
 * thread.
 *
 * @author Pedro Vicente Gómez Sánchez
 */
public interface Executor {

  void run(final Interactor interactor);
}
