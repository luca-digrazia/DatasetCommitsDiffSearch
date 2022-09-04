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
package org.androidannotations.rest.spring.test;

import static org.fest.assertions.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.springframework.web.client.ResponseErrorHandler;

@RunWith(RobolectricTestRunner.class)
public class RestResponseErrorHandlerTest {

	@Test
	public void testSameAsSingletonBeanResponseErrorHandler() {
		RestWithSingletonBeanResponseErrorHandler restClient = new RestWithSingletonBeanResponseErrorHandler_(Robolectric.application);
		ResponseErrorHandler errorHandler = restClient.getRestTemplate().getErrorHandler();
		MyResponseErrorHandlerBean errorHandlerBean = MyResponseErrorHandlerBean_.getInstance_(Robolectric.application);

		assertThat(errorHandler).isSameAs(errorHandlerBean);
	}

	@Test
	public void testInstanceCreatedFromNonBeanClassAsResponseErrorHandler() {
		new RestWithSimpleClassResponseErrorHandler_(Robolectric.application);

		assertThat(MyResponseErrorHandler.instanceCreated).isTrue();
	}

}
