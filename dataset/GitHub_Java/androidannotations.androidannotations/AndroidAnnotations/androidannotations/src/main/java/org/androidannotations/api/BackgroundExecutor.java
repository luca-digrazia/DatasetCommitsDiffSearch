/**
 * Copyright (C) 2010-2013 eBusiness Information, Excilys Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.androidannotations.api;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BackgroundExecutor {

	private static Executor executor = Executors.newScheduledThreadPool(2 * Runtime.getRuntime().availableProcessors());

	/**
	 * Execute a task after the given delay.
	 * 
	 * @param runnable
	 *            the task to execute
	 * @param delay
	 *            the time from now to delay execution, in milliseconds
	 * @throws IllegalArgumentException
	 *             if <code>delay</code> is strictly positive and the current
	 *             executor does not support scheduling (if
	 *             {@link #setExecutor(Executor)} has been called with such an
	 *             executor)
	 */
	public static void execute(Runnable runnable, long delay) {
		if (delay > 0) {
			if (!(executor instanceof ScheduledExecutorService)) {
				throw new IllegalArgumentException("The executor set does not support scheduling");
			}
			((ScheduledExecutorService) executor).schedule(runnable, delay, TimeUnit.MILLISECONDS);
		} else {
			/* execute now */
			executor.execute(runnable);
		}
	}

	/**
	 * Execute a task.
	 * 
	 * @param runnable
	 *            the task to execute
	 */
	public static void execute(Runnable runnable) {
		execute(runnable, 0);
	}

	/**
	 * Change the executor.
	 * 
	 * Note that if the given executor is not a {@link ScheduledExecutorService}
	 * then executing a task after a delay will not be supported anymore.
	 * 
	 * @param executor
	 *            the new executor
	 */
	public static void setExecutor(Executor executor) {
		BackgroundExecutor.executor = executor;
	}

}
