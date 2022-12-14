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
package smile.stat.distribution;

import java.util.List;
import java.util.ArrayList;
import smile.math.Math;

/**
 * The finite mixture of discrete distributions.
 *
 * @author Haifeng Li
 */
public class DiscreteMixture extends DiscreteDistribution {

    /**
     * A component in the mixture distribution is defined by a distribution
     * and its weight in the mixture.
     */
    public static class Component {

        /**
         * The distribution of component.
         */
        public DiscreteDistribution distribution;
        /**
         * The priori probability of component.
         */
        public double priori;
    }
    List<Component> components;

    /**
     * Constructor.
     */
    DiscreteMixture() {
        components = new ArrayList<Component>();
    }

    /**
     * Constructor.
     * @param mixture a list of discrete distributions.
     */
    public DiscreteMixture(List<Component> mixture) {
        components = new ArrayList<Component>();
        components.addAll(mixture);

        double sum = 0.0;
        for (Component component : mixture) {
            sum += component.priori;
            if (component.distribution instanceof DiscreteDistribution == false) {
                throw new IllegalArgumentException("Component " + component + " is not a discrete distribution.");
            }
        }

        if (Math.abs(sum - 1.0) > 1E-3) {
            throw new IllegalArgumentException("The sum of priori is not equal to 1.");
        }
    }

    @Override
    public double mean() {
        if (components.isEmpty()) {
            throw new IllegalStateException("Mixture is empty!");
        }

        double mu = 0.0;

        for (Component c : components) {
            mu += c.priori * c.distribution.mean();
        }

        return mu;
    }

    @Override
    public double var() {
        if (components.isEmpty()) {
            throw new IllegalStateException("Mixture is empty!");
        }

        double variance = 0.0;

        for (Component c : components) {
            variance += c.priori * c.priori * c.distribution.var();
        }

        return variance;
    }

    @Override
    public double sd() {
        return Math.sqrt(var());
    }

    /**
     * Shannon entropy. Not supported.
     */
    @Override
    public double entropy() {
        throw new UnsupportedOperationException("Mixture does not support entropy()");
    }

    @Override
    public double p(int x) {
        if (components.isEmpty()) {
            throw new IllegalStateException("Mixture is empty!");
        }

        double p = 0.0;

        for (Component c : components) {
            p += c.priori * c.distribution.p(x);
        }

        return p;
    }

    @Override
    public double logp(int x) {
        if (components.isEmpty()) {
            throw new IllegalStateException("Mixture is empty!");
        }

        return Math.log(p(x));
    }

    @Override
    public double cdf(double x) {
        if (components.isEmpty()) {
            throw new IllegalStateException("Mixture is empty!");
        }

        double p = 0.0;

        for (Component c : components) {
            p += c.priori * c.distribution.cdf(x);
        }

        return p;
    }

    @Override
    public double rand() {
        if (components.isEmpty()) {
            throw new IllegalStateException("Mixture is empty!");
        }

        double r = Math.random();

        double p = 0.0;
        for (Component g : components) {
            p += g.priori;
            if (r <= p) {
                return g.distribution.rand();
            }
        }

        // we should not arrive here.
        return components.get(components.size() - 1).distribution.rand();
    }

    @Override
    public double quantile(double p) {
        if (components.isEmpty()) {
            throw new IllegalStateException("Mixture is empty!");
        }

        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        // Starting guess near peak of density.
        // Expand interval until we bracket.
        int xl, xu, inc = 1;
        int x = (int) mean();
        if (p < cdf(x)) {
            do {
                x -= inc;
                inc *= 2;
            } while (p < cdf(x));
            xl = x;
            xu = x + inc / 2;
        } else {
            do {
                x += inc;
                inc *= 2;
            } while (p > cdf(x));
            xu = x;
            xl = x - inc / 2;
        }

        return quantile(p, xl, xu);
    }

    @Override
    public int npara() {
        if (components.isEmpty()) {
            throw new IllegalStateException("Mixture is empty!");
        }

        int f = 0;
        for (int i = 0; i < components.size(); i++) {
            f += components.get(i).distribution.npara();
        }

        return f;
    }

    /**
     * Returns the number of components in the mixture.
     */
    public int size() {
        return components.size();
    }

    /**
     * BIC score of the mixture for given data.
     */
    public double bic(double[] data) {
        if (components.isEmpty()) {
            throw new IllegalStateException("Mixture is empty!");
        }

        int n = data.length;

        double logLikelihood = 0.0;
        for (double x : data) {
            double p = p(x);
            if (p > 0) {
                logLikelihood += Math.log(p);
            }
        }

        return logLikelihood - 0.5 * npara() * Math.log(n);
    }

    /**
     * Returns the list of components in the mixture.
     */
    public List<Component> getComponents() {
        return components;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Mixture[");
        builder.append(components.size());
        builder.append("]:{");
        for (Component c : components) {
            builder.append(" (");
            builder.append(c.distribution);
            builder.append(':');
            builder.append(String.format("%.4f", c.priori));
            builder.append(')');
        }
        builder.append("}");
        return builder.toString();
    }
}
