/*
 * Copyright 2013 TORCH UG
 *
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 */
package models;

import com.google.common.collect.Lists;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import lib.APIException;
import lib.ApiClient;
import models.api.requests.ChangeUserRequest;
import models.api.responses.system.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Http;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

public class User {
	private static final Logger log = LoggerFactory.getLogger(User.class);

    private final ApiClient api;
    @Deprecated
    private final String id;
    private final String name;
	private final String email;
	private final String fullName;
	private final List<String> permissions;

    private final String passwordHash;

    @AssistedInject
    public User(ApiClient api, @Assisted UserResponse ur, @Nullable @Assisted String passwordHash) {
        this(api, ur.id, ur.username, ur.email, ur.fullName, ur.permissions, passwordHash);
    }

	public User(ApiClient api, String id, String name, String email, String fullName, List<String> permissions, String passwordHash) {
        this.api = api;
        this.id = id;
        this.name = name;
		this.email = email;
		this.fullName = fullName;
		this.permissions = permissions;
        this.passwordHash = passwordHash;
    }

    public void update(ChangeUserRequest request) {
        try {
            api.put().path("/users/{0}", getName()).body(request).expect(Http.Status.NO_CONTENT).execute();
        } catch (APIException e) {
            log.error("Unable to update user", e);
        } catch (IOException e) {
            log.error("Unable to update user", e);
        }
    }
    @Deprecated
    public String getId() {
        return getName();
    }

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getFullName() {
		return fullName;
	}

	public List<String> getPermissions() {
        if (permissions == null) {
            return Lists.newArrayList();
        }
		return permissions;
	}

    public String getPasswordHash() {
        return passwordHash;
    }

    public interface Factory {
        User fromResponse(UserResponse ur, String passwordHash);
    }

}
