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

package smile.projection;

import java.io.Serializable;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.EVD;
import smile.math.matrix.SVD;

/**
 * Principal component analysis. PCA is an orthogonal
 * linear transformation that transforms a number of possibly correlated
 * variables into a smaller number of uncorrelated variables called principal
 * components. The first principal component accounts for as much of the
 * variability in the data as possible, and each succeeding component accounts
 * for as much of the remaining variability as possible. PCA is theoretically
 * the optimum transform for given data in least square terms.
 * PCA can be thought of as revealing the internal structure of the data in
 * a way which best explains the variance in the data. If a multivariate
 * dataset is visualized as a set of coordinates in a high-dimensional data
 * space, PCA supplies the user with a lower-dimensional picture when viewed
 * from its (in some sense) most informative viewpoint.
 * <p>
 * PCA is mostly used as a tool in exploratory data analysis and for making
 * predictive models. PCA involves the calculation of the eigenvalue
 * decomposition of a data covariance matrix or singular value decomposition
 * of a data matrix, usually after mean centering the data for each attribute.
 * The results of a PCA are usually discussed in terms of component scores and
 * loadings.
 * <p>
 * As a linear technique, PCA is built for several purposes: first, it enables us to
 * decorrelate the original variables; second, to carry out data compression,
 * where we pay decreasing attention to the numerical accuracy by which we
 * encode the sequence of principal components; third, to reconstruct the
 * original input data using a reduced number of variables according to a
 * least-squares criterion; and fourth, to identify potential clusters in the data.
 * <p>
 * In certain applications, PCA can be misleading. PCA is heavily influenced
 * when there are outliers in the data. In other situations, the linearity
 * of PCA may be an obstacle to successful data reduction and compression.
 *
 * @see KPCA
 * @see PPCA
 * @see GHA
 * 
 * @author Haifeng Li
 */
