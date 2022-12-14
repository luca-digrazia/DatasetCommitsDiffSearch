package com.yammer.dropwizard.tasks.tests;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.yammer.dropwizard.tasks.Task;
import com.yammer.dropwizard.tasks.TaskServlet;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.PrintWriter;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class TaskServletTest {
    private final Task gc = mock(Task.class);
    private final Task clearCache = mock(Task.class);

    {
        when(gc.getName()).thenReturn("gc");
        when(clearCache.getName()).thenReturn("clear-cache");
    }

    private final TaskServlet servlet = new TaskServlet(ImmutableList.of(gc, clearCache));
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @Test
    public void returnsA404WhenNotFound() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/test");

        servlet.service(request, response);

        verify(response).sendError(404);
    }

    @Test
    public void runsATestWhenFound() throws Exception {
        final PrintWriter output = mock(PrintWriter.class);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/gc");
        when(request.getParameterNames()).thenReturn(Collections.enumeration(ImmutableList.of()));
        when(response.getWriter()).thenReturn(output);

        servlet.service(request, response);

        verify(gc).execute(ImmutableMultimap.<String, String>of(), output);
    }

    @Test
    public void passesQueryStringParamsAlong() throws Exception {
        final PrintWriter output = mock(PrintWriter.class);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/gc");
        when(request.getParameterNames()).thenReturn(Collections.enumeration(ImmutableList.of("runs")));
        when(request.getParameterValues("runs")).thenReturn(new String[]{"1"});
        when(response.getWriter()).thenReturn(output);

        servlet.service(request, response);

        verify(gc).execute(ImmutableMultimap.of("runs", "1"), output);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void returnsA500OnExceptions() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/gc");
        when(request.getParameterNames()).thenReturn(Collections.enumeration(ImmutableList.of()));

        final RuntimeException ex = new RuntimeException("whoops");
        
        doThrow(ex).when(gc).execute(any(ImmutableMultimap.class), any(PrintWriter.class));
        
        servlet.service(request, response);

        verify(response).sendError(500);
    }
}
