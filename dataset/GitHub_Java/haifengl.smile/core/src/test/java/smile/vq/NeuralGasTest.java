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

import smile.data.USPS;
import smile.math.MathEx;
import smile.validation.MutualInformation;
import smile.validation.NormalizedMutualInformation;
import smile.validation.RandIndex;
import smile.validation.AdjustedRandIndex;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class NeuralGasTest {
    
    public NeuralGasTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    @Test(expected = Test.None.class)
    public void testUSPS() {
        System.out.println("USPS");
        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = USPS.x;
        double[][] testx = USPS.testx;

        int epochs = 20;
        NeuralGas model = new NeuralGas(NeuralGas.random(400, x),
                LearningRate.exp(0.3, x.length * epochs / 2),
                LearningRate.exp(30, x.length * epochs / 8),
                LearningRate.exp(20, x.length * epochs / -2));

        for (int i = 0; i < epochs; i++) {
            for (int j : MathEx.permutate(x.length)) {
                model.update(x[j]);
            }
        }

        double error = 0.0;
        for (double[] xi : x) {
            double[] yi = model.quantize(xi).get();
            error += MathEx.distance(xi, yi);
        }
        error /= x.length;
        System.out.format("Training Quantization Error = %.4f%n", error);
        assertEquals(5.7011, error, 1E-4);

        error = 0.0;
        for (double[] xi : testx) {
            double[] yi = model.quantize(xi).get();
            error += MathEx.distance(xi, yi);
        }
        error /= testx.length;

        System.out.format("Test Quantization Error = %.4f%n", error);
        assertEquals(6.5534, error, 1E-4);
    }
}
