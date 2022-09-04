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

package smile.manifold;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.io.Read;
import smile.math.MathEx;
import smile.projection.PCA;
import smile.util.Paths;
import org.apache.commons.csv.CSVFormat;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class TSNETest {

    public TSNETest() {
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
    public void test() throws Exception {
        System.out.println("tSNE");

        MathEx.setSeed(19650218); // to get repeatable results.

        CSVFormat format = CSVFormat.DEFAULT.withDelimiter(' ');
        double[][] mnist = Read.csv(Paths.getTestData("mnist/mnist2500_X.txt"), format).toArray();

        PCA pca = PCA.fit(mnist);
        pca.setProjection(50);
        double[][] X = pca.project(mnist);

        long start = System.currentTimeMillis();
        TSNE tsne = new TSNE(X, 2, 20, 200, 1000);
        long end = System.currentTimeMillis();
        System.out.format("t-SNE takes %.2f seconds\n", (end - start) / 1000.0);

        assertEquals(-6.9417527754508255, tsne.coordinates[0][0], 1E-7);
        assertEquals(-4.877894494201111, tsne.coordinates[0][1], 1E-7);
        assertEquals(14.22564317852574, tsne.coordinates[100][0], 1E-7);
        assertEquals(-5.034221622144134, tsne.coordinates[100][1], 1E-7);
        assertEquals(-11.650229498504789, tsne.coordinates[1000][0], 1E-7);
        assertEquals(-14.799602105478963, tsne.coordinates[1000][1], 1E-7);
        assertEquals(15.210827467634445, tsne.coordinates[2000][0], 1E-7);
        assertEquals(-4.216214533598115, tsne.coordinates[2000][1], 1E-7);
    }
}
