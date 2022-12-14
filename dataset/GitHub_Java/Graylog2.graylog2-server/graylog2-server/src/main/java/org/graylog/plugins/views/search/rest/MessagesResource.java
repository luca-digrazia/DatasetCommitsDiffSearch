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
package org.graylog.plugins.views.search.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.glassfish.jersey.server.ChunkedOutput;
import org.graylog.plugins.views.search.Search;
import org.graylog.plugins.views.search.SearchDomain;
import org.graylog.plugins.views.search.SearchExecutionGuard;
import org.graylog.plugins.views.search.export.AuditingMessagesExporter;
import org.graylog.plugins.views.search.export.ChunkedRunner;
import org.graylog.plugins.views.search.export.CommandFactory;
import org.graylog.plugins.views.search.export.ExportMessagesCommand;
import org.graylog.plugins.views.search.export.MessagesExporter;
import org.graylog.plugins.views.search.export.MessagesRequest;
import org.graylog.plugins.views.search.export.ResultFormat;
import org.graylog.plugins.views.search.export.SimpleMessageChunk;
import org.graylog.plugins.views.search.views.ViewDTO;
import org.graylog2.audit.jersey.NoAuditEvent;
import org.graylog2.plugin.rest.PluginRestResource;
import org.graylog2.rest.MoreMediaTypes;
import org.graylog2.shared.rest.resources.RestResource;
import org.graylog2.shared.security.RestPermissions;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

@Api(value = "Search Messages")
@Path("/views/search/messages")
@RequiresAuthentication
public class MessagesResource extends RestResource implements PluginRestResource {

    private final CommandFactory commandFactory;
    private final SearchDomain searchDomain;
    private final SearchExecutionGuard executionGuard;
    private final PermittedStreams permittedStreams;
    private final ObjectMapper objectMapper;

    //allow mocking
    Function<Consumer<Consumer<SimpleMessageChunk>>, ChunkedOutput<SimpleMessageChunk>> asyncRunner = ChunkedRunner::runAsync;
    Function<String, MessagesExporter> messagesExporterFactory;

    @Inject
    public MessagesResource(
            MessagesExporter exporter,
            CommandFactory commandFactory,
            SearchDomain searchDomain,
            SearchExecutionGuard executionGuard,
            PermittedStreams permittedStreams,
            ObjectMapper objectMapper,
            @SuppressWarnings("UnstableApiUsage") EventBus eventBus) {
        this.commandFactory = commandFactory;
        this.searchDomain = searchDomain;
        this.executionGuard = executionGuard;
        this.permittedStreams = permittedStreams;
        this.objectMapper = objectMapper;
        this.messagesExporterFactory = userName -> new AuditingMessagesExporter(eventBus, userName, exporter);
    }

    private MessagesExporter exporter() {
        return messagesExporterFactory.apply(userName());
    }

    @POST
    @Produces(MoreMediaTypes.TEXT_CSV)
    @NoAuditEvent("Has custom audit events")
    public ChunkedOutput<SimpleMessageChunk> retrieve(@ApiParam @Valid MessagesRequest rawrequest) {
        final MessagesRequest request = fillInIfNecessary(rawrequest);

        executionGuard.checkUserIsPermittedToSeeStreams(request.streams(), this::hasStreamReadPermission);

        ExportMessagesCommand command = commandFactory.buildFromRequest(request);

        return asyncRunner.apply(chunkConsumer -> exporter().export(command, chunkConsumer));
    }

    private String userName() {
        return Objects.requireNonNull(getCurrentUser()).getName();
    }

    private MessagesRequest fillInIfNecessary(MessagesRequest requestFromClient) {
        MessagesRequest request = requestFromClient != null ? requestFromClient : MessagesRequest.withDefaults();

        if (request.streams().isEmpty()) {
            request = request.toBuilder().streams(loadAllAllowedStreamsForUser()).build();
        }
        return request;
    }

    @POST
    @Path("{searchId}")
    @Produces(MoreMediaTypes.TEXT_CSV)
    @NoAuditEvent("Has custom audit events")
    public ChunkedOutput<SimpleMessageChunk> retrieveForSearch(
            @ApiParam @PathParam("searchId") String searchId,
            @ApiParam @Valid ResultFormat formatFromClient) {
        ResultFormat format = emptyIfNull(formatFromClient);

        Search search = loadSearch(searchId, format.executionState());

        ExportMessagesCommand command = commandFactory.buildWithSearchOnly(search, format);

        return asyncRunner.apply(chunkConsumer -> exporter().export(command, chunkConsumer));
    }

    @POST
    @Path("{searchId}/{searchTypeId}")
    @Produces(MoreMediaTypes.TEXT_CSV)
    @NoAuditEvent("Has custom audit events")
    public ChunkedOutput<SimpleMessageChunk> retrieveForSearchType(
            @ApiParam @PathParam("searchId") String searchId,
            @ApiParam @PathParam("searchTypeId") String searchTypeId,
            @ApiParam @Valid ResultFormat formatFromClient) {
        ResultFormat format = emptyIfNull(formatFromClient);

        Search search = loadSearch(searchId, format.executionState());

        ExportMessagesCommand command = commandFactory.buildWithMessageList(search, searchTypeId, format);

        return asyncRunner.apply(chunkConsumer -> exporter().export(command, chunkConsumer));
    }

    private ResultFormat emptyIfNull(ResultFormat format) {
        return format == null ? ResultFormat.empty() : format;
    }

    private Search loadSearch(String searchId, Map<String, Object> executionState) {
        Search search = searchDomain.getForUser(searchId, getCurrentUser(), this::hasViewReadPermission)
                .orElseThrow(() -> new NotFoundException("Search with id " + searchId + " does not exist"));

        search = search.addStreamsToQueriesWithoutStreams(this::loadAllAllowedStreamsForUser);

        search = search.applyExecutionState(objectMapper, executionState);

        executionGuard.check(search, this::hasStreamReadPermission);

        return search;
    }

    private boolean hasViewReadPermission(ViewDTO view) {
        return isPermitted(ViewsRestPermissions.VIEW_READ, view.id());
    }

    private ImmutableSet<String> loadAllAllowedStreamsForUser() {
        return permittedStreams.load(this::hasStreamReadPermission);
    }

    private boolean hasStreamReadPermission(String streamId) {
        return isPermitted(RestPermissions.STREAMS_READ, streamId);
    }
}
