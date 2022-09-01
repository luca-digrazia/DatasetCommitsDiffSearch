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
package org.graylog2.shared.security;

import org.apache.shiro.authc.BearerToken;
import org.graylog2.audit.AuditActor;

public class TypedBearerToken extends BearerToken implements ActorAwareAuthenticationToken {
    private final AuditActor actor;
    private final String type;

    public TypedBearerToken(String token, AuditActor actor, String type) {
        super(token);
        this.actor = actor;
        this.type = type;
    }

    @Override
    public AuditActor getActor() {
         return actor;
    }

    public String getType() {
        return type;
    }
}
