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

package smile.classification;

import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.IntStream;

import smile.base.cart.*;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.data.vector.DoubleVector;
import smile.math.MathEx;
import smile.regression.RegressionTree;
import smile.validation.Accuracy;
import smile.validation.ClassificationMeasure;

/**
 * Gradient boosting for classification. Gradient boosting is typically used
 * with decision trees (especially CART regression trees) of a fixed size as
 * base learners. For this special case Friedman proposes a modification to
 * gradient boosting method which improves the quality of fit of each base
 * learner.
 * <p>
 * Generic gradient boosting at the t-th step would fit a regression tree to
 * pseudo-residuals. Let J be the number of its leaves. The tree partitions
 * the input space into J disjoint regions and predicts a constant value in
 * each region. The parameter J controls the maximum allowed
 * level of interaction between variables in the model. With J = 2 (decision
 * stumps), no interaction between variables is allowed. With J = 3 the model
 * may include effects of the interaction between up to two variables, and
 * so on. Hastie et al. comment that typically 4 &le; J &le; 8 work well
 * for boosting and results are fairly insensitive to the choice of in
 * this range, J = 2 is insufficient for many applications, and J &gt; 10 is
 * unlikely to be required.
 * <p>
 * Fitting the training set too closely can lead to degradation of the model's
 * generalization ability. Several so-called regularization techniques reduce
 * this over-fitting effect by constraining the fitting procedure.
 * One natural regularization parameter is the number of gradient boosting
 * iterations T (i.e. the number of trees in the model when the base learner
 * is a decision tree). Increasing T reduces the error on training set,
 * but setting it too high may lead to over-fitting. An optimal value of T
 * is often selected by monitoring prediction error on a separate validation
 * data set.
 * <p>
 * Another regularization approach is the shrinkage which times a parameter
 * &eta; (called the "learning rate") to update term.
 * Empirically it has been found that using small learning rates (such as
 * &eta; &lt; 0.1) yields dramatic improvements in model's generalization ability
 * over gradient boosting without shrinking (&eta; = 1). However, it comes at
 * the price of increasing computational time both during training and
 * prediction: lower learning rate requires more iterations.
 * <p>
 * Soon after the introduction of gradient boosting Friedman proposed a
 * minor modification to the algorithm, motivated by Breiman's bagging method.
 * Specifically, he proposed that at each iteration of the algorithm, a base
 * learner should be fit on a subsample of the training set drawn at random
 * without replacement. Friedman observed a substantial improvement in
 * gradient boosting's accuracy with this modification.
 * <p>
 * Subsample size is some constant fraction f of the size of the training set.
 * When f = 1, the algorithm is deterministic and identical to the one
 * described above. Smaller values of f introduce randomness into the
 * algorithm and help prevent over-fitting, acting as a kind of regularization.
 * The algorithm also becomes faster, because regression trees have to be fit
 * to smaller datasets at each iteration. Typically, f is set to 0.5, meaning
 * that one half of the training set is used to build each base learner.
 * <p>
 * Also, like in bagging, sub-sampling allows one to define an out-of-bag
 * estimate of the prediction performance improvement by evaluating predictions
 * on those observations which were not used in the building of the next
 * base learner. Out-of-bag estimates help avoid the need for an independent
 * validation dataset, but often underestimate actual performance improvement
 * and the optimal number of iterations.
 * <p>
 * Gradient tree boosting implementations often also use regularization by
 * limiting the minimum number of observations in trees' terminal nodes.
 * It's used in the tree building process by ignoring any splits that lead
 * to nodes containing fewer than this number of training set instances.
 * Imposing this limit helps to reduce variance in predictions at leaves.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> J. H. Friedman. Greedy Function Approximation: A Gradient Boosting Machine, 1999.</li>
 * <li> J. H. Friedman. Stochastic Gradient Boosting, 1999.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class GradientTreeBoost implements SoftClassifier<Tuple>, DataFrameClassifier {
    private static final long serialVersionUID = 2L;

    /**
     * Design matrix formula
     */
    private Formula formula;
    /**
     * The number of classes.
     */
    private int k = 2;
    /**
     * Forest of regression trees for binary classification.
     */
    private RegressionTree[] trees;
    /**
     * Forest of regression trees for multi-class classification.
     */
    private RegressionTree[][] forest;
    /**
     * Variable importance. Every time a split of a node is made on variable
     * the impurity criterion for the two descendent nodes is less than the
     * parent node. Adding up the decreases for each individual variable over
     * all trees in the forest gives a simple variable importance.
     */
    private double[] importance;
    /**
     * The intercept for binary classification.
     */
    private double b = 0.0;
    /**
     * The shrinkage parameter in (0, 1] controls the learning rate of procedure.
     */
    private double shrinkage = 0.005;
    /**
     * The class label encoder.
     */
    private ClassLabel labels;

    /**
     * Constructor of binary class.
     *
     * @param formula    a symbolic description of the model to be fitted.
     * @param trees      forest of regression trees.
     * @param b          the intercept
     * @param importance variable importance
     */
    public GradientTreeBoost(Formula formula, RegressionTree[] trees, double b, double shrinkage, double[] importance) {
        this(formula, trees, b, shrinkage, importance, ClassLabel.of(2));
    }

    /**
     * Constructor of binary class.
     *
     * @param formula    a symbolic description of the model to be fitted.
     * @param trees      forest of regression trees.
     * @param b          the intercept
     * @param importance variable importance
     * @param labels     class labels
     */
    public GradientTreeBoost(Formula formula, RegressionTree[] trees, double b, double shrinkage, double[] importance, ClassLabel labels) {
        this.formula = formula;
        this.k = 2;
        this.trees = trees;
        this.b = b;
        this.shrinkage = shrinkage;
        this.importance = importance;
        this.labels = labels;
    }

    /**
     * Constructor of multi-class.
     *
     * @param formula    a symbolic description of the model to be fitted.
     * @param forest     forest of regression trees.
     * @param importance variable importance
     */
    public GradientTreeBoost(Formula formula, RegressionTree[][] forest, double shrinkage, double[] importance) {
        this(formula, forest, shrinkage, importance, ClassLabel.of(forest.length));
    }

    /**
     * Constructor of multi-class.
     *
     * @param formula    a symbolic description of the model to be fitted.
     * @param forest     forest of regression trees.
     * @param importance variable importance
     * @param labels     class labels
     */
    public GradientTreeBoost(Formula formula, RegressionTree[][] forest, double shrinkage, double[] importance, ClassLabel labels) {
        this.formula = formula;
        this.k = forest.length;
        this.forest = forest;
        this.shrinkage = shrinkage;
        this.importance = importance;
        this.labels = labels;
    }

    /**
     * Learns a gradient tree boosting for classification.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data    the data frame of the explanatory and response variables.
     */
    public static GradientTreeBoost fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Properties());
    }

    /**
     * Learns a gradient tree boosting for classification.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data    the data frame of the explanatory and response variables.
     */
    public static GradientTreeBoost fit(Formula formula, DataFrame data, Properties prop) {
        int ntrees = Integer.valueOf(prop.getProperty("smile.gbt.trees", "500"));
        int maxNodes = Integer.valueOf(prop.getProperty("smile.gbt.max.nodes", "6"));
        int nodeSize = Integer.valueOf(prop.getProperty("smile.gbt.node.size", "5"));
        double shrinkage = Double.valueOf(prop.getProperty("smile.gbt.shrinkage", "0.05"));
        double subsample = Double.valueOf(prop.getProperty("smile.gbt.sample.rate", "0.7"));
        return fit(formula, data, ntrees, maxNodes, nodeSize, shrinkage, subsample);
    }

    /**
     * Constructor. Learns a gradient tree boosting for classification.
     *
     * @param formula   a symbolic description of the model to be fitted.
     * @param data      the data frame of the explanatory and response variables.
     * @param ntrees    the number of iterations (trees).
     * @param maxNodes  the number of leaves in each tree.
     * @param nodeSize  the number of instances in a node below which the tree will
     *                  not split, setting nodeSize = 5 generally gives good results.
     * @param shrinkage the shrinkage parameter in (0, 1] controls the learning rate of procedure.
     * @param subsample the sampling fraction for stochastic tree boosting.
     */
    public static GradientTreeBoost fit(Formula formula, DataFrame data, int ntrees, int maxNodes, int nodeSize, double shrinkage, double subsample) {
        if (ntrees < 1) {
            throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
        }

        if (maxNodes < 2) {
            throw new IllegalArgumentException("Invalid maximum leaves: " + maxNodes);
        }

        if (nodeSize < 1) {
            throw new IllegalArgumentException("Invalid minimum size of leaves: " + nodeSize);
        }

        if (shrinkage <= 0 || shrinkage > 1) {
            throw new IllegalArgumentException("Invalid shrinkage: " + shrinkage);
        }

        if (subsample <= 0 || subsample > 1) {
            throw new IllegalArgumentException("Invalid sampling fraction: " + subsample);
        }

        DataFrame x = formula.x(data);
        BaseVector y = formula.y(data);

        int[][] order = CART.order(x);
        ClassLabel.Result codec = ClassLabel.fit(y);

        if (codec.k == 2) {
            return train2(formula, x, codec, order, ntrees, maxNodes, nodeSize, shrinkage, subsample);
        } else {
            return traink(formula, x, codec, order, ntrees, maxNodes, nodeSize, shrinkage, subsample);
        }
    }

    @Override
    public Optional<Formula> formula() {
        return Optional.of(formula);
    }

    @Override
    public Optional<StructType> schema() {
        if (trees != null)
            return trees[0].schema();
        else
            return forest[0][0].schema();
    }

    /**
     * Returns the variable importance. Every time a split of a node is made
     * on variable the impurity criterion for the two descendent nodes is less
     * than the parent node. Adding up the decreases for each individual
     * variable over all trees in the forest gives a simple measure of variable
     * importance.
     *
     * @return the variable importance
     */
    public double[] importance() {
        return importance;
    }

    /**
     * Train L2 tree boost.
     */
    private static GradientTreeBoost train2(Formula formula, DataFrame x, ClassLabel.Result codec, int[][] order, int ntrees, int maxNodes, int nodeSize, double shrinkage, double subsample) {
        int n = x.nrows();
        int k = codec.k;
        int[] y = codec.y;

        int[] nc = new int[k];
        for (int i = 0; i < n; i++) nc[y[i]]++;

        int[] y2 = Arrays.stream(y).map(i -> 2 * i - 1).toArray();

        double[] h = new double[n]; // current F(x_i)
        double[] response = new double[n]; // response variable for regression tree.
        StructField field = new StructField("residual", DataTypes.DoubleType);

        double mu = MathEx.mean(y2);
        double b = 0.5 * Math.log((1 + mu) / (1 - mu));

        Arrays.fill(h, b);

        RegressionNodeOutput output = new BinaryLogisticRegressionNodeOutput(response);
        RegressionTree[] trees = new RegressionTree[ntrees];

        int[] permutation = IntStream.range(0, n).toArray();
        int[] samples = new int[n];

        for (int m = 0; m < ntrees; m++) {
            sampling(samples, permutation, nc, y, subsample);

            for (int i = 0; i < n; i++) {
                response[i] = 2.0 * y2[i] / (1 + Math.exp(2 * y2[i] * h[i]));
            }

            trees[m] = new RegressionTree(x, response, field, maxNodes, nodeSize, x.ncols(), samples, order, output);

            for (int i = 0; i < n; i++) {
                h[i] += shrinkage * trees[m].predict(x.get(i));
            }
        }

        double[] importance = new double[x.ncols()];
        for (RegressionTree tree : trees) {
            double[] imp = tree.importance();
            for (int i = 0; i < imp.length; i++) {
                importance[i] += imp[i];
            }
        }

        return new GradientTreeBoost(formula, trees, b, shrinkage, importance, codec.labels);
    }

    /**
     * Train L-k tree boost.
     */
    private static GradientTreeBoost traink(Formula formula, DataFrame x, ClassLabel.Result codec, int[][] order, int ntrees, int maxNodes, int nodeSize, double shrinkage, double subsample) {
        int n = x.nrows();
        int k = codec.k;
        int[] y = codec.y;

        int[] nc = new int[k];
        for (int i = 0; i < n; i++) nc[y[i]]++;

        double[][] h = new double[k][n]; // boost tree output.
        double[][] p = new double[k][n]; // posteriori probabilities.
        double[][] response = new double[k][n]; // pseudo response.
        StructField field = new StructField("residual", DataTypes.DoubleType);

        RegressionTree[][] forest = new RegressionTree[k][ntrees];

        RegressionNodeOutput[] output = new RegressionNodeOutput[k];
        for (int i = 0; i < k; i++) {
            output[i] = new MultiClassLogisticRegressionNodeOutput(k, response[i]);
        }

        int[] permutation = IntStream.range(0, n).toArray();
        int[] samples = new int[n];

        for (int m = 0; m < ntrees; m++) {
            for (int i = 0; i < n; i++) {
                double max = Double.NEGATIVE_INFINITY;
                for (int j = 0; j < k; j++) {
                    if (max < h[j][i]) {
                        max = h[j][i];
                    }
                }

                double Z = 0.0;
                for (int j = 0; j < k; j++) {
                    p[j][i] = Math.exp(h[j][i] - max);
                    Z += p[j][i];
                }

                for (int j = 0; j < k; j++) {
                    p[j][i] /= Z;
                }
            }

            for (int j = 0; j < k; j++) {
                for (int i = 0; i < n; i++) {
                    response[j][i] = (y[i] == j ? 1.0 : 0.0) - p[j][i];
                }

                sampling(samples, permutation, nc, y, subsample);

                forest[j][m] = new RegressionTree(x, response[j], field, maxNodes, nodeSize, x.ncols(), samples, order, output[j]);

                for (int i = 0; i < n; i++) {
                    h[j][i] += shrinkage * forest[j][m].predict(x.get(i));
                }
            }
        }

        double[] importance = new double[x.ncols()];
        for (RegressionTree[] grove : forest) {
            for (RegressionTree tree : grove) {
                double[] imp = tree.importance();
                for (int i = 0; i < imp.length; i++) {
                    importance[i] += imp[i];
                }
            }
        }

        return new GradientTreeBoost(formula, forest, shrinkage, importance, codec.labels);
    }

    /**
     * Stratified sampling.
     */
    private static void sampling(int[] samples, int[] permutation, int[] nc, int[] y, double subsample) {
        int n = samples.length;
        int k = nc.length;
        Arrays.fill(samples, 0);
        MathEx.permutate(permutation);

        for (int j = 0; j < k; j++) {
            int subj = (int) Math.round(nc[j] * subsample);
            for (int i = 0, nj = 0; i < n && nj < subj; i++) {
                int xi = permutation[i];
                if (y[xi] == j) {
                    samples[xi] = 1;
                    nj++;
                }
            }
        }
    }

    /**
     * Returns the number of trees in the model.
     *
     * @return the number of trees in the model
     */
    public int size() {
        return trees.length;
    }

    /**
     * Returns the regression trees.
     */
    public RegressionTree[] trees() {
        return trees;
    }

    /**
     * Trims the tree model set to a smaller size in case of over-fitting.
     * Or if extra decision trees in the model don't improve the performance,
     * we may remove them to reduce the model size and also improve the speed of
     * prediction.
     *
     * @param ntrees the new (smaller) size of tree model set.
     */
    public void trim(int ntrees) {
        if (ntrees < 1) {
            throw new IllegalArgumentException("Invalid new model size: " + ntrees);
        }

        if (k == 2) {
            if (ntrees > trees.length) {
                throw new IllegalArgumentException("The new model size is larger than the current size.");
            }

            if (ntrees < trees.length) {
                trees = Arrays.copyOf(trees, ntrees);
            }
        } else {
            if (ntrees > forest[0].length) {
                throw new IllegalArgumentException("The new model size is larger than the current one.");
            }

            if (ntrees < forest[0].length) {
                for (int i = 0; i < forest.length; i++) {
                    forest[i] = Arrays.copyOf(forest[i], ntrees);
                }
            }
        }
    }

    @Override
    public int predict(Tuple x) {
        Tuple xt = formula.x(x);
        if (k == 2) {
            double y = b;
            for (RegressionTree tree : trees) {
                y += shrinkage * tree.predict(xt);
            }

            return labels.label(y > 0 ? 1 : 0);
        } else {
            double max = Double.NEGATIVE_INFINITY;
            int y = -1;
            for (int j = 0; j < k; j++) {
                double yj = 0.0;
                for (RegressionTree tree : forest[j]) {
                    yj += shrinkage * tree.predict(xt);
                }

                if (yj > max) {
                    max = yj;
                    y = j;
                }
            }

            return labels.label(y);
        }
    }

    @Override
    public int predict(Tuple x, double[] posteriori) {
        if (posteriori.length != k) {
            throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected: %d", posteriori.length, k));
        }

        Tuple xt = formula.x(x);
        if (k == 2) {
            double y = b;
            for (RegressionTree tree : trees) {
                y += shrinkage * tree.predict(xt);
            }

            posteriori[0] = 1.0 / (1.0 + Math.exp(2 * y));
            posteriori[1] = 1.0 - posteriori[0];

            return labels.label(y > 0 ? 1 : 0);
        } else {
            double max = Double.NEGATIVE_INFINITY;
            int y = -1;
            for (int j = 0; j < k; j++) {
                posteriori[j] = 0.0;

                for (RegressionTree tree : forest[j]) {
                    posteriori[j] += shrinkage * tree.predict(xt);
                }

                if (posteriori[j] > max) {
                    max = posteriori[j];
                    y = j;
                }
            }

            double Z = 0.0;
            for (int i = 0; i < k; i++) {
                posteriori[i] = Math.exp(posteriori[i] - max);
                Z += posteriori[i];
            }

            for (int i = 0; i < k; i++) {
                posteriori[i] /= Z;
            }

            return labels.label(y);
        }
    }

    /**
     * Test the model on a validation dataset.
     *
     * @param data the test data set.
     * @return the predictions with first 1, 2, ..., decision trees.
     */
    public int[][] test(DataFrame data) {
        DataFrame x = formula.x(data);

        int n = x.nrows();
        int ntrees = trees != null ? trees.length : forest[0].length;
        int[][] prediction = new int[ntrees][n];

        if (k == 2) {
            for (int j = 0; j < n; j++) {
                Tuple xj = x.get(j);
                double base = 0;
                for (int i = 0; i < ntrees; i++) {
                    base += shrinkage * trees[i].predict(xj);
                    prediction[i][j] = base > 0 ? 1 : 0;
                }
            }
        } else {
            double[] p = new double[k];
            for (int j = 0; j < n; j++) {
                Tuple xj = x.get(j);
                Arrays.fill(p, 0);
                for (int i = 0; i < ntrees; i++) {
                    for (int l = 0; l < k; l++) {
                        p[l] += shrinkage * forest[l][i].predict(xj);
                    }
                    prediction[i][j] = MathEx.whichMax(p);
                }
            }
        }

        return prediction;
    }
}
