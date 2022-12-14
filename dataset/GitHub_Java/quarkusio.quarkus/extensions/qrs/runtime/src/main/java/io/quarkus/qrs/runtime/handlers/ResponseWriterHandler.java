package io.quarkus.qrs.runtime.handlers;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map.Entry;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import io.quarkus.qrs.runtime.core.JSONBMessageBodyWriter;
import io.quarkus.qrs.runtime.core.RequestContext;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;

/**
 * Our job is to write a Response
 */
public class ResponseWriterHandler implements RestHandler {

    private final JSONBMessageBodyWriter writer = new JSONBMessageBodyWriter();

    @Override
    public void handle(RequestContext requestContext) throws Exception {
        HttpServerResponse vertxResponse = requestContext.getContext().response();

        // has been converted in ResponseHandler
        Response response = requestContext.getResponse();
        Object entity = response.getEntity();
        vertxResponse.setStatusCode(response.getStatus());
        vertxResponse.setStatusMessage(response.getStatusInfo().getReasonPhrase());
        MultivaluedMap<String, String> headers = response.getStringHeaders();
        for (Entry<String, List<String>> entry : headers.entrySet()) {
            vertxResponse.putHeader(entry.getKey(), entry.getValue());
        }

        if (entity != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.writeTo(entity, null, null, null, response.getMediaType(), null, baos);
            requestContext.getContext().response().end(Buffer.buffer(baos.toByteArray()));
        } else {
            requestContext.getContext().response().end();
        }
    }
}
