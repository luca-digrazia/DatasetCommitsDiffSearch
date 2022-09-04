/*
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
 */

package smile.regression;

/**
 * Ensemble methods use multiple learning algorithms to obtain better
 * predictive performance than could be obtained from any of the constituent
 * learning algorithms alone.
 *
 * @param <T> the type of input object
 *
 * @author Haifeng Li
 */
public class Ensemble<T> implements Regression<T> {
    /** The base models. */
    private Regression<T>[] models;

    /**
     * Returns an ensemble model.
     * @param models the base models.
     */
    @SafeVarargs
    public Ensemble(Regression<T>... models) {
        this.models = models;
    }

    @Override
    public boolean online() {
        return false;
    }

    @Override
    public double predict(T x) {
        double y = 0;
        for (Regression<T> model : models) {
            y += model.predict(x);
        }
        return y / models.length;
    }
}
