package io.dropwizard.jersey.filter;

import com.google.common.net.HttpHeaders;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CharsetUtf8FilterTest {

    @Mock
    private ContainerRequestContext request;

    @Mock
    private ContainerResponseContext response;

    private CharsetUtf8Filter charsetUtf8Filter = new CharsetUtf8Filter();

    @Test
    public void testSetsCharsetEncoding() throws Exception {
        when(response.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_TYPE);
        when(response.getHeaders()).thenReturn(headers);

        charsetUtf8Filter.filter(request, response);

        assertThat((MediaType) headers.getFirst(HttpHeaders.CONTENT_TYPE))
            .isEqualTo(MediaType.valueOf("application/json;charset=UTF-8"));
    }
}
