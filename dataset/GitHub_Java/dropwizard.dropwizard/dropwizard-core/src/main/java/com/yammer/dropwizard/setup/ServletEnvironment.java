package com.yammer.dropwizard.setup;

import com.yammer.dropwizard.jetty.NonblockingServletHolder;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import java.util.EventListener;

import static com.google.common.base.Preconditions.checkNotNull;

public class ServletEnvironment {
    private final ServletContextHandler handler;

    public ServletEnvironment(ServletContextHandler handler) {
        this.handler = handler;
    }

    /**
     * Add a servlet instance.
     *
     * @param servlet    the servlet instance
     * @param urlPattern the URL pattern for requests that should be handled by {@code servlet}
     * @return a {@link ServletBuilder} instance allowing for further
     *         configuration
     */
    public ServletBuilder addServlet(Servlet servlet,
                                     String urlPattern) {
        final ServletHolder holder = new NonblockingServletHolder(checkNotNull(servlet));
        final ServletBuilder servletConfig = new ServletBuilder(holder, handler);
        servletConfig.addUrlPattern(checkNotNull(urlPattern));
        return servletConfig;
    }

    /**
     * Add a servlet class.
     *
     * @param klass      the servlet class
     * @param urlPattern the URL pattern for requests that should be handled by instances of {@code
     *                   klass}
     * @return a {@link ServletBuilder} instance allowing for further configuration
     */
    public ServletBuilder addServlet(Class<? extends Servlet> klass,
                                     String urlPattern) {
        final ServletHolder holder = new ServletHolder(checkNotNull(klass));
        final ServletBuilder servletConfig = new ServletBuilder(holder, handler);
        servletConfig.addUrlPattern(checkNotNull(urlPattern));
        return servletConfig;
    }

    /**
     * Add a filter instance.
     *
     * @param filter     the filter instance
     * @param urlPattern the URL pattern for requests that should be handled by {@code filter}
     * @return a {@link FilterBuilder} instance allowing for further
     *         configuration
     */
    public FilterBuilder addFilter(Filter filter,
                                   String urlPattern) {
        final FilterHolder holder = new FilterHolder(checkNotNull(filter));
        final FilterBuilder filterConfig = new FilterBuilder(holder, handler);
        filterConfig.addUrlPattern(checkNotNull(urlPattern));
        return filterConfig;
    }

    /**
     * Add a filter class.
     *
     * @param klass      the filter class
     * @param urlPattern the URL pattern for requests that should be handled by instances of {@code
     *                   klass}
     * @return a {@link FilterBuilder} instance allowing for further configuration
     */
    public FilterBuilder addFilter(Class<? extends Filter> klass,
                                   String urlPattern) {
        final FilterHolder holder = new FilterHolder(checkNotNull(klass));
        final FilterBuilder filterConfig = new FilterBuilder(holder, handler);
        filterConfig.addUrlPattern(checkNotNull(urlPattern));
        return filterConfig;
    }

    /**
     * Add one or more servlet event listeners.
     *
     * @param listeners one or more listener instances that implement {@link
     *                  javax.servlet.ServletContextListener}, {@link javax.servlet.ServletContextAttributeListener},
     *                  {@link javax.servlet.ServletRequestListener} or {@link
     *                  javax.servlet.ServletRequestAttributeListener}
     */
    public void addServletListeners(EventListener... listeners) {
        for (EventListener listener : listeners) {
            handler.addEventListener(listener);
        }
    }
}
