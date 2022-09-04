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

package smile.math.matrix;

import java.util.Arrays;
import smile.math.Math;
import smile.stat.distribution.GaussianDistribution;

/**
 * A dense matrix whose data is stored in a single 1D array of
 * doubles in row major order.
 */
public class RowMajorMatrix implements DenseMatrix {

    /**
     * The original matrix.
     */
    private double[] A;
    /**
     * The number of rows.
     */
    private int nrows;
    /**
     * The number of columns.
     */
    private int ncols;

    /**
     * Constructor.
     * @param A the array of matrix.
     */
    public RowMajorMatrix(double[][] A) {
        this(A.length, A[0].length);

        int pos = 0;
        for (int i = 0; i < nrows; i++) {
            System.arraycopy(A[i], 0, this.A, pos, ncols);
            pos += ncols;
        }
    }

    /**
     * Constructor of all-zero matrix.
     */
    public RowMajorMatrix(int rows, int cols) {
        this.nrows = rows;
        this.ncols = cols;
        A = new double[rows*cols];
    }

    /**
     * Constructor. Fill the matrix with given value.
     */
    public RowMajorMatrix(int rows, int cols, double value) {
        this(rows, cols);
        Arrays.fill(A, value);
    }

    /**
     * Constructor of matrix with normal random values with given mean and standard dev.
     */
    public RowMajorMatrix(int rows, int cols, double mu, double sigma) {
        this(rows, cols);
        GaussianDistribution g = new GaussianDistribution(mu, sigma);

        int n = rows * cols;
        for (int i = 0; i < n; i++) {
            A[i] = g.rand();
        }
    }

    /**
     * Return the one-dimensional array of matrix.
     * @return the one-dimensional array of matrix.
     */
    public double[] array() {
        return A;
    }

    /**
     * Sets the diagonal to the values of <code>diag</code> as long
     * as possible (i.e while there are elements left in diag or the dim of matrix
     * is not big enough.
     */
    public void setDiag(double[] diag) {
        for (int i = 0; i < ncols && i < nrows && i < diag.length; i++) {
            set(i, i, diag[i]);
        }
    }

    @Override
    public int nrows() {
        return nrows;
    }

    @Override
    public int ncols() {
        return ncols;
    }

    @Override
    public double get(int i, int j) {
        return A[i*ncols + j];
    }

    @Override
    public double apply(int i, int j) {
        return A[i*ncols + j];
    }

    @Override
    public RowMajorMatrix set(int i, int j, double x) {
        A[i*ncols + j] = x;
        return this;
    }

    @Override
    public RowMajorMatrix update(int i, int j, double x) {
        A[i*ncols + j] = x;
        return this;
    }

    @Override
    public IMatrix add(int i, int j, double x) {
        A[i*ncols + j] += x;
        return this;
    }

    @Override
    public IMatrix sub(int i, int j, double x) {
        A[i*ncols + j] -= x;
        return this;
    }

    @Override
    public IMatrix mul(int i, int j, double x) {
        A[i*ncols + j] *= x;
        return this;
    }

    @Override
    public IMatrix div(int i, int j, double x) {
        A[i*ncols + j] /= x;
        return this;
    }

    @Override
    public void ax(double[] x, double[] y) {
        int n = Math.min(nrows, y.length);
        int p = Math.min(ncols, x.length);

        Arrays.fill(y, 0.0);
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < p; k++) {
                y[i] += get(i, k) * x[k];
            }
        }
    }

    @Override
    public void axpy(double[] x, double[] y) {
        int n = Math.min(nrows, y.length);
        int p = Math.min(ncols, x.length);

        for (int i = 0; i < n; i++) {
            for (int k = 0; k < p; k++) {
                y[i] += get(i, k) * x[k];
            }
        }
    }

    @Override
    public void axpy(double[] x, double[] y, double b) {
        int n = Math.min(nrows, y.length);
        int p = Math.min(ncols, x.length);

        for (int i = 0; i < n; i++) {
            y[i] *= b;
            for (int k = 0; k < p; k++) {
                y[i] += get(i, k) * x[k];
            }
        }
    }

    @Override
    public void atx(double[] x, double[] y) {
        int n = Math.min(ncols, y.length);
        int p = Math.min(nrows, x.length);

        Arrays.fill(y, 0.0);
        for (int k = 0; k < p; k++) {
            for (int i = 0; i < n; i++) {
                y[i] += get(k, i) * x[k];
            }
        }
    }

    @Override
    public void atxpy(double[] x, double[] y) {
        int n = Math.min(ncols, y.length);
        int p = Math.min(nrows, x.length);

        for (int k = 0; k < p; k++) {
            for (int i = 0; i < n; i++) {
                y[i] += get(k, i) * x[k];
            }
        }
    }

    @Override
    public void atxpy(double[] x, double[] y, double b) {
        int n = Math.min(ncols, y.length);
        int p = Math.min(nrows, x.length);

        for (int i = 0; i < n; i++) {
            y[i] *= b;
        }

        for (int k = 0; k < p; k++) {
            for (int i = 0; i < n; i++) {
                y[i] += get(k, i) * x[k];
            }
        }
    }

    @Override
    public void asolve(double[] b, double[] x) {
        if (nrows != ncols) {
            throw new IllegalStateException("Matrix is not square.");
        }

        for (int i = 0; i < nrows; i++) {
            double Aii = get(i, i);
            x[i] = Aii != 0.0 ? b[i] / Aii : b[i];
        }
    }
}
