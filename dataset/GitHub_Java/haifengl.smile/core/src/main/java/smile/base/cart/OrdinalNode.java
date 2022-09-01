/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.base.cart;

import smile.data.Tuple;
import smile.data.measure.DiscreteMeasure;
import smile.data.measure.Measure;
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * A node with a ordinal split variable (real-valued or ordinal categorical value).
 */
public class OrdinalNode extends InternalNode {
    private static final long serialVersionUID = 1L;

    /**
     * The split value.
     */
    double value = Double.NaN;

    /** Constructor. */
    public OrdinalNode(int feature, double value, double score, Node trueChild, Node falseChild) {
        super(feature, score, trueChild, falseChild);
        this.value = value;
    }

    @Override
    public LeafNode predict(Tuple x) {
        return x.getDouble(feature) <= value ? trueChild.predict(x) : falseChild.predict(x);
    }

    @Override
    public String dot(StructType schema, StructField response, int id) {
        StructField field = schema.field(feature);
        return String.format(" %d [label=<%s &le; %s<br/>size = %d<br/>impurity reduction = %.4f>, fillcolor=\"#00000000\"];\n", id, field.name, field.toString(value), size(), score);
    }
}
