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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mongodb.BasicDBObject;
import org.graylog2.utilities.GRN;
import org.graylog2.utilities.GRNRegistry;
import org.graylog2.bindings.providers.MongoJackObjectMapperProvider;
import org.graylog2.database.MongoConnection;
import org.graylog2.database.PaginatedDbService;
import org.graylog2.plugin.database.users.User;
import org.mongojack.DBQuery;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class DBGrantService extends PaginatedDbService<GrantDTO> {
    public static final String COLLECTION_NAME = "grants";

    private final GRNRegistry grnRegistry;

    @Inject
    public DBGrantService(MongoConnection mongoConnection,
                             MongoJackObjectMapperProvider mapper,
                             GRNRegistry grnRegistry) {
        super(mongoConnection, mapper, GrantDTO.class, COLLECTION_NAME);
        this.grnRegistry = grnRegistry;

        db.createIndex(new BasicDBObject(GrantDTO.FIELD_GRANTEE, 1));
        db.createIndex(new BasicDBObject(GrantDTO.FIELD_TARGET, 1));
        // TODO: Add more indices
    }

    public ImmutableSet<GrantDTO> getForGranteesOrGlobal(Set<GRN> grantees) {
        return streamQuery(DBQuery.or(
                DBQuery.in(GrantDTO.FIELD_GRANTEE, grantees),
                DBQuery.is(GrantDTO.FIELD_GRANTEE, GRNRegistry.GLOBAL_USER_GRN)
        )).collect(ImmutableSet.toImmutableSet());
    }

    public List<GrantDTO> getForTargetAndGrantees(GRN target, Set<GRN> grantees) {
        return db.find(DBQuery.and(
                DBQuery.is(GrantDTO.FIELD_TARGET, target),
                DBQuery.in(GrantDTO.FIELD_GRANTEE, grantees))).toArray();
    }

    public GrantDTO create(GrantDTO grantDTO, @Nullable User currentUser) {
        final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        final String userName = requireNonNull(currentUser, "currentUser cannot be null").getName();

        return super.save(grantDTO.toBuilder()
                .createdBy(userName)
                .createdAt(now)
                .updatedBy(userName)
                .updatedAt(now)
                .build());
    }

    public GrantDTO update(GrantDTO updatedGrant, @Nullable User currentUser) {
        final GrantDTO existingGrant = get(updatedGrant.id())
                .orElseThrow(() -> new IllegalArgumentException("Couldn't find grant with ID " + updatedGrant.id()));

        return super.save(existingGrant.toBuilder()
                .grantee(updatedGrant.grantee())
                .role(updatedGrant.role())
                .target(updatedGrant.target())
                .updatedBy(requireNonNull(currentUser, "currentUser cannot be null").getName())
                .updatedAt(ZonedDateTime.now(ZoneOffset.UTC))
                .build());
    }

    public ImmutableMap<String, Set<GrantDTO>> listAll() {
        try (final Stream<GrantDTO> stream = streamAll()) {
            return ImmutableMap.of("grants", stream.collect(Collectors.toSet()));
        }
    }

    public List<GrantDTO> getForTarget(GRN targetGRN, GRN currentUserGRN) {
        return db.find(DBQuery.and(
                DBQuery.is(GrantDTO.FIELD_TARGET, targetGRN.toString()),
                DBQuery.notEquals(GrantDTO.FIELD_GRANTEE, currentUserGRN.toString())
        )).toArray();
    }
}
