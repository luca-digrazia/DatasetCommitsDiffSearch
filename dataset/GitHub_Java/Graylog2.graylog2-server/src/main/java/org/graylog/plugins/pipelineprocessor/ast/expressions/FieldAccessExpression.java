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

import org.apache.commons.beanutils.PropertyUtils;
import org.graylog.plugins.pipelineprocessor.EvaluationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

public class FieldAccessExpression implements Expression {
    private static final Logger log = LoggerFactory.getLogger(FieldAccessExpression.class);

    private final Expression object;
    private final Expression field;

    public FieldAccessExpression(Expression object, Expression field) {
        this.object = object;
        this.field = field;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    @Override
    public Object evaluate(EvaluationContext context) {
        final Object bean = this.object.evaluate(context);
        final String fieldName = field.evaluate(context).toString();
        try {
            Object property = PropertyUtils.getProperty(bean, fieldName);
            if (property == null) {
                // in case the bean is a Map, try again with a simple property, it might be masked by the Map
                property = PropertyUtils.getSimpleProperty(bean, fieldName);
            }
            log.debug("[field access] property {} of bean {}: {}", fieldName, bean.getClass().getTypeName(), property);
            return property;
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            log.debug("Unable to read property {} from {}", fieldName, bean);
            return null;
        }
    }

    @Override
    public Class getType() {
        return Object.class;
    }

    @Override
    public String toString() {
        return object.toString() + "." + field.toString();
    }
}
