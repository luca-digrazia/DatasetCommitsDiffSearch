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

package smile.validation;

/**
 * The false discovery rate (FDR) is ratio of false positives
 * to combined true and false positives, which is actually 1 - precision.
 * <p>
 * FDR = FP / (TP + FP)
 *
 * @author Haifeng Li
 */
public class FDR implements ClassificationMeasure {
    public final static FDR instance = new FDR();

    @Override
    public double measure(int[] truth, int[] prediction) {
        return apply(truth, prediction);
    }

    /** Calculates the false discovery rate. */
    public static double apply(int[] truth, int[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int fp = 0;
        int p = 0;
        for (int i = 0; i < truth.length; i++) {
            if (truth[i] != 0 && truth[i] != 1) {
                throw new IllegalArgumentException("FDR can only be applied to binary classification: " + truth[i]);
            }

            if (prediction[i] != 0 && prediction[i] != 1) {
                throw new IllegalArgumentException("FDR can only be applied to binary classification: " + prediction[i]);
            }

            if (prediction[i] == 1) {
                p++;

                if (truth[i] != 1) {
                    fp++;
                }
            }
        }

        return (double) fp / p;
    }

    @Override
    public String toString() {
        return "False Discovery Rate";
    }
}
