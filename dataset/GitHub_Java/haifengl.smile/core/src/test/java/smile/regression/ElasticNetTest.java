/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.regression;

import smile.data.AttributeDataset;
import smile.data.NumericAttribute;
import smile.data.parser.ArffParser;
import smile.data.parser.DelimitedTextParser;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.Math;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import static org.junit.Assert.*;

/**
 *
 * @author rayeaster
 */
public class ElasticNetTest {
	public ElasticNetTest() {
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
	public void testToy2() {
		double[][] A = { { 1, 0, 0, 0.5 }, { 0, 1, 0.2, 0.3 }, { 1, 0.5, 0.2, 0.3 }, { 0, 0.1, 0, 0.2 },
				{ 0, 0.1, 1, 0.2 } };

		double[] x0 = { 1, 0, 1, 0 }; // original signal
		double[] y = new double[A.length];
		DenseMatrix a = Matrix.newInstance(A);
		a.ax(x0, y); // measurements with no noise
		for (int i = 0; i < y.length; i++) {
			y[i] += 5;
		}

		ElasticNet elasticnet = new ElasticNet(A, y, 0.1, 0.999999999999999999999999);

		double rss = 0.0;
		int n = A.length;
		for (int i = 0; i < n; i++) {
			double r = y[i] - elasticnet.predict(A[i]);
			rss += r * r;
		}
		System.out.println("MSE = " + rss / n);

		assertEquals(5.026017354431575, elasticnet.getModifiedLasso().intercept(), 1E-7);

		double[] w = { 0.966178536428876, -2.894679720782083E-5, 0.9553541241010624, 7.306053208965189E-5 };
		for (int i = 0; i < w.length; i++) {
			assertEquals(w[i], elasticnet.getModifiedLasso().coefficients()[i], 1E-5);
		}
	}

	/**
	 * Test of learn method, of class RidgeRegression.
	 */
	@Test
	public void testLongley() {
		System.out.println("longley");

		double[][] longley = { { 234.289, 235.6, 159.0, 107.608, 1947, 60.323 },
				{ 259.426, 232.5, 145.6, 108.632, 1948, 61.122 }, { 258.054, 368.2, 161.6, 109.773, 1949, 60.171 },
				{ 284.599, 335.1, 165.0, 110.929, 1950, 61.187 }, { 328.975, 209.9, 309.9, 112.075, 1951, 63.221 },
				{ 346.999, 193.2, 359.4, 113.270, 1952, 63.639 }, { 365.385, 187.0, 354.7, 115.094, 1953, 64.989 },
				{ 363.112, 357.8, 335.0, 116.219, 1954, 63.761 }, { 397.469, 290.4, 304.8, 117.388, 1955, 66.019 },
				{ 419.180, 282.2, 285.7, 118.734, 1956, 67.857 }, { 442.769, 293.6, 279.8, 120.445, 1957, 68.169 },
				{ 444.546, 468.1, 263.7, 121.950, 1958, 66.513 }, { 482.704, 381.3, 255.2, 123.366, 1959, 68.655 },
				{ 502.601, 393.1, 251.4, 125.368, 1960, 69.564 }, { 518.173, 480.6, 257.2, 127.852, 1961, 69.331 },
				{ 554.894, 400.7, 282.7, 130.081, 1962, 70.551 } };

		double[] y = { 83.0, 88.5, 88.2, 89.5, 96.2, 98.1, 99.0, 100.0, 101.2, 104.6, 108.4, 110.8, 112.6, 114.2, 115.7,
				116.9 };

		double rss = 0.0;
		int n = longley.length;
		LOOCV loocv = new LOOCV(n);
		for (int i = 0; i < n; i++) {
			double[][] trainx = Math.slice(longley, loocv.train[i]);
			double[] trainy = Math.slice(y, loocv.train[i]);
			ElasticNet elasticnet = new ElasticNet(trainx, trainy, 0.1, 0.9);

			double r = y[loocv.test[i]] - elasticnet.predict(longley[loocv.test[i]]);
			rss += r * r;
		}

		System.out.println("longley LOOCV MSE = " + rss / n);
		assertEquals(3.0216389345925805, rss / n, 1E-4);
	}

	/**
	 * Test of learn method, of class LinearRegression.
	 */
	@Test
	public void testCPU() {
		System.out.println("CPU");
		ArffParser parser = new ArffParser();
		parser.setResponseIndex(6);
		try {
			AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/cpu.arff"));
			double[][] datax = data.toArray(new double[data.size()][]);
			double[] datay = data.toArray(new double[data.size()]);

			int n = datax.length;
			int k = 10;

			CrossValidation cv = new CrossValidation(n, k);
			double rss = 0.0;
			for (int i = 0; i < k; i++) {
				double[][] trainx = Math.slice(datax, cv.train[i]);
				double[] trainy = Math.slice(datay, cv.train[i]);
				double[][] testx = Math.slice(datax, cv.test[i]);
				double[] testy = Math.slice(datay, cv.test[i]);

				ElasticNet elasticnet = new ElasticNet(trainx, trainy, 50, 0.79999999999999999999);

				for (int j = 0; j < testx.length; j++) {
					double r = testy[j] - elasticnet.predict(testx[j]);
					rss += r * r;
				}
			}

			System.out.println("CPU 10-CV RMSE = " + Math.sqrt(rss / n));
		} catch (Exception ex) {
			System.err.println(ex);
		}
	}

	/**
	 * Test of learn method, of class LinearRegression.
	 */
	@Test
	public void tesProstate() {
		System.out.println("---ProStateCancer---");
		DelimitedTextParser parser = new DelimitedTextParser();
		parser.setResponseIndex(new NumericAttribute("lpsa"), 8);
		parser.setColumnNames(true);
		try {
			AttributeDataset train = parser.parse("prostate Train",
					smile.data.parser.IOUtils.getTestDataFile("regression/prostate-train.csv"));
			AttributeDataset test = parser.parse("prostate Test",
					smile.data.parser.IOUtils.getTestDataFile("regression/prostate-test.csv"));

			double[][] x = train.toArray(new double[train.size()][]);
			double[] y = train.toArray(new double[train.size()]);
			double[][] testx = test.toArray(new double[test.size()][]);
			double[] testy = test.toArray(new double[test.size()]);

			ElasticNet elasticnet = new ElasticNet(x, y, 10, 0.85);

			double testrss = 0;
			int n = testx.length;
			for (int j = 0; j < testx.length; j++) {
				double r = testy[j] - elasticnet.predict(testx[j]);
				testrss += r * r;
			}

			System.out.println("Prostate Test MSE = " + testrss / n);
		} catch (Exception ex) {
			System.err.println(ex);
		}
	}

	/**
	 * Test of learn method, of class LinearRegression.
	 */
	@Test
	public void tesAbalone() {
		System.out.println("---Abalone---");
		DelimitedTextParser parser = new DelimitedTextParser();
		parser.setResponseIndex(new NumericAttribute("ring"), 8);
		parser.setColumnNames(false);
		parser.setDelimiter(",");
		parser.addIgnoredColumn(0);
		try {
			AttributeDataset train = parser.parse("abalone Train",
					smile.data.parser.IOUtils.getTestDataFile("regression/abalone-train.data"));
			AttributeDataset test = parser.parse("abalone Test",
					smile.data.parser.IOUtils.getTestDataFile("regression/abalone-test.data"));

			double[][] x = train.toArray(new double[train.size()][]);
			double[] y = train.toArray(new double[train.size()]);
			double[][] testx = test.toArray(new double[test.size()][]);
			double[] testy = test.toArray(new double[test.size()]);

			ElasticNet elasticnet = new ElasticNet(x, y, 1, 0.85);

			double testrss = 0;
			int n = testx.length;
			for (int j = 0; j < testx.length; j++) {
				double r = testy[j] - elasticnet.predict(testx[j]);
				testrss += r * r;
			}

			System.out.println("Abalone Test MSE = " + testrss / n);
		} catch (Exception ex) {
			System.err.println(ex);
		}
	}

	/**
	 * Test of learn method, of class LinearRegression.
	 */
	@Test
	public void tesDiabetes() {
		System.out.println("---Diabetes---");
		DelimitedTextParser parser = new DelimitedTextParser();
		parser.setResponseIndex(new NumericAttribute("y"), 0);
		parser.setColumnNames(true);
		parser.setDelimiter(",");
		try {
			AttributeDataset data = parser.parse("diabetes",
					smile.data.parser.IOUtils.getTestDataFile("regression/diabetes.csv"));
			double[][] datax = data.toArray(new double[data.size()][]);
			double[] datay = data.toArray(new double[data.size()]);

			int n = datax.length;
			int k = 40;

			CrossValidation cv = new CrossValidation(n, k);
			double rss = 0.0;
			for (int i = 0; i < k; i++) {
				double[][] trainx = Math.slice(datax, cv.train[i]);
				double[] trainy = Math.slice(datay, cv.train[i]);
				double[][] testx = Math.slice(datax, cv.test[i]);
				double[] testy = Math.slice(datay, cv.test[i]);

				ElasticNet elasticnet = new ElasticNet(trainx, trainy, 1, 0.7999999999999999999999);

				for (int j = 0; j < testx.length; j++) {
					double r = testy[j] - elasticnet.predict(testx[j]);
					rss += r * r;
				}
			}

			System.out.println("Diabetes 40-CV RMSE = " + Math.sqrt(rss / n));
		} catch (Exception ex) {
			System.err.println(ex);
		}
	}
}