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
package org.graylog2.restclient.models;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.graylog2.restclient.lib.APIException;
import org.graylog2.restclient.lib.ApiClient;
import org.graylog2.restclient.models.alerts.Alert;
import org.graylog2.restclient.models.api.requests.streams.CreateStreamRequest;
import org.graylog2.restclient.models.api.requests.streams.TestMatchRequest;
import org.graylog2.restclient.models.api.responses.EmptyResponse;
import org.graylog2.restclient.models.api.responses.streams.CreateStreamResponse;
import org.graylog2.restclient.models.api.responses.streams.GetStreamsResponse;
import org.graylog2.restclient.models.api.responses.streams.StreamSummaryResponse;
import org.graylog2.restclient.models.api.responses.streams.TestMatchResponse;
import org.graylog2.restclient.models.api.results.StreamsResult;
import org.graylog2.restroutes.generated.StreamResource;
import org.graylog2.restroutes.generated.routes;
import play.mvc.Http;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class StreamService {

    private final ApiClient api;
    private final Stream.Factory streamFactory;
    private final StreamResource resource = routes.StreamResource();

    @Inject
    private StreamService(ApiClient api, Stream.Factory streamFactory) {
        this.api = api;
        this.streamFactory = streamFactory;
    }

    public List<Stream> all() throws IOException, APIException {
        GetStreamsResponse r = null;
        r = api.path(resource.get(), GetStreamsResponse.class).execute();

        List<Stream> streams = Lists.newArrayList();

        for (StreamSummaryResponse stream : r.streams) {
            streams.add(streamFactory.fromSummaryResponse(stream));
        }

        //return new StreamsResult(r.total, r.streams);
        return streams;
    }

    public List<Stream> allEnabled() throws IOException, APIException {
        GetStreamsResponse r = null;
        r = api.path(resource.getEnabled(), GetStreamsResponse.class).execute();

        List<Stream> streams = Lists.newArrayList();

        for (StreamSummaryResponse stream : r.streams) {
            streams.add(streamFactory.fromSummaryResponse(stream));
        }

        return streams;
    }

    public Stream get(String streamId) throws IOException, APIException {
        StreamSummaryResponse streamResponse = null;
        streamResponse = api.path(resource.get(streamId), StreamSummaryResponse.class).execute();

        return streamFactory.fromSummaryResponse(streamResponse);
    }

    public String create(CreateStreamRequest request) throws APIException, IOException {
        CreateStreamResponse csr = api.path(resource.create(), CreateStreamResponse.class)
                .body(request).expect(Http.Status.CREATED).execute();
        return csr.streamId;
    }

    public void update(String streamId, CreateStreamRequest request) throws APIException, IOException {
        api.path(resource.update(streamId)).body(request).expect(Http.Status.OK).execute();
    }

    public void delete(String streamId) throws APIException, IOException {
        api.path(resource.delete(streamId)).expect(Http.Status.NO_CONTENT).execute();
    }

    public void pause(String streamId) throws APIException, IOException {
        api.path(resource.pause(streamId)).expect(Http.Status.OK).execute();
    }

    public void resume(String streamId) throws APIException, IOException {
        api.path(resource.resume(streamId)).expect(Http.Status.OK).execute();
    }

    public TestMatchResponse testMatch(String streamId, TestMatchRequest request) throws APIException, IOException {
        TestMatchResponse testMatchResponse = null;
        testMatchResponse = api.path(resource.testMatch(streamId), TestMatchResponse.class)
                .body(request).expect(Http.Status.OK).execute();
        return testMatchResponse;
    }

    public String cloneStream(String streamId, CreateStreamRequest request) throws APIException, IOException {
        CreateStreamResponse csr = api.path(resource.cloneStream(streamId), CreateStreamResponse.class)
                .body(request).expect(Http.Status.CREATED).execute();
        return csr.streamId;
    }

    public void sendDummyAlert(String streamId) throws APIException, IOException {
        api.path(routes.StreamAlertReceiverResource().sendDummyAlert(streamId))
                .expect(Http.Status.NO_CONTENT).execute();
    }

    public List<Alert> allowedAlertsSince(int since) throws IOException, APIException {
        List<Alert> alerts = Lists.newArrayList();

        for(Stream stream : all()) {
            alerts.addAll(stream.getAlertsSince(since));
        }

        return alerts;
    }
}
