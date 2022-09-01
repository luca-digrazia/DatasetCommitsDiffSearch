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
package models.api.requests;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import models.SystemJob;
import models.User;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class SystemJobTriggerRequest extends ApiRequest {

    @SerializedName("job_name")
    public String jobName;

    @SerializedName("creator_user_id")
    public String creatorUserId;

    public SystemJobTriggerRequest(SystemJob.Type type, User user) {
        this.jobName = type.toString().toLowerCase();
        this.creatorUserId = user.getName();
    }

}
