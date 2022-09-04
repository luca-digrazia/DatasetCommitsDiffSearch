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

package smile.regression;

import org.junit.*;
import smile.data.*;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.sort.QuickSort;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import smile.validation.RMSE;
import smile.validation.Validation;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Haifeng Li
 */
public class RandomForestTest {
    long[] seeds = MathEx.seeds(19650218L, 256).limit(200).toArray();

    public RandomForestTest() {
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
    public void testLongley() {
        System.out.println("longley");

        MathEx.setSeed(19650218); // to get repeatable results.
        RandomForest model = RandomForest.fit(Longley.formula, Longley.data, 100, 3, 10, 3, 1.0, Optional.of(Arrays.stream(seeds)));

        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        assertEquals(39293.8193, importance[0], 1E-4);
        assertEquals(6595.3241,  importance[1], 1E-4);
        assertEquals(10242.6828, importance[2], 1E-4);
        assertEquals(34755.6754, importance[3], 1E-4);
        assertEquals(31075.0867, importance[4], 1E-4);
        assertEquals(31644.9317, importance[5], 1E-4);

        System.out.println("----- Progressive RMSE -----");
        double[][] test = model.test(Longley.data);
        for (int i = 0; i < test.length; i++) {
            System.out.format("RMSE with %3d trees: %.4f%n", i+1, RMSE.apply(Longley.y, test[i]));
        }

        double[] prediction = LOOCV.regression(Longley.data, x -> RandomForest.fit(Longley.formula, x));
        double rmse = RMSE.apply(Longley.y, prediction);

        System.out.println("LOOCV RMSE = " + rmse);
    }

    public void test(String name, Formula formula, DataFrame data) {
        System.out.println(name);

        RandomForest model = RandomForest.fit(formula, data);

        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        //double[] prediction = CrossValidation.regression(3, data, x -> RandomForest.fit(formula, x));
        //double rmse = RMSE.apply(formula.y(data).toDoubleArray(), prediction);
        //System.out.format("10-CV RMSE = %.4f%n", rmse);
    }

    @Test
    public void testAll() {
        test("CPU", CPU.formula, CPU.data);
        test("2dplanes", Planes.formula, Planes.data);
        test("abalone", Abalone.formula, Abalone.train);
        test("ailerons", Ailerons.formula, Ailerons.data);
        test("bank32nh", Bank32nh.formula, Bank32nh.data);
        test("autoMPG", AutoMPG.formula, AutoMPG.data);
        test("cal_housing", CalHousing.formula, CalHousing.data);
        test("puma8nh", Puma8NH.formula, Puma8NH.data);
        test("kin8nm", Kin8nm.formula, Kin8nm.data);
    }

    @Test
    public void testRandomForestMerging() throws Exception {
        System.out.println("Random forest merging");

        RandomForest forest1 = RandomForest.fit(Abalone.formula, Abalone.train);
        RandomForest forest2 = RandomForest.fit(Abalone.formula, Abalone.train);
        RandomForest forest = forest1.merge(forest2);
        System.out.format("Forest 1 RMSE = %.4f%n", RMSE.apply(Abalone.testy, Validation.test(forest1, Abalone.test)));
        System.out.format("Forest 2 RMSE = %.4f%n", RMSE.apply(Abalone.testy, Validation.test(forest2, Abalone.test)));
        System.out.format("Merged   RMSE = %.4f%n", RMSE.apply(Abalone.testy, Validation.test(forest, Abalone.test)));
    }
}
