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

package smile.regression;

import smile.math.Math;
import smile.math.kernel.MercerKernel;
import smile.math.matrix.CholeskyDecomposition;
import smile.math.matrix.EigenValueDecomposition;
import smile.math.matrix.LUDecomposition;

/**
 * Gaussian Process for Regression. A Gaussian process is a stochastic process
 * whose realizations consist of random values associated with every point in
 * a range of times (or of space) such that each such random variable has
 * a normal distribution. Moreover, every finite collection of those random
 * variables has a multivariate normal distribution.
 * <p>
 * A Gaussian process can be used as a prior probability distribution over
 * functions in Bayesian inference. Given any set of N points in the desired
 * domain of your functions, take a multivariate Gaussian whose covariance
 * matrix parameter is the Gram matrix of N points with some desired kernel,
 * and sample from that Gaussian. Inference of continuous values with a
 * Gaussian process prior is known as Gaussian process regression.
 * <p>
 * The fitting is performed in the reproducing kernel Hilbert space with
 * the "kernel trick". The loss function is squared-error. This also arises
 * as the kriging estimate of a Gaussian random field in spatial statistics.
 * <p>
 * A significant problem with Gaussian process prediction is that it typically
 * scales as O(n<sup>3</sup>). For large problems (e.g. n &gt; 10,000) both
 * storing the Gram matrix and solving the associated linear systems are
 * prohibitive on modern workstations. An extensive range of proposals have
 * been suggested to deal with this problem. A popular approach is the
 * reduced-rank Approximations of the Gram Matrix, known as Nystrom approximation.
 * Greedy approximation is another popular approach that uses an active set of
 * training points of size m selected from the training set of size n &gt; m.
 * We assume that it is impossible to search for the optimal subset of size m
 * due to combinatorics. The points in the active set could be selected
 * randomly, but in general we might expect better performance if the points
 * are selected greedily w.r.t. some criterion. Recently, researchers had
 * proposed relaxing the constraint that the inducing variables must be a
 * subset of training/test cases, turning the discrete selection problem
 * into one of continuous optimization.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Carl Edward Rasmussen and Chris Williams. Gaussian Processes for Machine Learning, 2006.</li>
 * <li> Joaquin Quinonero-candela,  Carl Edward Ramussen,  Christopher K. I. Williams. Approximation Methods for Gaussian Process Regression. 2007. </li>
 * <li> T. Poggio and F. Girosi. Networks for approximation and learning. Proc. IEEE 78(9):1484-1487, 1990. </li>
 * <li> Kai Zhang and James T. Kwok. Clustered Nystrom Method for Large Scale Manifold Learning and Dimension Reduction. IEEE Transactions on Neural Networks, 2010. </li>
 * <li> </li>
 * </ol>
 * @author Haifeng Li
 */
public class GaussianProcessRegression <T> implements Regression<T> {

    /**
     * The control points in the regression.
     */
    private T[] knots;
    /**
     * The linear weights.
     */
    private double[] w;
    /**
     * The distance functor.
     */
    private MercerKernel<T> kernel;
    /**
     * The shrinkage/regularization parameter.
     */
    private double lambda;

    /**
     * Trainer for Gaussian Process for Regression.
     */
    public static class Trainer<T> extends RegressionTrainer<T> {
        /**
         * The Mercer kernel.
         */
        private MercerKernel<T> kernel;
        /**
         * The shrinkage/regularization parameter.
         */
        private double lambda;

        /**
         * Constructor.
         * 
         * @param kernel the Mercer kernel.
         * @param lambda the shrinkage/regularization parameter.
         */
        public Trainer(MercerKernel<T> kernel, double lambda) {
            this.kernel = kernel;
            this.lambda = lambda;
        }
        
        @Override
        public GaussianProcessRegression<T> train(T[] x, double[] y) {
            return new GaussianProcessRegression<>(x, y, kernel, lambda);
        }
        
        /**
         * Learns a Gaussian Process with given subset of regressors.
         * 
         * @param x training samples.
         * @param y training labels in [0, k), where k is the number of classes.
         * @param t the inducing input, which are pre-selected or inducing samples
         * acting as active set of regressors. Commonly, these can be chosen as
         * the centers of k-means clustering.
         * @return a trained Gaussian Process.
         */
        public GaussianProcessRegression<T> train(T[] x, double[] y, T[] t) {
            return new GaussianProcessRegression<>(x, y, t, kernel, lambda);
        }
    }
    
