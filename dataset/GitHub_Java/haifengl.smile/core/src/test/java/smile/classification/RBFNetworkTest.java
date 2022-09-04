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

package smile.classification;

import smile.base.rbf.RBF;
import smile.clustering.KMeans;
import smile.data.*;
import smile.math.distance.EuclideanDistance;
import smile.math.MathEx;
import smile.math.rbf.GaussianRadialBasis;
import smile.validation.*;
import smile.validation.metric.Error;
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
@SuppressWarnings("unused")
public class RBFNetworkTest {

    public RBFNetworkTest() {
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

    @Test
    public void testIris() {
        System.out.println("Iris");

        MathEx.setSeed(19650218); // to get repeatable results.

        ClassificationMetrics metrics = LOOCV.classification(Iris.x, Iris.y,
                (x, y) -> RBFNetwork.fit(x, y, RBF.fit(x, 10)));

        System.out.println("RBF Network: " + metrics);
        assertEquals(5, metrics.accuracy);

        metrics = LOOCV.classification(Iris.x, Iris.y,
                (x, y) -> RBFNetwork.fit(x, y, RBF.fit(x, 10), true));

        System.out.println("Normalized RBF Network: " + metrics);
        assertEquals(4, metrics.accuracy);
    }

    @Test
    public void testPenDigits() {
        System.out.println("Pen Digits");

        MathEx.setSeed(19650218); // to get repeatable results.
        ClassificationValidations<RBFNetwork> result = CrossValidation.classification(10, PenDigits.x, PenDigits.y,
                (x, y) -> RBFNetwork.fit(x, y, RBF.fit(x, 50)));

        System.out.println("RBF Network: " + result);
        assertEquals(628, result.avg.accuracy);

        result = CrossValidation.classification(10, PenDigits.x, PenDigits.y,
                (x, y) -> RBFNetwork.fit(x, y, RBF.fit(x, 50), true));

        System.out.println("Normalized RBF Network: " + result);
        assertEquals(607, result.avg.accuracy);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        ClassificationValidations<RBFNetwork> result = CrossValidation.classification(10, BreastCancer.x, BreastCancer.y,
                (x, y) -> RBFNetwork.fit(x, y, RBF.fit(x, 30)));

        System.out.println("RBF Network: " + result);
        assertEquals(32, result.avg.accuracy);

        result = CrossValidation.classification(10, BreastCancer.x, BreastCancer.y,
                (x, y) -> RBFNetwork.fit(x, y, RBF.fit(x, 30), true));

        System.out.println("Normalized RBF Network: " + result);
        assertEquals(38, result.avg.accuracy);
    }

    @Test
    public void testSegment() {
        System.out.println("Segment");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = MathEx.clone(Segment.x);
        double[][] testx = MathEx.clone(Segment.testx);
        MathEx.standardize(x);
        MathEx.standardize(testx);

        RBFNetwork<double[]> model = RBFNetwork.fit(x, Segment.y, RBF.fit(x, 30));
        int[] prediction = Validation.test(model, testx);
        int error = Error.of(Segment.testy, prediction);
        System.out.println("RBF Network Error = " + error);
        assertEquals(123, error);

        model = RBFNetwork.fit(x, Segment.y, RBF.fit(x, 30), true);
        prediction = Validation.test(model, testx);
        error = Error.of(Segment.testy, prediction);
        System.out.println("Normalized RBF Network Error = " + error);
        assertEquals(110, error);
    }

    @Test(expected = Test.None.class)
    public void testUSPS() throws Exception {
        System.out.println("USPS");

        MathEx.setSeed(19650218); // to get repeatable results.

        KMeans kmeans = KMeans.fit(USPS.x, 200);
        EuclideanDistance distance = new EuclideanDistance();
        RBF<double[]>[] neurons = RBF.of(kmeans.centroids, new GaussianRadialBasis(8.0), distance);

        RBFNetwork<double[]> model = RBFNetwork.fit(USPS.x, USPS.y, neurons);
        int[] prediction = Validation.test(model, USPS.testx);
        int error = Error.of(USPS.testy, prediction);
        System.out.println("RBF Network Error = " + error);
        assertEquals(142, error);

        model = RBFNetwork.fit(USPS.x, USPS.y, neurons, true);
        prediction = Validation.test(model, USPS.testx);
        error = Error.of(USPS.testy, prediction);
        System.out.println("Normalized RBF Network Error = " + error);
        assertEquals(143, error);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }
}