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
package org.graylog.plugins.pipelineprocessor.ast.functions;

import com.google.common.collect.ImmutableList;
import org.graylog.plugins.pipelineprocessor.EvaluationContext;
import org.graylog.plugins.pipelineprocessor.ast.expressions.Expression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public interface Function<T> {

    Logger log = LoggerFactory.getLogger(Function.class);

    Function ERROR_FUNCTION = new AbstractFunction<Void>() {
        @Override
        public Void evaluate(FunctionArgs args, EvaluationContext context) {
            return null;
        }

        @Override
        public FunctionDescriptor<Void> descriptor() {
            return FunctionDescriptor.<Void>builder()
                    .name("__unresolved_function")
                    .returnType(Void.class)
                    .params(ImmutableList.of())
                    .build();
        }
    };

    default void preprocessArgs(FunctionArgs args) {
        for (Map.Entry<String, Expression> e : args.getConstantArgs().entrySet()) {
            try {
                final Object value = preComputeConstantArgument(args, e.getKey(), e.getValue());
                if (value != null) {
                    args.setPreComputedValue(e.getKey(), value);
                }
            } catch (Exception exception) {
                log.warn("Unable to precompute argument value for " + e.getKey(), exception);
                throw exception;
            }
        }

    }

    /**
     * Implementations should provide a non-null value for each argument they wish to pre-compute.
     * <br/>
     * Examples include compile a Pattern from a regex string, which will never change during the lifetime of the function.
     * If any part of the expression tree depends on external values this method will not be called, e.g. if the regex depends on a message field.
     * @param args the function args for this functions, usually you don't need this
     * @param name the name of the argument to potentially precompute
     * @param arg the expression tree for the argument
     * @return the precomputed value for the argument or <code>null</code> if the value should be dynamically calculated for each invocation
     */
    Object preComputeConstantArgument(FunctionArgs args, String name, Expression arg);

    T evaluate(FunctionArgs args, EvaluationContext context);

    FunctionDescriptor<T> descriptor();

}
