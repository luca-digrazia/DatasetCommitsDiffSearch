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

package smile.demo.vq;

import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JFrame;
import smile.vq.GrowingNeuralGas;
import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.plot.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class GrowingNeuralGasDemo extends VQDemo {
    public GrowingNeuralGasDemo() {
    }

    @Override
    public JComponent learn() {
        long clock = System.currentTimeMillis();
        GrowingNeuralGas gas = new GrowingNeuralGas(2, 0.05, 0.0006, 88, 200,  0.5, 0.9995);

        for (int loop = 0; loop < 25; loop++) {
            for (double[] x : dataset[datasetIndex]) {
                gas.update(x);
            }
        }

        gas.partition(clusterNumber);
        System.out.format("Growing Neural Gas clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        int[] membership = new int[dataset[datasetIndex].length];
        int[] clusterSize = new int[clusterNumber];
        for (int i = 0; i < dataset[datasetIndex].length; i++) {
            membership[i] = gas.predict(dataset[datasetIndex][i]);
            clusterSize[membership[i]]++;
        }

        GrowingNeuralGas.Neuron[] neurons = gas.neurons();
        double[][] x = new double[neurons.length][];
        for (int i = 0; i < x.length; i++)
            x[i] = neurons[i].w;

        PlotCanvas plot = ScatterPlot.plot(x, '@');
        for (int k = 0; k < clusterNumber; k++) {
            if (clusterSize[k] > 0) {
                double[][] cluster = new double[clusterSize[k]][];
                for (int i = 0, j = 0; i < dataset[datasetIndex].length; i++) {
                    if (membership[i] == k) {
                        cluster[j++] = dataset[datasetIndex][i];
                    }
                }

                plot.points(cluster, pointLegend, Palette.COLORS[k % Palette.COLORS.length]);
            }
        }

        for (int i = 0; i < neurons.length; i++) {
            for (int j = 0; j < neurons[i].neighbors.length; j++) {
                plot.line(neurons[i].w, neurons[i].neighbors[j].w);
            }
        }
        plot.points(x, '@');

        return plot;
    }

    @Override
    public String toString() {
        return "Growing Neural Gas";
    }

    public static void main(String argv[]) {
        GrowingNeuralGasDemo demo = new GrowingNeuralGasDemo();
        JFrame f = new JFrame("Growing Neural Gas");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
