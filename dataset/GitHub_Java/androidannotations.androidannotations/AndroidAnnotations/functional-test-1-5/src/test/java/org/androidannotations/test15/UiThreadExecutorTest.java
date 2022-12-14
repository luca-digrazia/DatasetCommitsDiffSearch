/**
 * Copyright (C) 2010-2015 eBusiness Information, Excilys Group
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
package org.androidannotations.test15;

import static org.fest.reflect.core.Reflection.staticField;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.androidannotations.api.UiThreadExecutor;
import org.fest.reflect.reference.TypeRef;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class UiThreadExecutorTest {

	@Test
	public void oneTaskTest() throws Exception {
		final CountDownLatch latch = new CountDownLatch(1);
		UiThreadExecutor.runTask("test", new Runnable() {
			@Override
			public void run() {
				UiThreadExecutor.done("test", this);
				latch.countDown();
			}
		}, 0);
		await(latch);
		assertTrue("The task is leaked", getTasks().isEmpty());
	}

	@Test
	public void oneTaskInThreadTest() throws Exception {
		final CountDownLatch taskStartedLatch = new CountDownLatch(1);
		final CountDownLatch taskFinishedLatch = new CountDownLatch(1);
		new Thread() {
			@Override
			public void run() {
				UiThreadExecutor.runTask("test", new Runnable() {
					@Override
					public void run() {
						await(taskStartedLatch);
						assertFalse("There is no tasks in map", getTasks().isEmpty());
						UiThreadExecutor.done("test", this);
						taskFinishedLatch.countDown();
					}
				}, 0);
				taskStartedLatch.countDown();
				await(taskFinishedLatch);
				assertTrue("The task is leaked", getTasks().isEmpty());
			}
		}.start();
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Execution hanged up");
			}
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private ConcurrentHashMap<String, Set<Runnable>> getTasks() {
		return staticField("TASKS").ofType(new TypeRef<ConcurrentHashMap<String, Set<Runnable>>>() {
		}).in(UiThreadExecutor.class).get();
	}

}
