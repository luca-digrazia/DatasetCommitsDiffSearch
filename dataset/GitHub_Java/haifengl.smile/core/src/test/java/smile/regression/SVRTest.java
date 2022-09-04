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

package smile.regression;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.*;
import smile.math.kernel.GaussianKernel;
import smile.math.MathEx;
import smile.validation.*;
import smile.validation.metric.RMSE;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author Haifeng Li
 */
public class SVRTest {

    public SVRTest() {
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
    public void testLongley() throws Exception {
        System.out.println("longley");

        RegressionMetrics metrics = LOOCV.regression(Longley.x, Longley.y, (x, y) -> SVR.fit(x, y, 2.0, 10.0, 1E-3));

        System.out.println("LOOCV RMSE = " + metrics.rmse);
        assertEquals(1.6140026106705365, metrics.rmse, 1E-4);

        Regression<double[]> model = SVR.fit(Longley.x, Longley.y, 2.0, 10.0, 1E-3);
        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    @Test
    public void testCPU() {
        System.out.println("CPU");

        double[][] x = MathEx.clone(CPU.x);
        MathEx.standardize(x);

        MathEx.setSeed(19650218); // to get repeatable results.
        RegressionValidations<Regression<double[]>> result = CrossValidation.regression(10, x, CPU.y,
                (xi, yi) -> SVR.fit(xi, yi,40.0, 10.0, 1E-3));

        System.out.println(result);
        assertEquals(54.63430240465948, result.avg.rmse, 1E-4);
    }

    @Test
    public void tesProstate() {
        System.out.println("Prostate");

        GaussianKernel kernel = new GaussianKernel(6.0);

        RegressionValidation<Regression<double[]>> result = RegressionValidation.of(Prostate.x, Prostate.y,
                Prostate.testx, Prostate.testy, (x, y) -> SVR.fit(x, y, kernel, 0.5, 5, 1E-3));

        System.out.println(result);
        assertEquals(0.9112183360712871, result.metrics.rmse, 1E-4);
    }

    @Test
    public void tesAbalone() {
        System.out.println("Abalone");
        GaussianKernel kernel = new GaussianKernel(5.0);
        KernelMachine<double[]> model = SVR.fit(Abalone.x, Abalone.y, kernel, 1.5, 100, 1E-3);

        double[] prediction = Validation.test(model, Abalone.testx);
        double rmse = RMSE.of(Abalone.testy, prediction);
        System.out.println("Test RMSE = " + rmse);
        assertEquals(2.1098880372502586, rmse, 1E-3);
    }

    @Test
    public void tesDiabetes() {
        System.out.println("Diabetes");

        MathEx.setSeed(19650218); // to get repeatable results.
        GaussianKernel kernel = new GaussianKernel(5.0);
        RegressionValidations<Regression<double[]>> result = CrossValidation.regression(10, Diabetes.x, Diabetes.y,
                (x, y) -> SVR.fit(x, y, kernel, 50, 1000, 1E-3));

        System.out.println(result);
        assertEquals(61.710080572519516, result.avg.rmse, 1E-4);
    }
}