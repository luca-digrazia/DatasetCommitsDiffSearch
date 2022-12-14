/**
 * This file is part of Graylog Pipeline Processor.
 *
 * Graylog Pipeline Processor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog Pipeline Processor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog Pipeline Processor.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog.plugins.pipelineprocessor.functions.messages;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.graylog.plugins.pipelineprocessor.EvaluationContext;
import org.graylog.plugins.pipelineprocessor.ast.functions.AbstractFunction;
import org.graylog.plugins.pipelineprocessor.ast.functions.FunctionArgs;
import org.graylog.plugins.pipelineprocessor.ast.functions.FunctionDescriptor;
import org.graylog2.database.NotFoundException;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.streams.StreamService;

import static com.google.common.collect.ImmutableList.of;
import static org.graylog.plugins.pipelineprocessor.ast.functions.ParameterDescriptor.string;

public class RouteToStream extends AbstractFunction<Void> {

    public static final String NAME = "route_to_stream";
    private static final String ID_ARG = "id";
    private static final String NAME_ARG = "name";
    private final StreamService streamService;

    @Inject
    public RouteToStream(StreamService streamService) {
        this.streamService = streamService;
        streamService.loadAllEnabled();
    }

    @Override
    public Void evaluate(FunctionArgs args, EvaluationContext context) {
        String id = args.param(ID_ARG).eval(args, context, String.class).orElse("");

        final Stream stream;
        if ("".equals(id)) {
            final String name = args.param(NAME_ARG).eval(args, context, String.class).orElse("");
            if ("".equals(name)) {
                return null;
            }
            // TODO efficiency
            final ImmutableMap<String, Stream> stringStreamImmutableMap = Maps.uniqueIndex(streamService.loadAll(),
                                                                                           Stream::getTitle);
            stream = stringStreamImmutableMap.get(name);
            if (stream == null) {
                // TODO signal error somehow
                return null;
            }
        } else {
            try {
                stream = streamService.load(id);
            } catch (NotFoundException e) {
                return null;
            }
        }
        // TODO needs message stack in context to pick message
        if (!stream.isPaused()) {
            context.currentMessage().addStream(stream);
        }
        return null;
    }

    @Override
    public FunctionDescriptor<Void> descriptor() {
        return FunctionDescriptor.<Void>builder()
                .name(NAME)
                .returnType(Void.class)
                .params(of(
                        string(NAME_ARG).optional().build(),
                        string(ID_ARG).optional().build()))
                .build();
    }
}
