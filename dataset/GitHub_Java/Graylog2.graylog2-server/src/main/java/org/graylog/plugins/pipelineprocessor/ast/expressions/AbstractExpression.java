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
package org.graylog.plugins.pipelineprocessor.ast.expressions;

import org.antlr.v4.runtime.Token;
import org.graylog.plugins.pipelineprocessor.EvaluationContext;
import org.graylog.plugins.pipelineprocessor.ast.exceptions.FunctionEvaluationException;

import javax.annotation.Nullable;

import static org.graylog2.shared.utilities.ExceptionUtils.getRootCause;

public abstract class AbstractExpression implements Expression {

    private final Token startToken;

    public AbstractExpression(Token startToken) {
        this.startToken = startToken;
    }

    public Token getStartToken() {
        return startToken;
    }

    @Override
    @Nullable
    public Object evaluate(EvaluationContext context) {
        try {
            return evaluateUnsafe(context);
        } catch (FunctionEvaluationException fee) {
            context.addEvaluationError(fee.getStartToken().getLine(),
                                       fee.getStartToken().getCharPositionInLine(),
                                       fee.getFunctionExpression().getFunction().descriptor(),
                                       getRootCause(fee));
        } catch (Exception e) {
            context.addEvaluationError(startToken.getLine(), startToken.getCharPositionInLine(), null, getRootCause(e));
        }
        return null;
    }

    @Nullable
    /**
     * This method is allow to throw exceptions. The outside world is supposed to call evaluate instead.
     */
    public abstract Object evaluateUnsafe(EvaluationContext context);

}
