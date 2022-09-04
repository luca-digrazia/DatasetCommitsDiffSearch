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
package org.graylog.plugins.pipelineprocessor.functions.conversion;

import org.graylog.plugins.pipelineprocessor.EvaluationContext;
import org.graylog.plugins.pipelineprocessor.ast.functions.AbstractFunction;
import org.graylog.plugins.pipelineprocessor.ast.functions.FunctionArgs;
import org.graylog.plugins.pipelineprocessor.ast.functions.FunctionDescriptor;
import org.joda.time.DateTime;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.google.common.collect.ImmutableList.of;
import static org.graylog.plugins.pipelineprocessor.ast.functions.ParameterDescriptor.object;
import static org.graylog.plugins.pipelineprocessor.ast.functions.ParameterDescriptor.string;

public class StringConversion extends AbstractFunction<String> {

    public static final String NAME = "tostring";

    private static final String VALUE = "value";
    private static final String DEFAULT = "default";

    // this is per-thread to save an expensive concurrent hashmap access
    private final ThreadLocal<LinkedHashMap<Class<?>, Class<?>>> declaringClassCache;

    public StringConversion() {
        declaringClassCache = new ThreadLocal<LinkedHashMap<Class<?>, Class<?>>>() {
            @Override
            protected LinkedHashMap<Class<?>, Class<?>> initialValue() {
                return new LinkedHashMap<Class<?>, Class<?>>() {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Class<?>, Class<?>> eldest) {
                        return size() > 1024;
                    }
                };
            }
        };
    }

    @Override
    public String evaluate(FunctionArgs args, EvaluationContext context) {
        final Object evaluated = args.param(VALUE).evalRequired(args, context, Object.class);
        // fast path for the most common targets
        //noinspection Duplicates
        if (evaluated instanceof String
                || evaluated instanceof Number
                || evaluated instanceof Boolean
                || evaluated instanceof DateTime) {
            return evaluated.toString();
        } else {
            try {
                // slow path, we aren't sure that the object's class actually overrides toString() so we'll look it up.
                final Class<?> klass = evaluated.getClass();
                final LinkedHashMap<Class<?>, Class<?>> classCache = declaringClassCache.get();

                Class<?> declaringClass = classCache.get(klass);
                if (declaringClass == null) {
                    declaringClass = klass.getMethod("toString").getDeclaringClass();
                    classCache.put(klass, declaringClass);
                }
                if ((declaringClass != Object.class)) {
                    return evaluated.toString();
                } else {
                    return args.param(DEFAULT).eval(args, context, String.class).orElse("");
                }
            } catch (NoSuchMethodException ignored) {
                // should never happen because toString is always there
                return args.param(DEFAULT).eval(args, context, String.class).orElse("");
            }
        }
    }

    @Override
    public FunctionDescriptor<String> descriptor() {
        return FunctionDescriptor.<String>builder()
                .name(NAME)
                .returnType(String.class)
                .params(of(
                        object(VALUE).build(),
                        string(DEFAULT).optional().build()
                ))
                .build();
    }
}
