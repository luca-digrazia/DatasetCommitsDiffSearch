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

import org.apache.shiro.authz.Permission;

import java.util.Set;

/**
 * Resolves a user name to specific permissions based on grants.
 */
public interface GrantPermissionResolver {
    /**
     * Returns resolved permissions for the given user name.
     *
     * @param userName the user name
     * @return the resolved permissions
     */
    Set<Permission> resolvePermissionsForUser(String userName);
}
