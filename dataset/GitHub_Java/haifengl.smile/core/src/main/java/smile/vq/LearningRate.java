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

package smile.vq;

import java.io.Serializable;

/**
 * The learning rate function.
 *
 * @author Haifeng Li
 */
public interface LearningRate extends Serializable {
    /**
     * Returns the learning rate at a given iteration.
     * @param t the order number of current iteration.
     */
    double of(int t);

    /**
     * Returns the linear learning rate lambda (alpha * (1 - t/T)).
     * @param alpha the initial learning rate.
     * @param T the number of iterations.
     */
    static LearningRate linear(double alpha, int T) {
        return t -> alpha * (1.0 - (double) Math.min(t, T-1) / T);
    }

    /**
     * Returns the inverse learning rate lambda (alpha * C / (C + t)).
     * where C is typically a small percentage of the number of iterations
     * T (e.g. T / 100).
     * @param alpha the initial learning rate.
     * @param C a small percentage of the number of iterations.
     */
    static LearningRate inverse(double alpha, int C) {
        return t -> alpha * C / (C + t);
    }

    /**
     * Returns the power series learning rate lambda (alpha * exp(-t/C)).
     * where C is typically smaller than the number of iterations T (e.g. T / 4).
     * @param alpha the initial learning rate.
     * @param C a small percentage of the number of iterations.
     */
    static LearningRate exp(double alpha, int C) {
        return t -> alpha * Math.exp((double) -t / C);
    }
}
