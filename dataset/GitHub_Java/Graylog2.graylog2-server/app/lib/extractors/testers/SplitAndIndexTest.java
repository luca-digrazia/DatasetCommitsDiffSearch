/**
 * Copyright 2013 Lennart Koopmann <lennart@torch.sh>
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
 *
 */
package lib.extractors.testers;

import com.google.common.collect.Maps;
import lib.APIException;
import lib.ApiClient;
import models.api.responses.SplitAndIndexTestResponse;

import java.io.IOException;
import java.util.Map;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class SplitAndIndexTest {

    public static Map<String, Object> test(String splitBy, int index, String string) throws IOException, APIException {
        SplitAndIndexTestResponse r = ApiClient.get(SplitAndIndexTestResponse.class)
                .path("/tools/split_and_index_tester")
                .queryParam("split_by", splitBy)
                .queryParam("index", index)
                .queryParam("string", string)
                .execute();

                //Api.get(part, SplitAndIndexTestResponse.class);

        Map<String, Object> match = Maps.newHashMap();
        match.put("start", r.beginIndex);
        match.put("end", r.endIndex);

        Map<String, Object> result = Maps.newHashMap();
        result.put("successful", r.successful);
        result.put("cut", r.cut);
        result.put("match", match);

        return result;
    }

}
