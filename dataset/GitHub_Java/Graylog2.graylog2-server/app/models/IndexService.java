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
package models;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import lib.APIException;
import lib.ApiClient;
import models.api.responses.system.indices.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class IndexService {

    private final ApiClient api;
    private final Index.Factory indexFactory;

    @Inject
    private IndexService(ApiClient api, Index.Factory indexFactory) {
        this.api = api;
        this.indexFactory = indexFactory;
    }

    public List<Index> all() throws APIException, IOException {
        List<Index> indices = Lists.newArrayList();

        IndexRangesResponse rr = api.get(IndexRangesResponse.class)
                .path("/system/indices/ranges")
                .execute();

        for (IndexRangeSummary range : rr.ranges) {
            indices.add(indexFactory.fromRangeResponse(range));
        }

        return indices;
    }

    public DeflectorInformationResponse getDeflectorInfo() throws APIException, IOException {
        return api.get(DeflectorInformationResponse.class)
                .path("/system/deflector")
                .execute();
    }

    public DeflectorConfigResponse getDeflectorConfig() throws APIException, IOException {
        return api.get(DeflectorConfigResponse.class)
                .onlyMasterNode()
                .path("/system/deflector/config")
                .execute();
    }

    public ClosedIndicesResponse getClosedIndices() throws APIException, IOException {
        return api.get(ClosedIndicesResponse.class)
                .path("/system/indexer/indices/closed")
                .execute();
    }

    public void recalculateRanges() throws APIException, IOException {
        api.post().path("/system/indices/ranges/rebuild")
                .expect(202)
                .execute();
    }

    public void cycleDeflector() throws APIException, IOException {
        api.post().path("/system/deflector/cycle")
                .timeout(60, TimeUnit.SECONDS)
                .onlyMasterNode()
                .execute();
    }

    // Not part an Index model instance method because opening/closing can be applied to indices without calculated ranges.
    public void close(String index) throws APIException, IOException {
        api.post().path("/system/indexer/indices/{0}/close", index)
                .timeout(60, TimeUnit.SECONDS)
                .expect(204)
                .execute();
    }

    // Not part an Index model instance method because opening/closing can be applied to indices without calculated ranges.
    public void reopen(String index) throws APIException, IOException {
        api.post().path("/system/indexer/indices/{0}/reopen", index)
                .timeout(60, TimeUnit.SECONDS)
                .expect(204)
                .execute();
    }

    // Not part an Index model instance method because opening/closing can be applied to indices without calculated ranges.
    public void delete(String index) throws APIException, IOException {
        api.delete().path("/system/indexer/indices/{0}", index)
                .timeout(60, TimeUnit.SECONDS)
                .expect(204)
                .execute();
    }

}
