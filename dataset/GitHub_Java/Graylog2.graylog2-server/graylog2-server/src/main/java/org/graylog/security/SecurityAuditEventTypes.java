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
package org.graylog.security;

import com.google.common.collect.ImmutableSet;
import org.graylog2.audit.PluginAuditEventTypes;

import java.util.Set;

public class SecurityAuditEventTypes implements PluginAuditEventTypes {
    public static final String NAMESPACE = "security:";
    public static final String NAMESPACE_AUTH_SERVICE = NAMESPACE + "auth-service:";

    public static final String SHARE_CREATE = NAMESPACE + "share:create";
    public static final String SHARE_UPDATE = NAMESPACE + "share:update";
    public static final String SHARE_DELETE = NAMESPACE + "share:delete";

    public static final String AUTH_SERVICE_GLOBAL_CONFIG_UPDATE = NAMESPACE_AUTH_SERVICE + "global-config:update";
    public static final String AUTH_SERVICE_BACKEND_CREATE = NAMESPACE_AUTH_SERVICE + "backend:create";
    public static final String AUTH_SERVICE_BACKEND_DELETE = NAMESPACE_AUTH_SERVICE + "backend:delete";
    public static final String AUTH_SERVICE_BACKEND_UPDATE = NAMESPACE_AUTH_SERVICE + "backend:update";

    private static final ImmutableSet<String> EVENT_TYPES = ImmutableSet.of(
            SHARE_CREATE,
            SHARE_UPDATE,
            SHARE_DELETE,
            AUTH_SERVICE_GLOBAL_CONFIG_UPDATE,
            AUTH_SERVICE_BACKEND_CREATE,
            AUTH_SERVICE_BACKEND_DELETE,
            AUTH_SERVICE_BACKEND_UPDATE
    );

    @Override
    public Set<String> auditEventTypes() {
        return EVENT_TYPES;
    }
}

