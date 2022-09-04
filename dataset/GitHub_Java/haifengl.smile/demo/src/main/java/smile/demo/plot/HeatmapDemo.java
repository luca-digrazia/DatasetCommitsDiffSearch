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

package smile.demo.plot;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.stream.IntStream;

import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.io.CSV;
import smile.plot.Contour;
import smile.plot.Heatmap;
import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.util.Paths;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class HeatmapDemo extends JPanel {
    public HeatmapDemo() {
        super(new GridLayout(2,4));
        setBackground(Color.white);

        int n = 81;
        double[] x = new double[n];
        for (int i = 0; i < n; i++)
            x[i] = -2.0 + 0.05 * i;

        int m = 81;
        double[] y = new double[m];
        for (int i = 0; i < m; i++)
            y[i] = -2.0 + 0.05 * i;

        double[][] z = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++)
                z[i][j] = x[j] * Math.exp(-x[j]*x[j] - y[i]*y[i]);
        }

        PlotCanvas canvas = Heatmap.plot(z, Palette.jet(256));
        canvas.add(new Contour(z));
        canvas.setTitle("jet");
        add(canvas);
        canvas = Heatmap.plot(x, y, z, Palette.redblue(256));
        canvas.add(new Contour(x, y, z));
        canvas.setTitle("redblue");
        add(canvas);
        canvas = Heatmap.plot(z, Palette.redgreen(256));
        canvas.add(new Contour(z));
        canvas.setTitle("redgreen");
        add(canvas);
        canvas = Heatmap.plot(x, y, z, Palette.heat(256));
        canvas.add(new Contour(x, y, z));
        canvas.setTitle("heat");
        add(canvas);
        canvas = Heatmap.plot(z, Palette.terrain(256));
        canvas.add(new Contour(z));
        canvas.setTitle("terrain");
        add(canvas);
        canvas = Heatmap.plot(x, y, z, Palette.rainbow(256));
        canvas.add(new Contour(x, y, z));
        canvas.setTitle("rainbow");
        add(canvas);
        canvas = Heatmap.plot(z, Palette.topo(256));
        canvas.add(new Contour(z));
        canvas.setTitle("topo");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Heatmap";
    }

    public static void main(String[] args) {
        ArrayList<StructField> fields = new ArrayList<>();
        fields.add(new StructField("class", DataTypes.ByteType));
        IntStream.range(1, 257).forEach(i -> fields.add(new StructField("V"+i, DataTypes.DoubleType)));
        StructType schema = DataTypes.struct(fields);

        CSV csv = new CSV(CSVFormat.DEFAULT.withDelimiter(' '));
        csv.schema(schema);
        try {
            DataFrame train = csv.read(Paths.getTestData("usps/zip.train"));
            Formula formula = Formula.lhs("class");
            double[][] x = formula.x(train).toArray();
            int[] y = formula.y(train).toIntArray();

            JFrame frame = new JFrame("Heatmap");
            frame.setSize(1000, 1000);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.getContentPane().add(Heatmap.plot(x, Palette.jet(256)));
            frame.setVisible(true);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
