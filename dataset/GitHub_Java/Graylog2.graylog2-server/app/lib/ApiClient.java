/*
 * Copyright 2013 TORCH UG
 *
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 */
package lib;

import com.google.inject.ImplementedBy;
import com.ning.http.client.AsyncHttpClient;
import models.api.responses.EmptyResponse;

/*
  Note: This is an interface to break the cyclic dependency between ServerNodes and ApiClientImpl.
  Guice will insert a proxy to break the cycle.
 */
@ImplementedBy(ApiClientImpl.class)
public interface ApiClient {
    String ERROR_MSG_IO = "Could not connect to graylog2-server. Please make sure that it is running and you configured the correct REST URI.";
    String ERROR_MSG_NODE_NOT_FOUND = "Node not found.";

    void start();

    void stop();

    // default visibility for access from tests (overrides the effects of initialize())
    void setHttpClient(AsyncHttpClient client);

    <T> ApiClientImpl.ApiRequestBuilder<T> get(Class<T> responseClass);

    <T> ApiClientImpl.ApiRequestBuilder<T> post(Class<T> responseClass);

    ApiClientImpl.ApiRequestBuilder<EmptyResponse> post();

    <T> ApiClientImpl.ApiRequestBuilder<T> put(Class<T> responseClass);

    ApiClientImpl.ApiRequestBuilder<EmptyResponse> put();

    <T> ApiClientImpl.ApiRequestBuilder<T> delete(Class<T> responseClass);

    ApiClientImpl.ApiRequestBuilder<EmptyResponse> delete();

    public enum Method {
        GET,
        POST,
        PUT,
        DELETE
    }
}
