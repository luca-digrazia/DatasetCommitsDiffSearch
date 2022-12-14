/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.shared.rest;

import org.glassfish.grizzly.http.server.Response;
import org.graylog2.shared.security.ShiroSecurityContext;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.util.Date;

import static java.util.Objects.requireNonNull;

public class RestAccessLogFilter implements ContainerResponseFilter {
    private static final Logger LOG = LoggerFactory.getLogger("org.graylog2.rest.accesslog");

    private final Response response;

    public RestAccessLogFilter(@Context Response response) {
        this.response = requireNonNull(response);
    }

    private String getUsernameFromSession(String sessionID) {
    	String result = "anonymous";
    	if (sessionID != null) {
    	    final DefaultSecurityManager securityManager = (DefaultSecurityManager) SecurityUtils.getSecurityManager();
            Subject.Builder builder = new Subject.Builder(securityManager);
            builder.sessionId(sessionID);
            Subject subject = builder.buildSubject();
    	    result = subject.getPrincipal().toString();
    	}
    	return result;
    
    }
    
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        if (LOG.isDebugEnabled()) {
            try {
                final String rawQuery = requestContext.getUriInfo().getRequestUri().getRawQuery();
                final SecurityContext securityContext = requestContext.getSecurityContext();
                final String sessionID = securityContext instanceof ShiroSecurityContext ?
                        ((ShiroSecurityContext) securityContext).getUsername() : null;
                final String userName = getUsernameFromSession(sessionID);
                final Date requestDate = requestContext.getDate();

                LOG.debug("{} {} [{}] \"{} {}{}\" {} {} {}",
                        response.getRequest().getRemoteAddr(),
                        (userName == null ? "-" : userName),
                        (requestDate == null ? "-" : requestDate),
                        requestContext.getMethod(),
                        requestContext.getUriInfo().getPath(),
                        (rawQuery == null ? "" : "?" + rawQuery),
                        requestContext.getHeaderString(HttpHeaders.USER_AGENT),
                        responseContext.getStatus(),
                        responseContext.getLength());
            } catch (Exception e) {
                LOG.error("Error while processing REST API access log", e);
            }
        }
    }
}
