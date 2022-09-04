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
package org.graylog2.restclient.models.api.requests.streams;

import org.graylog2.restclient.models.api.requests.ApiRequest;
import play.data.validation.Constraints;

import java.util.Map;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class TestMatchRequest extends ApiRequest {
    //@Constraints.Required
    public Map<String, Object> message;
}