    /**
     * Constructor. Fitting a regular Gaussian process model.
     * @param x the training dataset.
     * @param y the response variable.
     * @param kernel the Mercer kernel.
     * @param lambda the shrinkage/regularization parameter.
     */
    public GaussianProcessRegression(T[] x, double[] y, MercerKernel<T> kernel, double lambda) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid regularization parameter lambda = " + lambda);
        }

        this.kernel = kernel;
        this.lambda = lambda;
        this.knots = x;
        
        int n = x.length;

        double[][] K = new double[n][n];
        w = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                K[i][j] = kernel.k(x[i], x[j]);
                K[j][i] = K[i][j];
            }

            K[i][i] += lambda;
        }

        CholeskyDecomposition cholesky = new CholeskyDecomposition(K, true);
        cholesky.solve(y, w);
    }

    /**
     * Constructor. Fits an approximate Gaussian process model by the method
     * of subset of regressors.
     * @param x the training dataset.
     * @param y the response variable.
     * @param t the inducing input, which are pre-selected or inducing samples
     * acting as active set of regressors. In simple case, these can be chosen
     * randomly from the training set or as the centers of k-means clustering.
     * @param kernel the Mercer kernel.
     * @param lambda the shrinkage/regularization parameter.
     */
    public GaussianProcessRegression(T[] x, double[] y, T[] t, MercerKernel<T> kernel, double lambda) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid regularization parameter lambda = " + lambda);
        }

        this.kernel = kernel;
        this.lambda = lambda;
        this.knots = t;
        
        int n = x.length;
        int m = t.length;

        double[][] G = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                G[i][j] = kernel.k(x[i], t[j]);
            }
        }

        double[][] K = Math.atamm(G);
        for (int i = 0; i < m; i++) {
            for (int j = 0; j <= i; j++) {
                K[i][j] += lambda * kernel.k(t[i], t[j]);
                K[j][i] = K[i][j];
            }
        }

        double[] b = new double[m];
        w = new double[m];
        Math.atx(G, y, b);

        LUDecomposition lu = new LUDecomposition(K, true);
        lu.solve(b, w);
    }

    /**
     * Constructor. Fits an approximate Gaussian process model with
     * Nystrom approximation of kernel matrix.
     * @param x the training dataset.
     * @param y the response variable.
     * @param t the inducing input for Nystrom approximation. Commonly, these
     * can be chosen as the centers of k-means clustering.
     * @param kernel the Mercer kernel.
     * @param lambda the shrinkage/regularization parameter.
     * @param nystrom THe value of this parameter doesn't really matter. The purpose is to be different from
     *                the constructor of regressor approximation.
     */
    GaussianProcessRegression(T[] x, double[] y, T[] t, MercerKernel<T> kernel, double lambda, boolean nystrom) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid regularization parameter lambda = " + lambda);
        }

        this.kernel = kernel;
        this.lambda = lambda;
        this.knots = x;
        
        int n = x.length;
        int m = t.length;

        double[][] E = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                E[i][j] = kernel.k(x[i], t[j]);
            }
        }

        double[][] W = new double[m][m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j <= i; j++) {
                W[i][j] = kernel.k(t[i], t[j]);
                W[j][i] = W[i][j];
            }
        }

        EigenValueDecomposition eigen = EigenValueDecomposition.decompose(W);
        double[][] U = eigen.getEigenVectors();
        double[][] D = eigen.getD();
        for (int i = 0; i < m; i++) {
            D[i][i] = 1.0 / Math.sqrt(D[i][i]);
        }
        
        double[][] UD = Math.abmm(U, D);
        double[][] UDUt = Math.abtmm(UD, U);
        double[][] L = Math.abmm(E, UDUt);
        
        double[][] LtL = Math.atamm(L);
        for (int i = 0; i < m; i++) {
            LtL[i][i] += lambda;
        }
        
        double[][] invLtL = Math.inverse(LtL);
        double[][] K = Math.abtmm(Math.abmm(L, invLtL), L);
        
        w = new double[n];
        Math.atx(K, y, w);
        
        for (int i = 0; i < n; i++) {
            w[i] = (y[i] - w[i]) / lambda;
        }
    }

    /**
     * Returns the coefficients.
     */
    public double[] coefficients() {
        return w;
    }

    /**
     * Returns the shrinkage parameter.
     */
    public double shrinkage() {
        return lambda;
    }

    @Override
    public double predict(T x) {
        double f = 0.0;
        
        for (int i = 0; i < knots.length; i++) {
            f += w[i] * kernel.k(x, knots[i]);
        }

        return f;
    }
}
