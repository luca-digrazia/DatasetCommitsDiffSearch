/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.data.formula;

import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructType;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The term of exp function.
 *
 * @author Haifeng Li
 */
public class Exp<T> implements Factor<T, Double> {
    /** The operand factor of exp expression. */
    private Factor<T, Double> child;

    /**
     * Constructor.
     *
     * @param factor the factor that exp function is applied to.
     */
    public Exp(Factor<T, Double> factor) {
        this.child = factor;
    }

    @Override
    public String name() {
        return String.format("exp(%s)", child.name());
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public boolean equals(Object o) {
        return name().equals(o);
    }

    @Override
    public List<? extends Factor> factors() {
        return Collections.singletonList(this);
    }

    @Override
    public Set<String> variables() {
        return child.variables();
    }

    @Override
    public Double apply(T o) {
        return Math.exp(child.apply(o));
    }

    @Override
    public DataType type() {
        return DataTypes.DoubleType;
    }

    @Override
    public void bind(StructType schema) {
        child.bind(schema);
    }
}
