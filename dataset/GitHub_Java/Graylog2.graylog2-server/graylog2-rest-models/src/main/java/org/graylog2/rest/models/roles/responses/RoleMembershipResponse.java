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
package org.graylog2.rest.models.roles.responses;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.graylog2.rest.models.users.responses.UserSummary;

import java.util.Collection;

@AutoValue
public abstract class RoleMembershipResponse {

    @JsonProperty
    public abstract String role();

    @JsonProperty
    public abstract Collection<UserSummary> users();

    @JsonCreator
    public static RoleMembershipResponse create(@JsonProperty("role") String roleName, @JsonProperty("users") Collection<UserSummary> users) {
        return new AutoValue_RoleMembershipResponse(roleName, users);
    }
}