public class PCA implements LinearProjection, Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The dimension of feature space.
     */
    private int p;
    /**
     * The dimension of input space.
     */
    private int n;
    /**
     * The sample mean.
     */
    private double[] mu;
    /**
     * The projected sample mean.
     */
    private double[] pmu;
    /**
     * The matrix of variable loadings, whose columns contain the eigenvectors.
     */
    private DenseMatrix eigvectors;
    /**
     * Eigenvalues of principal components.
     */
    private double[] eigvalues;
    /**
     * The proportion of variance contained in each principal component.
     */
    private double[] proportion;
    /**
     * The cumulative proportion of variance contained in principal components.
     */
    private double[] cumulativeProportion;
    /**
     * Projection matrix.
     */
    private DenseMatrix projection;

    /**
     * Constructor.
     * @param mu the mean of samples.
     * @param eigvalues the eigen values of principal components.
     * @param loadings the matrix of variable loadings.
     */
    public PCA(double[] mu, double[] eigvalues, DenseMatrix loadings) {
        this.mu = mu;
        this.eigvalues = eigvalues;
        this.eigvectors = loadings;
        this.n = mu.length;

        proportion = eigvalues.clone();
        MathEx.unitize1(proportion);

        cumulativeProportion = new double[eigvalues.length];
        cumulativeProportion[0] = proportion[0];
        for (int i = 1; i < eigvalues.length; i++) {
            cumulativeProportion[i] = cumulativeProportion[i - 1] + proportion[i];
        }

        setProjection(0.95);
    }

    /**
     * Fits principal component analysis with covariance matrix.
     * @param data training data of which each row is a sample.
     *             If the sample size is larger than the data
     *             dimension and cor = false, SVD is employed for
     *             efficiency. Otherwise, eigen decomposition on
     *             covariance or correlation matrix is performed.
     */
    public static PCA fit(double[][] data) {
        int m = data.length;
        int n = data[0].length;

        double[] mu = MathEx.colMeans(data);
        DenseMatrix x = Matrix.of(data);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                x.sub(i, j, mu[j]);
            }
        }

        double[] eigvalues;
        DenseMatrix eigvectors;
        if (m > n) {
            SVD svd = x.svd();
            eigvalues = svd.getSingularValues();
            for (int i = 0; i < eigvalues.length; i++) {
                eigvalues[i] *= eigvalues[i];
            }

            eigvectors = svd.getV();
        } else {

            DenseMatrix cov = Matrix.zeros(n, n);
            for (int k = 0; k < m; k++) {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j <= i; j++) {
                        cov.add(i, j, x.get(k, i) * x.get(k, j));
                    }
                }
            }

            for (int i = 0; i < n; i++) {
                for (int j = 0; j <= i; j++) {
                    cov.div(i, j, m); // divide m instead of m-1 for S-PLUS compatibility
                    cov.set(j, i, cov.get(i, j));
                }
            }

            cov.setSymmetric(true);
            EVD eigen = cov.eigen();

            DenseMatrix loadings = eigen.getEigenVectors();

            eigvalues = eigen.getEigenValues();
            eigvectors = loadings;
        }

        return new PCA(mu, eigvalues, eigvectors);
    }

    /**
     * Fits principal component analysis with correlation matrix.
     * @param data training data of which each row is a sample.
     *             If the sample size is larger than the data
     *             dimension and cor = false, SVD is employed for
     *             efficiency. Otherwise, eigen decomposition on
     *             covariance or correlation matrix is performed.
     */
    public static PCA cor(double[][] data) {
        int m = data.length;
        int n = data[0].length;

        double[] mu = MathEx.colMeans(data);
        DenseMatrix x = Matrix.of(data);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                x.sub(i, j, mu[j]);
            }
        }

        DenseMatrix cov = Matrix.zeros(n, n);
        for (int k = 0; k < m; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j <= i; j++) {
                    cov.add(i, j, x.get(k, i) * x.get(k, j));
                }
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                cov.div(i, j, m); // divide m instead of m-1 for S-PLUS compatibility
                cov.set(j, i, cov.get(i, j));
            }
        }

        double[] sd = new double[n];
        for (int i = 0; i < n; i++) {
            sd[i] = Math.sqrt(cov.get(i, i));
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                cov.div(i, j, sd[i] * sd[j]);
                cov.set(j, i, cov.get(i, j));
            }
        }

        cov.setSymmetric(true);
        EVD eigen = cov.eigen();

        DenseMatrix loadings = eigen.getEigenVectors();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                loadings.div(i, j, sd[i]);
            }
        }

        return new PCA(mu, eigen.getEigenValues(), loadings);
    }

    /**
     * Returns the center of data.
     */
    public double[] getCenter() {
        return mu;
    }

    /**
     * Returns the variable loading matrix, ordered from largest to smallest
     * by corresponding eigenvalues. The matrix columns contain the eigenvectors.
     */
    public DenseMatrix getLoadings() {
        return eigvectors;
    }

    /**
     * Returns the principal component variances, ordered from largest to smallest,
     * which are the eigenvalues of the covariance or correlation matrix of learning data.
     */
    public double[] getVariance() {
        return eigvalues;
    }

    /**
     * Returns the proportion of variance contained in each principal component,
     * ordered from largest to smallest.
     */
    public double[] getVarianceProportion() {
        return proportion;
    }

    /**
     * Returns the cumulative proportion of variance contained in principal components,
     * ordered from largest to smallest.
     */
    public double[] getCumulativeVarianceProportion() {
        return cumulativeProportion;
    }

    @Override
    public DenseMatrix getProjection() {
        return projection;
    }

    /**
     * Set the projection matrix with given number of principal components.
     * @param p choose top p principal components used for projection.
     */
    public PCA setProjection(int p) {
        if (p < 1 || p > n) {
            throw new IllegalArgumentException("Invalid dimension of feature space: " + p);
        }

        this.p = p;
        projection = Matrix.zeros(p, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                projection.set(j, i, eigvectors.get(i, j));
            }
        }

        pmu = new double[p];
        projection.ax(mu, pmu);

        return this;
    }

    /**
     * Set the projection matrix with top principal components that contain
     * (more than) the given percentage of variance.
     * @param p the required percentage of variance.
     */
    public PCA setProjection(double p) {
        if (p <= 0 || p > 1) {
            throw new IllegalArgumentException("Invalid percentage of variance: " + p);
        }

        for (int k = 0; k < n; k++) {
            if (cumulativeProportion[k] >= p) {
                setProjection(k + 1);
                break;
            }
        }

        return this;
    }

    @Override
    public double[] project(double[] x) {
        if (x.length != n) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, n));
        }

        double[] y = new double[p];
        projection.ax(x, y);
        MathEx.sub(y, pmu);
        return y;
    }

    @Override
    public double[][] project(double[][] x) {
        if (x[0].length != mu.length) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x[0].length, n));
        }

        double[][] y = new double[x.length][p];
        for (int i = 0; i < x.length; i++) {
            projection.ax(x[i], y[i]);
            MathEx.sub(y[i], pmu);
        }
        return y;
    }
}
