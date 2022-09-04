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

package smile.data;

import org.apache.commons.csv.CSVFormat;
import smile.data.formula.Formula;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.io.Read;
import smile.util.Paths;

/**
 *
 * @author Haifeng
 */
public class BreastCancer {

    public static DataFrame data;
    public static Formula formula = Formula.lhs("diagnosis");
    public static double[][] x;
    public static int[] y;

    static {
        try {
            data = Read.csv(Paths.getTestData("classification/breastcancer.csv"), CSVFormat.DEFAULT.withFirstRecordAsHeader());
            data = data.drop("id").factorize("diagnosis");
            x = formula.x(data).toArray(false, CategoricalEncoder.DUMMY);
            y = formula.y(data).toIntArray();
        } catch (Exception ex) {
            System.err.println("Failed to load 'breast cancer': " + ex);
            System.exit(-1);
        }
    }
}
