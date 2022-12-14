/**
 * The MIT License
 * Copyright (c) 2012 Graylog, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.graylog2.plugin.rest;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.testng.annotations.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class JsonProcessingExceptionMapperTest {

    @Test
    public void testToResponse() throws Exception {
        ExceptionMapper<JsonProcessingException> mapper = new JsonProcessingExceptionMapper();

        Response response = mapper.toResponse(new JsonMappingException("Boom!", JsonLocation.NA, new RuntimeException("rootCause")));

        assertEquals(response.getStatusInfo(), Response.Status.BAD_REQUEST);
        assertEquals(response.getMediaType(), MediaType.APPLICATION_JSON_TYPE);
        assertTrue(response.hasEntity());
        assertTrue(response.getEntity() instanceof ApiError);

        final ApiError responseEntity = (ApiError)response.getEntity();
        assertTrue(responseEntity.message.startsWith("Boom!"));
    }
}