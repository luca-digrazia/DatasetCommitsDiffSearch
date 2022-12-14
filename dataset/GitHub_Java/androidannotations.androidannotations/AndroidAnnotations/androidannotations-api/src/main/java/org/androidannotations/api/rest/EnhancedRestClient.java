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
package org.androidannotations.api.rest;

import org.springframework.web.client.RestTemplate;

/**
 * A @Rest interface implementing this interface will automatically have the
 * implementations of these methods generated.
 */
public interface EnhancedRestClient {

	/**
	 * Gets the root URL for the rest service.
	 * 
	 * @return String
	 */
	String getRootUrl();

	/**
	 * Sets the root URL for the rest service.
	 * 
	 * @param rootUrl
	 */
	void setRootUrl(String rootUrl);

	/**
	 * Gets the rest template used by the rest service implementation.
	 * 
	 * @return RestTemplate
	 */
	RestTemplate getRestTemplate();

	/**
	 * Sets the rest template used by the rest service implementation.
	 * 
	 * @param rt
	 */
	void setRestTemplate(RestTemplate rt);

	/**
	 * Sets the error handler called when a rest error occurs.
	 * 
	 * @param handler
	 */
	void setRestErrorHandler(RestErrorHandler handler);

	String getCookie(String c);

	void setHeader(String h, String v);

	String getHeader(String h);

	void setCookie(String c, String v);
}
