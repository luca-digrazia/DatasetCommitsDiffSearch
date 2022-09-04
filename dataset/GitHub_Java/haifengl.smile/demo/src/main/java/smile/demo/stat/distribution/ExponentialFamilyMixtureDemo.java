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

package smile.demo.stat.distribution;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.math.Math;
import smile.plot.Histogram;
import smile.plot.PlotCanvas;
import smile.plot.QQPlot;
import smile.stat.distribution.ExponentialDistribution;
import smile.stat.distribution.ExponentialFamilyMixture;
import smile.stat.distribution.GammaDistribution;
import smile.stat.distribution.GaussianDistribution;
import smile.stat.distribution.Mixture;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class ExponentialFamilyMixtureDemo extends JPanel {
    public ExponentialFamilyMixtureDemo() {
        super(new GridLayout(1, 2));

        // Mixture of Gaussian, Exponential, and Gamma.
        double[] data = new double[2000];

        GaussianDistribution gaussian = new GaussianDistribution(-2.0, 1.0);
        for (int i = 0; i < 500; i++)
            data[i] = gaussian.rand();

        ExponentialDistribution exp = new ExponentialDistribution(0.8);
        for (int i = 500; i < 1000; i++)
            data[i] = exp.rand();

        GammaDistribution gamma = new GammaDistribution(2.0, 3.0);
        for (int i = 1000; i < 2000; i++)
            data[i] = gamma.rand();

        List<Mixture.Component> m = new ArrayList<>();
        Mixture.Component c = new Mixture.Component();
        c.priori = 0.25;
        c.distribution = new GaussianDistribution(0.0, 1.0);
        m.add(c);

        c = new Mixture.Component();
        c.priori = 0.25;
        c.distribution = new ExponentialDistribution(1.0);
        m.add(c);

        c = new Mixture.Component();
        c.priori = 0.5;
        c.distribution = new GammaDistribution(1.0, 2.0);
        m.add(c);

        ExponentialFamilyMixture mixture = new ExponentialFamilyMixture(m, data);

        PlotCanvas canvas = Histogram.plot(data, 50);
        canvas.setTitle("Mixture of Gaussian, Exponential, and Gamma");
        add(canvas);

        double width = (Math.max(data) - Math.min(data)) / 50;
        double[][] p = new double[400][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = -10 + i*0.1;
            p[i][1] = mixture.p(p[i][0]) * width;
        }

        canvas.line(p, Color.RED);

        canvas = QQPlot.plot(data, mixture);
        canvas.setTitle("Q-Q Plot");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Exponential Family Mixture";
    }

    public static void main(String[] args) {
        // Mixture of Gaussian, Exponential, and Gamma.
        double[] data = new double[2000];

        GaussianDistribution gaussian = new GaussianDistribution(-2.0, 1.0);
        for (int i = 0; i < 500; i++)
            data[i] = gaussian.rand();

        ExponentialDistribution exp = new ExponentialDistribution(0.8);
        for (int i = 500; i < 1000; i++)
            data[i] = exp.rand();

        GammaDistribution gamma = new GammaDistribution(2.0, 3.0);
        for (int i = 1000; i < 2000; i++)
            data[i] = gamma.rand();

        List<Mixture.Component> m = new ArrayList<>();
        Mixture.Component c = new Mixture.Component();
        c.priori = 0.25;
        c.distribution = new GaussianDistribution(0.0, 1.0);
        m.add(c);

        c = new Mixture.Component();
        c.priori = 0.25;
        c.distribution = new ExponentialDistribution(1.0);
        m.add(c);

        c = new Mixture.Component();
        c.priori = 0.25;
        c.distribution = new GammaDistribution(1.0, 2.0);
        m.add(c);

        ExponentialFamilyMixture mixture = new ExponentialFamilyMixture(m, data);
        System.out.println(mixture);

        JFrame frame = new JFrame("Mixture of Exponential Family Distributions");
        PlotCanvas canvas = Histogram.plot(data, 50);
        frame.add(canvas);

        double width = (Math.max(data) - Math.min(data)) / 50;
        double[][] p = new double[400][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = -10 + i*0.1;
            p[i][1] = mixture.p(p[i][0]) * width;
        }

        canvas.line(p, Color.RED);

        frame.add(QQPlot.plot(data, mixture));

        frame.setVisible(true);
    }
}
