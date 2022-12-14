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

package smile.feature;

import smile.math.Math;
import smile.data.Attribute;

/**
 * Standardizes numeric feature to 0 mean and unit variance.
 * Standardization makes an assumption that the data follows
 * a Gaussian distribution and are also not robust when outliers present.
 * A robust alternative is to subtract the median and divide by the IQR
 * by <code>RobustStandardizer</code>.
 *
 * @author Haifeng Li
 */
public class Standardizer implements FeatureTransform {
    /**
     * Mean or median.
     */
    double[] mu;
    /**
     * Standard deviation or IQR.
     */
    double[] std;

    /** Default constructor. */
    Standardizer() {

    }

    /**
     * Constructor. Learn the scaling parameters from the data.
     * @param data The training data to learn scaling parameters.
     *             The data will not be modified.
     */
    public Standardizer(double[][] data) {
        mu = Math.colMeans(data);
        std = Math.colSds(data);

        for (int i = 0; i < std.length; i++) {
            if (Math.isZero(std[i]))
                std[i] = 1.0;
        }
    }

    /**
     * Constructor. Learn the scaling parameters from the data.
     * @param attributes The variable attributes. Of which, numeric variables
     *                   will be standardized.
     * @param data The training data to learn scaling parameters.
     *             The data will not be modified.
     */
    public Standardizer(Attribute[] attributes, double[][] data) {
        mu = Math.colMeans(data);
        std = Math.colSds(data);

        for (int i = 0; i < std.length; i++) {
            if (attributes[i].getType() != Attribute.Type.NUMERIC) {
                mu[i] = Double.NaN;
            }

            if (Math.isZero(std[i])) {
                std[i] = 1.0;
            }
        }
    }

    /**
     * Standardizes the input vector.
     * @param x a vector to be standardized. The vector will be modified on output.
     * @return the input vector.
     */
    @Override
    public double[] transform(double[] x) {
        if (x.length != mu.length) {
            throw new IllegalArgumentException(String.format("Invalid vector size %d, expected %d", x.length, mu.length));
        }

        for (int i = 0; i < x.length; i++) {
            if (!Double.isNaN(mu[i])) {
                x[i] = (x[i] - mu[i]) / std[i];
            }
        }

        return x;
    }
}
