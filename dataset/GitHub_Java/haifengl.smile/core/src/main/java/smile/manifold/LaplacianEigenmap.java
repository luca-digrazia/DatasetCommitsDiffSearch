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
package smile.manifold;

import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.data.SparseDataset;
import smile.graph.AdjacencyList;
import smile.graph.Graph;
import smile.graph.Graph.Edge;
import smile.math.Math;
import smile.math.SparseArray;
import smile.math.distance.EuclideanDistance;
import smile.math.matrix.EigenValueDecomposition;
import smile.math.matrix.SparseMatrix;
import smile.neighbor.CoverTree;
import smile.neighbor.KDTree;
import smile.neighbor.KNNSearch;
import smile.neighbor.Neighbor;

/**
 * Laplacian Eigenmap. Using the notion of the Laplacian of the nearest
 * neighbor adjacency graph, Laplacian Eigenmap computes a low dimensional
 * representation of the dataset that optimally preserves local neighborhood
 * information in a certain sense. The representation map generated by the
 * algorithm may be viewed as a discrete approximation to a continuous map
 * that naturally arises from the geometry of the manifold.
 * <p>
 * The locality preserving character of the Laplacian Eigenmap algorithm makes
 * it relatively insensitive to outliers and noise. It is also not prone to
 * "short circuiting" as only the local distances are used.
 *
 * @see IsoMap
 * @see LLE
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Mikhail Belkin and Partha Niyogi. Laplacian Eigenmaps and Spectral Techniques for Embedding and Clustering. NIPS, 2001. </li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class LaplacianEigenmap {
    private static final Logger logger = LoggerFactory.getLogger(LaplacianEigenmap.class);

    /**
     * The width of heat kernel.
     */
    private double t;
    /**
     * The original sample index.
     */
    private int[] index;
    /**
     * Coordinate matrix.
     */
    private double[][] coordinates;
    /**
     * Nearest neighbor graph.
     */
    private Graph graph;

    /**
     * Constructor. Learn Laplacian Eigenmaps with discrete weights.
     * @param data the dataset.
     * @param d the dimension of the manifold.
     * @param k k-nearest neighbor.
     */
    public LaplacianEigenmap(double[][] data, int d, int k) {
        this(data, d, k, -1);
    }

    /**
     * Constructor. Learn Laplacian Eigenmap with Gaussian kernel.
     * @param data the dataset.
     * @param d the dimension of the manifold.
     * @param k k-nearest neighbor.
     * @param t the smooth/width parameter of heat kernel e<sup>-||x-y||<sup>2</sup> / t</sup>.
     * Non-positive value means discrete weights.
     */
    public LaplacianEigenmap(double[][] data, int d, int k, double t) {
        this.t = t;
        
        int n = data.length;
        KNNSearch<double[], double[]> knn = null;
        if (data[0].length < 10) {
            knn = new KDTree<double[]>(data, data);
        } else {
            knn = new CoverTree<double[]>(data, new EuclideanDistance());
        }

        graph = new AdjacencyList(n);
        for (int i = 0; i < n; i++) {
            Neighbor<double[], double[]>[] neighbors = knn.knn(data[i], k);
            for (int j = 0; j < k; j++) {
                graph.setWeight(i, neighbors[j].index, neighbors[j].distance);
            }
        }

        // Use largest connected component.
        int[][] cc = graph.bfs();
        if (cc.length == 1) {
            index = new int[n];
            for (int i = 0; i < n; i++) {
                index[i] = i;
            }
        } else {
            n = 0;
            int component = 0;
            for (int i = 0; i < cc.length; i++) {
                if (cc[i].length > n) {
                    component = i;
                    n = cc[i].length;
                }
            }

            logger.info("Laplacian Eigenmap: {} connected components, largest one has {} samples.", cc.length, n);

            index = cc[component];
            graph = graph.subgraph(index);
        }

        SparseDataset W = new SparseDataset(n);
        double[] D = new double[n];
        if (t <= 0) {
            for (int i = 0; i < n; i++) {
                W.set(i, i, 1.0);

                Collection<Edge> edges = graph.getEdges(i);
                    for (Edge edge : edges) {
                    int j = edge.v2;
                    if (i == j) {
                        j = edge.v1;
                    }

                    W.set(i, j, 1.0);
                    D[i] += 1.0;
                }

                D[i] = 1 / Math.sqrt(D[i]);
            }
        } else {
            double gamma = -1.0 / t;
            for (int i = 0; i < n; i++) {
                W.set(i, i, 1.0);

                Collection<Edge> edges = graph.getEdges(i);
                    for (Edge edge : edges) {
                    int j = edge.v2;
                    if (i == j) {
                        j = edge.v1;
                    }

                    double w = Math.exp(gamma * Math.sqr(edge.weight));
                    W.set(i, j, w);
                    D[i] += w;
                }

                D[i] = 1 / Math.sqrt(D[i]);
            }
        }

        SparseMatrix L = W.toSparseMatrix();
        for (int i = 0; i < n; i++) {
            SparseArray edges = W.get(i).x;
            for (SparseArray.Entry edge : edges) {
                int j = edge.i;
                double s = D[i] * edge.x * D[j];
                L.set(i, j, s);
            }
            L.set(i, i, -1.0);
        }

        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            v[i] = Math.random();
        }

        // Largest eigenvalue.
        double lambda = -Math.eigen(L, v, 1E-6);
        for (int i = 0; i < n; i++) {
            L.set(i, i, lambda - 1.0);
        }

        EigenValueDecomposition eigen = EigenValueDecomposition.decompose(L, d + 1);

        coordinates = new double[n][d];
        for (int j = 0; j < d; j++) {
            double norm = 0.0;
            for (int i = 0; i < n; i++) {
                coordinates[i][j] = eigen.getEigenVectors()[i][j + 1] * D[i];
                norm += coordinates[i][j] * coordinates[i][j];
            }

            norm = Math.sqrt(norm);
            for (int i = 0; i < n; i++) {
                coordinates[i][j] /= norm;
            }
        }
    }

    /**
     * Returns the original sample index. Because Laplacian Eigenmap is applied to the largest
     * connected component of k-nearest neighbor graph, we record the the original
     * indices of samples in the largest component.
     */
    public int[] getIndex() {
        return index;
    }

    /**
     * Returns the coordinates of projected data.
     */
    public double[][] getCoordinates() {
        return coordinates;
    }

    /**
     * Returns the nearest neighbor graph.
     */
    public Graph getNearestNeighborGraph() {
        return graph;
    }

    /**
     * Returns the width of heat kernel.
     */
    public double getHeatKernelWidth() {
        return t;
    }
}
