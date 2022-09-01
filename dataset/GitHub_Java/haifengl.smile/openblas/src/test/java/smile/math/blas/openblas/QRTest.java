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

package smile.math.blas.openblas;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.math.matrix.FloatMatrix;
import static smile.math.matrix.FloatMatrix.QR;

/**
 *
 * @author Haifeng Li
 */
public class QRTest {

    float[][] A = {
        {0.9000f, 0.4000f, 0.7000f},
        {0.4000f, 0.5000f, 0.3000f},
        {0.7000f, 0.3000f, 0.8000f}
    };
    float[] b = {0.5f, 0.5f, 0.5f};
    float[] x = {-0.2027027f, 0.8783784f, 0.4729730f};
    float[][] B = {
        {0.5f, 0.2f},
        {0.5f, 0.8f},
        {0.5f, 0.3f}
    };
    float[][] X = {
        {-0.2027027f, -1.2837838f},
        { 0.8783784f,  2.2297297f},
        { 0.4729730f,  0.6621622f}
    };

    public QRTest() {
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
    public void testSolve() {
        System.out.println("solve");
        FloatMatrix a = new FloatMatrix(A);
        QR result = a.qr();
        result.solve(b.clone(), b);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], b[i], 1E-7f);
        }
    }

    @Test
    public void testSolveMatrix() {
        System.out.println("solve");
        FloatMatrix a = new FloatMatrix(A);
        QR result = a.qr();
        FloatMatrix b = new FloatMatrix(B);
        result.solve(b);
        for (int i = 0; i < X.length; i++) {
            for (int j = 0; j < X[i].length; j++) {
                assertEquals(X[i][j], b.get(i, j), 1E-7f);
            }
        }
    }
}