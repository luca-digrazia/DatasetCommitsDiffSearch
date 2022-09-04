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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Optional;

import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.math.MathEx;
import smile.sort.QuickSort;

/**
 * A classifier assigns an input object into one of a given number of categories.
 * The input object is formally termed an instance, and the categories are
 * termed classes. The instance is usually described by a vector of features,
 * which together constitute a description of all known characteristics of the
 * instance.
 * <p>
 * Classification normally refers to a supervised procedure, i.e. a procedure
 * that produces an inferred function to predict the output value of new
 * instances based on a training set of pairs consisting of an input object
 * and a desired output value. The inferred function is called a classifier
 * if the output is discrete or a regression function if the output is
 * continuous.
 * 
 * @param <T> the type of input object
 * 
 * @author Haifeng Li
 */
public interface Classifier<T> extends Serializable {
    /**
     * Predicts the class label of an instance.
     *
     * @param x the instance to be classified.
     * @return the predicted class label.
     */
    int predict(T x);

    /**
     * Returns the real-valued decision function value.
     *
     * @param x the instance to be classified.
     * @return the prediction score.
     */
    default double f(T x) {
        throw new UnsupportedOperationException();
    }

    /**
     * Predicts the class label of an instance.
     * @param x a tuple instance.
     * @return the predicted class label.
     */
    default int predict(Tuple x) {
        throw new UnsupportedOperationException();
    }

    /**
     * Predicts the class labels of an array of instances.
     *
     * @param x the instances to be classified.
     * @return the predicted class labels.
     */
    default int[] predict(T[] x) {
        return Arrays.stream(x).mapToInt(xi -> predict(xi)).toArray();
    }

    /**
     * Predicts the class labels of a data frame.
     *
     * @param data the data frame.
     * @return the predicted class labels.
     */
    default int[] predict(DataFrame data) {
        return data.stream().mapToInt(x -> predict(x)).toArray();
    }

    /** Returns the formula associated with the model. */
    default Optional<Formula> formula() {
        return Optional.empty();
    }

    /** Returns the design matrix schema. */
    default Optional<StructType> schema() {
        return Optional.empty();
    }
}
