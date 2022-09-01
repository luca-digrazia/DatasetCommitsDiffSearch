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

package smile.demo.clustering;

import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import smile.clustering.GMeans;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.plot.swing.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class GMeansDemo extends ClusteringDemo {
    JTextField maxClusterNumberField;
    int maxClusterNumber = 50;

    public GMeansDemo() {
        // Remove K TextFile
        optionPane.remove(optionPane.getComponentCount() - 1);
        optionPane.remove(optionPane.getComponentCount() - 1);

        maxClusterNumberField = new JTextField(Integer.toString(maxClusterNumber), 5);
        optionPane.add(new JLabel("Max K:"));
        optionPane.add(maxClusterNumberField);
    }

    @Override
    public JComponent learn() {
        try {
            maxClusterNumber = Integer.parseInt(maxClusterNumberField.getText().trim());
            if (maxClusterNumber < 2) {
                JOptionPane.showMessageDialog(this, "Invalid Max K: " + maxClusterNumber, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid Max K: " + maxClusterNumberField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        long clock = System.currentTimeMillis();
        GMeans gmeans = GMeans.fit(dataset[datasetIndex], maxClusterNumber);
        System.out.format("G-Means clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        PlotCanvas plot = ScatterPlot.plot(gmeans.centroids, '@');
        for (int k = 0; k < gmeans.k; k++) {
            if (gmeans.size[k] > 0) {
                double[][] cluster = new double[gmeans.size[k]][];
                for (int i = 0, j = 0; i < dataset[datasetIndex].length; i++) {
                    if (gmeans.y[i] == k) {
                        cluster[j++] = dataset[datasetIndex][i];
                    }
                }

                plot.points(cluster, pointLegend, Palette.COLORS[k % Palette.COLORS.length]);
            }
        }
        plot.points(gmeans.centroids, '@');
        return plot;
    }

    @Override
    public String toString() {
        return "G-Means";
    }

    public static void main(String argv[]) {
        ClusteringDemo demo = new GMeansDemo();
        JFrame f = new JFrame("G-Means");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
