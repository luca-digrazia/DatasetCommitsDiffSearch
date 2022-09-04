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

package smile.validation;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class LOOCVTest {

    public LOOCVTest() {
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
    public void testComplete() {
        System.out.println("Complete");
        int n = 57;
        int[][] splits = LOOCV.of(n);
        boolean[] hit = new boolean[n];
        for (int i = 0; i < n; i++) {
            Arrays.fill(hit, false);

            for (int j : splits[i]) {
                hit[j] = true;
            }

            assertFalse(hit[i]);
            hit[i] = true;

            for (int j = 0; j < n; j++) {
                assertTrue(hit[j]);
            }
        }
    }

    @Test
    public void testOrthogonal() {
        System.out.println("Orthogonal");
        int n = 57;
        int[][] splits = LOOCV.of(n);
        for (int i = 0; i < n; i++) {
            assertTrue(Arrays.binarySearch(splits[i], i) < 0);
        }
    }
}