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

package smile.netlib;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.math.matrix.SVD;

/**
 *
 * @author Haifeng Li
 */
public class SVDTest {

    public SVDTest() {
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

    /**
     * Test of decompose method, of class SingularValueDecomposition.
     */
    @Test
    public void testDecompose1() {
        System.out.println("decompose symm");
        double[][] A = {
            {0.9000, 0.4000, 0.7000},
            {0.4000, 0.5000, 0.3000},
            {0.7000, 0.3000, 0.8000}
        };

        double[] s = {1.7498382, 0.3165784, 0.1335834};

        double[][] U = {
            {0.6881997, -0.07121225, 0.7220180},
            {0.3700456, 0.89044952, -0.2648886},
            {0.6240573, -0.44947578, -0.6391588}
        };

        double[][] V = {
            {0.6881997, -0.07121225, 0.7220180},
            {0.3700456, 0.89044952, -0.2648886},
            {0.6240573, -0.44947578, -0.6391588}
        };

        SVD result = Matrix.of(A).svd();
        assertTrue(MathEx.equals(s, result.getSingularValues(), 1E-7));

        assertEquals(U.length, result.getU().nrows());
        assertEquals(U[0].length, result.getU().ncols());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(result.getU().get(i, j)), 1E-7);
            }
        }

        assertEquals(V.length, result.getV().nrows());
        assertEquals(V[0].length, result.getV().ncols());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(result.getV().get(i, j)), 1E-7);
            }
        }
    }

    /**
     * Test of decompose method, of class SingularValueDecomposition.
     */
    @Test
    public void testDecompose2() {
        System.out.println("decompose asymm");
        double[][] A = {
            {1.19720880, -1.8391378, 0.3019585, -1.1165701, -1.7210814, 0.4918882, -0.04247433},
            {0.06605075, 1.0315583, 0.8294362, -0.3646043, -1.6038017, -0.9188110, -0.63760340},
            {-1.02637715, 1.0747931, -0.8089055, -0.4726863, -0.2064826, -0.3325532, 0.17966051},
            {-1.45817729, -0.8942353, 0.3459245, 1.5068363, -2.0180708, -0.3696350, -1.19575563},
            {-0.07318103, -0.2783787, 1.2237598, 0.1995332, 0.2545336, -0.1392502, -1.88207227},
            {0.88248425, -0.9360321, 0.1393172, 0.1393281, -0.3277873, -0.5553013, 1.63805985},
            {0.12641406, -0.8710055, -0.2712301, 0.2296515, 1.1781535, -0.2158704, -0.27529472}
        };

        double[] s = {3.8589375, 3.4396766, 2.6487176, 2.2317399, 1.5165054, 0.8109055, 0.2706515};

        double[][] U = {
            {-0.3082776, 0.77676231, 0.01330514, 0.23231424, -0.47682758, 0.13927109, 0.02640713},
            {-0.4013477, -0.09112050, 0.48754440, 0.47371793, 0.40636608, 0.24600706, -0.37796295},
            {0.0599719, -0.31406586, 0.45428229, -0.08071283, -0.38432597, 0.57320261, 0.45673993},
            {-0.7694214, -0.12681435, -0.05536793, -0.62189972, -0.02075522, -0.01724911, -0.03681864},
            {-0.3319069, -0.17984404, -0.54466777, 0.45335157, 0.19377726, 0.12333423, 0.55003852},
            {0.1259351, 0.49087824, 0.16349687, -0.32080176, 0.64828744, 0.20643772, 0.38812467},
            {0.1491884, 0.01768604, -0.47884363, -0.14108924, 0.03922507, 0.73034065, -0.43965505}
        };

        double[][] V = {
            {-0.2122609, -0.54650056, 0.08071332, -0.43239135, -0.2925067, 0.1414550, 0.59769207},
            {-0.1943605, 0.63132116, -0.54059857, -0.37089970, -0.1363031, 0.2892641, 0.17774114},
            {0.3031265, -0.06182488, 0.18579097, -0.38606409, -0.5364911, 0.2983466, -0.58642548},
            {0.1844063, 0.24425278, 0.25923756, 0.59043765, -0.4435443, 0.3959057, 0.37019098},
            {-0.7164205, 0.30694911, 0.58264743, -0.07458095, -0.1142140, -0.1311972, -0.13124764},
            {-0.1103067, -0.10633600, 0.18257905, -0.03638501, 0.5722925, 0.7784398, -0.09153611},
            {-0.5156083, -0.36573746, -0.47613340, 0.41342817, -0.2659765, 0.1654796, -0.32346758}
        };

        SVD result = Matrix.of(A).svd();
        assertTrue(MathEx.equals(s, result.getSingularValues(), 1E-7));

        assertEquals(U.length, result.getU().nrows());
        assertEquals(U[0].length, result.getU().ncols());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(result.getU().get(i, j)), 1E-7);
            }
        }

        assertEquals(V.length, result.getV().nrows());
        assertEquals(V[0].length, result.getV().ncols());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(result.getV().get(i, j)), 1E-7);
            }
        }
    }

    /**
     * Test of decompose method, of class SingularValueDecomposition.
     */
    @Test
    public void testDecompose3() {
        System.out.println("decompose m = n+1");
        double[][] A = {
            {1.19720880, -1.8391378, 0.3019585, -1.1165701, -1.7210814, 0.4918882},
            {0.06605075, 1.0315583, 0.8294362, -0.3646043, -1.6038017, -0.9188110},
            {-1.02637715, 1.0747931, -0.8089055, -0.4726863, -0.2064826, -0.3325532},
            {-1.45817729, -0.8942353, 0.3459245, 1.5068363, -2.0180708, -0.3696350},
            {-0.07318103, -0.2783787, 1.2237598, 0.1995332, 0.2545336, -0.1392502},
            {0.88248425, -0.9360321, 0.1393172, 0.1393281, -0.3277873, -0.5553013},
            {0.12641406, -0.8710055, -0.2712301, 0.2296515, 1.1781535, -0.2158704}
        };

        double[] s = {3.6447007, 3.1719019, 2.4155022, 1.6952749, 1.0349052, 0.6735233};

        double[][] U = {
            {-0.66231606, 0.51980064, -0.26908096, -0.33255132, 0.1998343961, 0.25344461},
            {-0.30950323, -0.38356363, -0.57342388, 0.43584295, -0.2842953084, 0.06773874},
            {0.17209598, -0.40152786, -0.25549740, -0.47194228, -0.1795895194, 0.60960160},
            {-0.58855512, -0.52801793, 0.59486615, -0.13721651, -0.0004042427, -0.01414006},
            {-0.06838272, 0.03221968, 0.14785619, 0.64819245, 0.3955572924, 0.53374206},
            {-0.23683786, 0.25613876, 0.07459517, 0.19208798, -0.7235935956, -0.10586201},
            {0.16959559, 0.27570548, 0.39014092, 0.02900709, -0.4085787191, 0.51310416}
        };

        double[][] V = {
            {-0.08624942, 0.642381656, -0.35639657, 0.2600624, -0.303192728, -0.5415995},
            {0.46728106, -0.567452824, -0.56054543, 0.1717478, 0.067268188, -0.3337846},
            {-0.26399674, -0.005897261, -0.02438536, 0.8302504, 0.448103782, 0.1989057},
            {-0.03389306, -0.296652409, 0.68563317, 0.2309273, -0.145824242, -0.6051146},
            {0.83642784, 0.352498963, 0.29305340, 0.2264531, 0.006202435, 0.1973149},
            {0.06127719, 0.230326187, 0.04693098, -0.3300697, 0.825499232, -0.3880689}
        };

        SVD result = Matrix.of(A).svd();
        assertTrue(MathEx.equals(s, result.getSingularValues(), 1E-7));

        assertEquals(U.length, result.getU().nrows());
        assertEquals(U[0].length, result.getU().ncols());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(result.getU().get(i, j)), 1E-7);
            }
        }

        assertEquals(V.length, result.getV().nrows());
        assertEquals(V[0].length, result.getV().ncols());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(result.getV().get(i, j)), 1E-7);
            }
        }
    }

    /**
     * Test of decompose method, of class SingularValueDecomposition.
     */
    @Test
    public void testDecompose4() {
        System.out.println("decompose m = n+2");
        double[][] A = {
            {1.19720880, -1.8391378, 0.3019585, -1.1165701, -1.7210814},
            {0.06605075, 1.0315583, 0.8294362, -0.3646043, -1.6038017},
            {-1.02637715, 1.0747931, -0.8089055, -0.4726863, -0.2064826},
            {-1.45817729, -0.8942353, 0.3459245, 1.5068363, -2.0180708},
            {-0.07318103, -0.2783787, 1.2237598, 0.1995332, 0.2545336},
            {0.88248425, -0.9360321, 0.1393172, 0.1393281, -0.3277873},
            {0.12641406, -0.8710055, -0.2712301, 0.2296515, 1.1781535}
        };

        double[] s = {3.6392869, 3.0965326, 2.4131673, 1.6285557, 0.7495616};

        double[][] U = {
            {-0.68672751, -0.47077690, -0.27062524, 0.30518577, 0.35585700},
            {-0.28422169, 0.33969351, -0.56700359, -0.38788214, -0.15789372},
            {0.18880503, 0.39049353, -0.26448028, 0.50872376, 0.42411327},
            {-0.56957699, 0.56761727, 0.58111879, 0.11662686, -0.01347444},
            {-0.06682433, -0.04559753, 0.15586923, -0.68802278, 0.60990585},
            {-0.23677832, -0.29935481, 0.09428368, -0.03224480, -0.50781217},
            {0.16440378, -0.31082218, 0.40550635, 0.09794049, 0.19627380}
        };

        double[][] V = {
            {-0.10646320, -0.668422750, -0.33744231, -0.1953744, -0.6243702},
            {0.48885825, 0.546411345, -0.57018018, -0.2348795, -0.2866678},
            {-0.26164973, 0.002221196, -0.01788181, -0.9049532, 0.3350739},
            {-0.02353895, 0.306904408, 0.68247803, -0.2353931, -0.6197333},
            {0.82502638, -0.400562630, 0.30810911, -0.1797507, 0.1778750}
        };

        SVD result = Matrix.of(A).svd();
        assertTrue(MathEx.equals(s, result.getSingularValues(), 1E-7));

        assertEquals(U.length, result.getU().nrows());
        assertEquals(U[0].length, result.getU().ncols());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(result.getU().get(i, j)), 1E-7);
            }
        }

        assertEquals(V.length, result.getV().nrows());
        assertEquals(V[0].length, result.getV().ncols());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(result.getV().get(i, j)), 1E-7);
            }
        }
    }

    /**
     * Test of decompose method, of class SingularValueDecomposition.
     */
    @Test
    public void testDecompose5() {
        System.out.println("decompose m = n+3");
        double[][] A = {
            {1.19720880, -1.8391378, 0.3019585, -1.1165701},
            {0.06605075, 1.0315583, 0.8294362, -0.3646043},
            {-1.02637715, 1.0747931, -0.8089055, -0.4726863},
            {-1.45817729, -0.8942353, 0.3459245, 1.5068363},
            {-0.07318103, -0.2783787, 1.2237598, 0.1995332},
            {0.88248425, -0.9360321, 0.1393172, 0.1393281},
            {0.12641406, -0.8710055, -0.2712301, 0.2296515}
        };

        double[] s = {3.2188437, 2.5504483, 1.7163918, 0.9212875};

        double[][] U = {
            {-0.7390710, 0.15540183, 0.093738524, -0.60555964},
            {0.1716777, 0.26405327, -0.616548935, -0.11171885},
            {0.4583068, 0.19615535, 0.365025826, -0.56118537},
            {0.1185448, -0.88710768, -0.004538332, -0.24629659},
            {-0.1055393, -0.19831478, -0.634814754, -0.26986239},
            {-0.3836089, -0.06331799, 0.006896881, 0.41026537},
            {-0.2047156, -0.19326474, 0.273456965, 0.06389058}
        };

        double[][] V = {
            {-0.5820171, 0.4822386, -0.12201399, 0.6432842},
            {0.7734720, 0.4993237, -0.27962029, 0.2724507},
            {-0.1670058, -0.1563235, -0.94966302, -0.2140379},
            {0.1873664, -0.7026270, -0.07117046, 0.6827473}
        };

        SVD result = Matrix.of(A).svd();
        assertTrue(MathEx.equals(s, result.getSingularValues(), 1E-7));

        assertEquals(U.length, result.getU().nrows());
        assertEquals(U[0].length, result.getU().ncols());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(result.getU().get(i, j)), 1E-7);
            }
        }

        assertEquals(V.length, result.getV().nrows());
        assertEquals(V[0].length, result.getV().ncols());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(result.getV().get(i, j)), 1E-7);
            }
        }
    }

    /**
     * Test of decompose method, of class SingularValueDecomposition.
     */
    @Test
    public void testDecompose6() {
        System.out.println("decompose m = n-1");
        double[][] A = {
            {1.19720880, -1.8391378, 0.3019585, -1.1165701, -1.7210814, 0.4918882, -0.04247433},
            {0.06605075, 1.0315583, 0.8294362, -0.3646043, -1.6038017, -0.9188110, -0.63760340},
            {-1.02637715, 1.0747931, -0.8089055, -0.4726863, -0.2064826, -0.3325532, 0.17966051},
            {-1.45817729, -0.8942353, 0.3459245, 1.5068363, -2.0180708, -0.3696350, -1.19575563},
            {-0.07318103, -0.2783787, 1.2237598, 0.1995332, 0.2545336, -0.1392502, -1.88207227},
            {0.88248425, -0.9360321, 0.1393172, 0.1393281, -0.3277873, -0.5553013, 1.63805985},};

        double[] s = {3.8244094, 3.4392541, 2.3784254, 2.1694244, 1.5150752, 0.4743856};

        double[][] U = {
            {0.31443091, 0.77409564, -0.06404561, 0.2362505, 0.48411517, 0.08732402},
            {0.37429954, -0.08997642, 0.33948894, 0.7403030, -0.37663472, -0.21598420},
            {-0.08460683, -0.30944648, 0.49768196, 0.1798789, 0.40657776, 0.67211259},
            {0.78096534, -0.13597601, 0.27845058, -0.5407806, 0.01748391, -0.03632677},
            {0.35762337, -0.18789909, -0.68652942, 0.1670810, -0.20242003, 0.54459792},
            {-0.12678093, 0.49307962, 0.29000123, -0.2083774, -0.64590538, 0.44281315}
        };

        double[][] V = {
            {-0.2062642, 0.54825338, -0.27956720, 0.3408979, -0.2925761, -0.412469519},
            {-0.2516350, -0.62127194, 0.28319612, 0.5322251, -0.1297528, -0.410270389},
            {0.3043552, 0.05848409, -0.35475338, 0.2434904, -0.5456797, 0.040325347},
            {0.2047160, -0.24974630, 0.01491918, -0.6588368, -0.4616580, -0.465507184},
            {-0.6713331, -0.30795185, -0.57548345, -0.1976943, -0.1242132, 0.261610893},
            {-0.1122210, 0.10728081, -0.28476779, -0.1527923, 0.5474147, -0.612188492},
            {-0.5443460, 0.37590198, 0.55072289, -0.2115256, -0.2675392, -0.003003781}
        };

        SVD result = Matrix.of(A).svd();
        assertTrue(MathEx.equals(s, result.getSingularValues(), 1E-7));

        assertEquals(U.length, result.getU().nrows());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(result.getU().get(i, j)), 1E-7);
            }
        }

        assertEquals(V.length, result.getV().nrows());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(result.getV().get(i, j)), 1E-7);
            }
        }
    }

    /**
     * Test of decompose method, of class SingularValueDecomposition.
     */
    @Test
    public void testDecompose7() {
        System.out.println("decompose m = n-2");
        double[][] A = {
            {1.19720880, -1.8391378, 0.3019585, -1.1165701, -1.7210814, 0.4918882, -0.04247433},
            {0.06605075, 1.0315583, 0.8294362, -0.3646043, -1.6038017, -0.9188110, -0.63760340},
            {-1.02637715, 1.0747931, -0.8089055, -0.4726863, -0.2064826, -0.3325532, 0.17966051},
            {-1.45817729, -0.8942353, 0.3459245, 1.5068363, -2.0180708, -0.3696350, -1.19575563},
            {-0.07318103, -0.2783787, 1.2237598, 0.1995332, 0.2545336, -0.1392502, -1.88207227},};

        double[] s = {3.8105658, 3.0883849, 2.2956507, 2.0984771, 0.9019027};

        double[][] U = {
            {0.4022505, -0.8371341, 0.218900330, -0.01150020, 0.29891712},
            {0.3628648, 0.1788073, 0.520476180, 0.66921454, -0.34294833},
            {-0.1204081, 0.3526074, 0.512685919, -0.03159520, 0.77286790},
            {0.7654028, 0.3523577, -0.005786511, -0.53518467, -0.05955197},
            {0.3258590, 0.1369180, -0.646766462, 0.51439164, 0.43836489}
        };

        double[][] V = {
            {-0.1340510, -0.6074832, -0.07579249, 0.38390363, -0.4471462},
            {-0.3332981, 0.5665840, 0.37922383, 0.48268873, -0.1570301},
            {0.3105522, -0.0324612, -0.30945577, 0.48678825, -0.3365301},
            {0.1820803, 0.4083420, -0.35471238, -0.43842294, -0.6389961},
            {-0.7114696, 0.1311251, -0.64046888, 0.07815179, 0.1194533},
            {-0.1112159, -0.2728406, -0.19551704, -0.23056606, 0.1841538},
            {-0.4720051, -0.2247534, 0.42477493, -0.36219292, -0.4534882}
        };

        SVD result = Matrix.of(A).svd();
        assertTrue(MathEx.equals(s, result.getSingularValues(), 1E-7));

        assertEquals(U.length, result.getU().nrows());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(result.getU().get(i, j)), 1E-7);
            }
        }

        assertEquals(V.length, result.getV().nrows());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(result.getV().get(i, j)), 1E-7);
            }
        }
    }

    /**
     * Test of decompose method, of class SingularValueDecomposition.
     */
    @Test
    public void testDecompose8() {
        System.out.println("decompose m = n-3");
        double[][] A = {
            {1.19720880, -1.8391378, 0.3019585, -1.1165701, -1.7210814, 0.4918882, -0.04247433},
            {0.06605075, 1.0315583, 0.8294362, -0.3646043, -1.6038017, -0.9188110, -0.63760340},
            {-1.02637715, 1.0747931, -0.8089055, -0.4726863, -0.2064826, -0.3325532, 0.17966051},
            {-1.45817729, -0.8942353, 0.3459245, 1.5068363, -2.0180708, -0.3696350, -1.19575563},};

        double[] s = {3.668957, 3.068763, 2.179053, 1.293110};

        double[][] U = {
            {-0.4918880, 0.7841689, -0.1533124, 0.34586230},
            {-0.3688033, -0.2221466, -0.8172311, -0.38310353},
            {0.1037476, -0.3784190, -0.3438745, 0.85310363},
            {-0.7818356, -0.4387814, 0.4363243, 0.07632262}
        };

        double[][] V = {
            {0.11456074, 0.63620515, -0.23901163, -0.4625536},
            {0.36382542, -0.54930940, -0.60614838, -0.1412273},
            {-0.22044591, 0.06740501, -0.13539726, -0.6782114},
            {-0.14811938, -0.41609019, 0.59161667, -0.4135324},
            {0.81615679, -0.00968160, 0.35107473, -0.2405136},
            {0.09577622, 0.28606564, 0.28844835, 0.1625626},
            {0.32967585, 0.18412070, -0.02567023, 0.2254902}
        };

        SVD result = Matrix.of(A).svd();
        assertTrue(MathEx.equals(s, result.getSingularValues(), 1E-6));

        assertEquals(U.length, result.getU().nrows());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(result.getU().get(i, j)), 1E-7);
            }
        }

        assertEquals(V.length, result.getV().nrows());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(result.getV().get(i, j)), 1E-7);
            }
        }
    }

    /**
     * Test of decompose method, of class SingularValueDecomposition.
     */
    @Test
    public void testDecompose9() {
        System.out.println("decompose sparse matrix");
        double[][] A = {
            {1, 0, 0, 1, 0, 0, 0, 0, 0},
            {1, 0, 1, 0, 0, 0, 0, 0, 0},
            {1, 1, 0, 0, 0, 0, 0, 0, 0},
            {0, 1, 1, 0, 1, 0, 0, 0, 0},
            {0, 1, 1, 2, 0, 0, 0, 0, 0},
            {0, 1, 0, 0, 1, 0, 0, 0, 0},
            {0, 1, 0, 0, 1, 0, 0, 0, 0},
            {0, 0, 1, 1, 0, 0, 0, 0, 0},
            {0, 1, 0, 0, 0, 0, 0, 0, 1},
            {0, 0, 0, 0, 0, 1, 1, 1, 0},
            {0, 0, 0, 0, 0, 0, 1, 1, 1},
            {0, 0, 0, 0, 0, 0, 0, 1, 1}
        };

        double[] s = {3.34088, 2.5417, 2.35394, 1.64453, 1.50483, 1.30638, 0.845903, 0.560134, 0.363677};

        double[][] Vt = {
            {0.197393, 0.60599, 0.462918, 0.542114, 0.279469, 0.00381521, 0.0146315, 0.0241368, 0.0819574},
            {0.0559135, -0.165593, 0.127312, 0.231755, -0.106775, -0.192848, -0.437875, -0.615122, -0.529937},
            {-0.11027, 0.497326, -0.207606, -0.569921, 0.50545, -0.0981842, -0.192956, -0.252904, -0.0792731},
            {-0.949785, -0.0286489, 0.0416092, 0.267714, 0.150035, 0.0150815, 0.0155072, 0.010199, -0.0245549},
            {-0.0456786, 0.206327, -0.378336, 0.205605, -0.327194, -0.394841, -0.349485, -0.149798, 0.601993},
            {-0.0765936, -0.256475, 0.7244, -0.368861, 0.034813, -0.300161, -0.212201, 9.74342e-05, 0.362219},
            {-0.177318, 0.432984, 0.23689, -0.2648, -0.672304, 0.34084, 0.152195, -0.249146, -0.0380342},
            {-0.0143933, 0.0493053, 0.0088255, -0.0194669, -0.0583496, 0.454477, -0.761527, 0.449643, -0.0696375},
            {-0.0636923, 0.242783, 0.0240769, -0.0842069, -0.262376, -0.619847, 0.0179752, 0.51989, -0.453507}
        };

        double[][] Ut = {
            {0.221351, 0.197645, 0.24047, 0.403599, 0.644481, 0.265037, 0.265037, 0.300828, 0.205918, 0.0127462, 0.0361358, 0.0317563},
            {0.11318, 0.0720878, -0.043152, -0.0570703, 0.167301, -0.10716, -0.10716, 0.14127, -0.273647, -0.490162, -0.622785, -0.450509},
            {-0.288958, -0.13504, 0.164429, 0.337804, -0.361148, 0.425998, 0.425998, -0.330308, 0.177597, -0.23112, -0.223086, -0.141115},
            {-0.414751, -0.55224, -0.594962, 0.0991137, 0.333462, 0.0738122, 0.0738122, 0.188092, -0.0323519, 0.024802, 0.000700072, -0.00872947},
            {0.106275, -0.281769, 0.106755, -0.331734, 0.158955, -0.0803194, -0.0803194, -0.114785, 0.53715, -0.59417, 0.0682529, 0.300495},
            {-0.340983, 0.495878, -0.254955, 0.384832, -0.206523, -0.169676, -0.169676, 0.272155, 0.080944, -0.392125, 0.114909, 0.277343},
            {-0.522658, 0.0704234, 0.30224, -0.00287218, 0.165829, -0.282916, -0.282916, -0.0329941, 0.466898, 0.288317, -0.159575, -0.339495},
            {-0.0604501, -0.00994004, 0.062328, -0.000390504, 0.034272, -0.0161465, -0.0161465, -0.018998, -0.0362988, 0.254568, -0.681125, 0.6784180},
            {-0.406678, -0.10893, 0.492444, 0.0123293, 0.270696, -0.0538747, -0.0538747, -0.165339, -0.579426, -0.225424, 0.231961, 0.182535}
        };

        SVD result = Matrix.of(A).svd();
        assertTrue(MathEx.equals(s, result.getSingularValues(), 1E-5));

        assertEquals(Ut[0].length, result.getU().nrows());
        assertEquals(Ut.length, result.getU().ncols());
        for (int i = 0; i < Ut.length; i++) {
            for (int j = 0; j < Ut[i].length; j++) {
                assertEquals(Math.abs(Ut[i][j]), Math.abs(result.getU().get(j, i)), 1E-5);
            }
        }

        assertEquals(Vt[0].length, result.getV().nrows());
        assertEquals(Vt.length, result.getV().ncols());
        for (int i = 0; i < Vt.length; i++) {
            for (int j = 0; j < Vt[i].length; j++) {
                assertEquals(Math.abs(Vt[i][j]), Math.abs(result.getV().get(j, i)), 1E-5);
            }
        }
    }

    /**
     * Test of solve method, of class SingularValueDecomposition.
     */
    @Test
    public void testSolve_doubleArr_doubleArr() {
        System.out.println("solve");
        double[][] A = {
            {0.9000, 0.4000, 0.7000},
            {0.4000, 0.5000, 0.3000},
            {0.7000, 0.3000, 0.8000}
        };
        double[] B = {0.5, 0.5, 0.5};
        double[] X = {-0.2027027, 0.8783784, 0.4729730};

        SVD result = Matrix.of(A).svd();
        double[] x = new double[B.length];
        result.solve(B, x);
        assertEquals(X.length, x.length);
        for (int i = 0; i < X.length; i++) {
            assertEquals(X[i], x[i], 1E-7);
        }
    }

    /**
     * Test of solve method, of class SingularValueDecomposition.
     */
    @Test
    public void testSolve_doubleArrArr_doubleArrArr() {
        System.out.println("solve");
        double[][] A = {
            {0.9000, 0.4000, 0.7000},
            {0.4000, 0.5000, 0.3000},
            {0.7000, 0.3000, 0.8000}
        };
        double[][] B = {
            {0.5, 0.2},
            {0.5, 0.8},
            {0.5, 0.3}
        };
        double[][] X = {
            {-0.2027027, -1.2837838},
            {0.8783784, 2.2297297},
            {0.4729730, 0.6621622}
        };

        SVD result = new NLMatrix(A).svd();
        NLMatrix x = new NLMatrix(B);
        result.solve(x);
        for (int i = 0; i < x.nrows(); i++) {
            for (int j = 0; j < x.ncols(); j++) {
                assertEquals(X[i][j], x.get(i, j), 1E-7);
            }
        }

    }
}