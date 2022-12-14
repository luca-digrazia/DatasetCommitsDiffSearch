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
package org.graylog.plugins.pipelineprocessor.functions.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.TypeLiteral;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import org.graylog.plugins.pipelineprocessor.EvaluationContext;
import org.graylog.plugins.pipelineprocessor.ast.functions.AbstractFunction;
import org.graylog.plugins.pipelineprocessor.ast.functions.FunctionArgs;
import org.graylog.plugins.pipelineprocessor.ast.functions.FunctionDescriptor;
import org.graylog.plugins.pipelineprocessor.ast.functions.ParameterDescriptor;

import javax.inject.Inject;
import java.util.Map;

import static com.google.common.collect.ImmutableList.of;
import static java.util.stream.Collectors.toMap;

public class SelectJsonPath extends AbstractFunction<Map<String, Object>> {

    public static final String NAME = "select_jsonpath";

    private final Configuration configuration;
    private final ParameterDescriptor<JsonNode, JsonNode> jsonParam;
    private final ParameterDescriptor<Map<String, String>, Map<String, JsonPath>> pathsParam;

    @Inject
    public SelectJsonPath(ObjectMapper objectMapper) {
        configuration = Configuration.builder()
                .options(Option.SUPPRESS_EXCEPTIONS)
                .jsonProvider(new JacksonJsonNodeJsonProvider(objectMapper))
                .build();

        jsonParam = ParameterDescriptor.type("json", JsonNode.class).build();
        // sigh generics and type erasure
        //noinspection unchecked
        pathsParam = ParameterDescriptor.type("paths",
                                              (Class<Map<String, String>>) new TypeLiteral<Map<String, String>>() {}.getRawType(),
                                              (Class<Map<String, JsonPath>>) new TypeLiteral<Map<String, JsonPath>>() {}.getRawType())
                .transform(inputMap -> inputMap
                        .entrySet().stream()
                        .collect(toMap(Map.Entry::getKey, e -> JsonPath.compile(e.getValue()))))
                .build();
    }

    @Override
    public Map<String, Object> evaluate(FunctionArgs args, EvaluationContext context) {
        final JsonNode json = jsonParam.required(args, context);
        final Map<String, JsonPath> paths = pathsParam.required(args, context);
        if (json == null || paths == null) {
            return null;
        }
        return paths
                .entrySet().stream()
                .collect(toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().read(json, configuration)
                ));
    }

    @Override
    public FunctionDescriptor<Map<String, Object>> descriptor() {
        //noinspection unchecked
        return FunctionDescriptor.<Map<String, Object>>builder()
                .name(NAME)
                .returnType((Class<? extends Map<String, Object>>) new TypeLiteral<Map<String, Object>>() {}.getRawType())
                .params(of(
                        jsonParam,
                        pathsParam
                ))
                .build();
    }

}
