/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 ******************************************************************************/

package smile.math.kernel;

/**
 * The Gaussian Kernel.
 * <p>
 * <pre>
 *     k(u, v) = e<sup>-||u-v||<sup>2</sup> / (2 * &sigma;<sup>2</sup>)</sup>
 * </pre>
 * where <code>&sigma; &gt; 0</code> is the scale parameter of the kernel.
 * <p>
 * The Gaussian kernel is a good choice for a great deal of applications,
 * although sometimes it is remarked as being overused.

 * @author Haifeng Li
 */
public class Gaussian implements IsotropicKernel {
    private static final long serialVersionUID = 2L;

    /**
     * The width of the kernel.
     */
    private double gamma;

    /**
     * Constructor.
     * @param sigma the smooth/width parameter of Gaussian kernel.
     */
    public Gaussian(double sigma) {
        if (sigma <= 0) {
            throw new IllegalArgumentException("sigma is not positive: " + sigma);
        }

        this.gamma = 0.5 / (sigma * sigma);
    }

    @Override
    public String toString() {
        return String.format("Gaussian(sigma = %.4f)", Math.sqrt(0.5/gamma));
    }

    @Override
    public double k(double dist) {
        return Math.exp(-gamma * dist);
    }
}
