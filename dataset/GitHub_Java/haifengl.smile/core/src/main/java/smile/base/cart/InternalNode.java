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
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.math.MathEx;
import smile.regression.Regression;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An internal node in CART.
 */
public abstract class InternalNode implements Node {

    /**
     * Children node.
     */
    Node trueChild;

    /**
     * Children node.
     */
    Node falseChild;

    /**
     * The split feature for this node.
     */
    int feature;

    /**
     * Reduction in impurity compared to parent.
     */
    double score;

    /**
     * The deviance of node.
     */
    double deviance;

    public InternalNode(int feature, double score, double deviance, Node trueChild, Node falseChild) {
        this.feature = feature;
        this.score = score;
        this.deviance = deviance;
        this.trueChild = trueChild;
        this.falseChild = falseChild;
    }

    /**
     * Evaluate the tree over an instance.
     */
    public abstract LeafNode predict(Tuple x);

    @Override
    public int size() {
        return trueChild.size() + falseChild.size();
    }

    @Override
    public double deviance() {
        return deviance;
    }

    @Override
    public int depth() {
        // compute the depth of each subtree
        int ld = trueChild.depth();
        int rd = falseChild.depth();

        // use the larger one
        return Math.max(ld, rd) + 1;
    }

    @Override
    public Node merge() {
        trueChild = trueChild.merge();
        falseChild = falseChild.merge();

        if (trueChild instanceof DecisionNode && falseChild instanceof DecisionNode) {
            if (((DecisionNode) trueChild).output() == ((DecisionNode) falseChild).output()) {
                int[] a = ((DecisionNode) trueChild).count();
                int[] b = ((DecisionNode) falseChild).count();
                int[] count = new int[a.length];
                for (int i = 0; i < count.length; i++) {
                    count[i] = a[i] + b[i];
                }
                return new DecisionNode(count);
            }

        } else if (trueChild instanceof RegressionNode && falseChild instanceof RegressionNode) {
            if (((RegressionNode) trueChild).output() == ((RegressionNode) falseChild).output()) {
                RegressionNode a = (RegressionNode) trueChild;
                RegressionNode b = (RegressionNode) falseChild;

                int size = a.size + b.size;
                return new RegressionNode(size, a.output(), (a.size * a.mean() + b.size * b.mean()) / size, a.impurity() + b.impurity());
            }
        }

        return this;
    }

    /**
     * Returns the string representation of branch.
     *
     * @param schema the schema of data.
     * @param trueBranch for true or false branch.
     */
    public abstract String toString(StructType schema, boolean trueBranch);

    @Override
    public int[] toString(StructType schema, StructField response, InternalNode parent, int depth, BigInteger id, List<String> lines) {
        BigInteger trueId = id.shiftLeft(1);
        BigInteger falseId = trueId.add(BigInteger.ONE);
        int[] c1 = falseChild.toString(schema, response, this, depth + 1, falseId, lines);
        int[] c2 = trueChild. toString(schema, response, this, depth + 1, trueId,  lines);

        int size = size();
        int[] count = null;
        if (c1 != null) {
            int k = c1.length;
            count = new int[k];
            for (int i = 0; i < k; i++) {
                count[i] = c1[i] + c2[i];
            }
        }

        StringBuilder line = new StringBuilder();

        // indent
        for (int i = 0; i < depth; i++) line.append(" ");
        line.append(id).append(") ");

        // split
        line.append(parent == null ? "root" : parent.toString(schema, this == parent.trueChild)).append(" ");

        // size
        line.append(size).append(" ");

        // deviance
        line.append(String.format("%.5g", deviance())).append(" ");

        if (count == null) {
            // fitted value
            double output = (sumy()) / size;
            line.append(String.format("%g", output)).append(" ");
        } else {
            // fitted value
            int output = MathEx.whichMax(count);
            line.append(response.toString(output)).append(" ");

            // probabilities
            double[] prob = new double[count.length];
            DecisionNode.posteriori(count, prob);
            line.append(Arrays.stream(prob).mapToObj(p -> String.format("%.5g", p)).collect(Collectors.joining(" ", "(", ")")));
        }

        lines.add(line.toString());

        return count;
    }

    /** The size * output in case of regression tree. */
    private double sumy() {
        double t, f;

        if (trueChild instanceof InternalNode) {
            t = ((InternalNode) trueChild).sumy();
        } else if (trueChild instanceof RegressionNode) {
            RegressionNode leaf = (RegressionNode) trueChild;
            t = leaf.output() * leaf.size();
        } else {
            throw new IllegalStateException("Call sumy() on DecisionTree?");
        }

        if (falseChild instanceof InternalNode) {
            f = ((InternalNode) falseChild).sumy();
        } else if (falseChild instanceof RegressionNode) {
            RegressionNode leaf = (RegressionNode) falseChild;
            f = leaf.output() * leaf.size();
        } else {
            throw new IllegalStateException("Call sumy() on DecisionTree?");
        }

        return t + f;
    }
}
