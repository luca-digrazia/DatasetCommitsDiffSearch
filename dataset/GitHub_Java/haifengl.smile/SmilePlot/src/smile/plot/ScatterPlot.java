/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.plot;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashMap;

import smile.math.Math;

/**
 * The data is displayed as a collection of points.
 *
 * @author Haifeng Li
 */
public class ScatterPlot extends Plot {

    /**
     * The coordinates of points.
     */
    private double[][] data;
    
    /**
     * Class label of data points.
     */
    private int[] y;
    /**
     * The legend for each class.
     */
    private char[] legends;
    /**
     * The color for each class.
     */
    private Color[] palette;

    private HashMap<Integer,Integer> valueLookupTable;
    /**
     * The legend of points.
     */
    private char legend;

    /**
     * Labels of points.
     */
    private String[] labels;

    /**
     * Constructor.
     */
    public ScatterPlot(double[][] data) {
        this(data, 'o');
    }

    /**
     * Constructor.
     */
    public ScatterPlot(double[][] data, String[] labels) {
        if (data.length != labels.length) {
            throw new IllegalArgumentException("The number of points and that of labels are not same.");
        }

        this.data = data;
        this.labels = labels;
    }

    /**
     * Constructor.
     */
    public ScatterPlot(double[][] data, char legend) {
        this.data = data;
        this.legend = legend;
    }

    /**
     * Constructor.
     */
    public ScatterPlot(double[][] data, Color color) {
        this(data, 'o', color);
    }

    /**
     * Constructor.
     */
    public ScatterPlot(double[][] data, char legend, Color color) {
        super(color);
        this.data = data;
        this.legend = legend;
    }

    /**
     * Constructor.
     */
    public ScatterPlot(double[][] data, int[] y, char[] legends) {
        this(data, y, legends, (Color[]) null);
    }

    /**
     * Constructor.
     */
    public ScatterPlot(double[][] data, int[] y, char[] legends, Color color) {
        this(data, y, legends, (Color[]) null);
        setColor(color);
    }

    /**
     * Constructor.
     */
    public ScatterPlot(double[][] data, int[] y, Color[] palette) {
        this(data, y, null, palette);
    }

    /**
     * Constructor.
     */
    public ScatterPlot(double[][] data, int[] y, char legend, Color[] palette) {
        this(data, y, null, palette);
        this.legend = legend;
    }

    /**
     * Constructor.
     */
    public ScatterPlot(double[][] data, int[] y, char[] legends, Color[] palette) {
        if (data.length != y.length) {
            throw new IllegalArgumentException("Data and label size are different.");
        }
        
        // class label set.
        int[] id = Math.unique(y);
        Arrays.sort(id);

        valueLookupTable = new HashMap<Integer, Integer>(id.length);

        for (int i =0; i <  id.length; i++) {
            valueLookupTable.put(id[i], i);
        }

        for (int i = 0; i < id.length; i++) {
            if (id[i] < 0) {
                throw new IllegalArgumentException("Negative class label: " + id[i]); 
            }
        }
        
        int k = id.length;

        if (legends != null && k > legends.length) {
            throw new IllegalArgumentException("Too few legends.");
        }
        
        if (palette != null && k > palette.length) {
            throw new IllegalArgumentException("Too few colors.");
        }

        this.data = data;
        this.y = y;
        this.legends = legends;
        this.palette = palette;
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(getColor());

        if (labels != null) {
            for (int i = 0; i < data.length; i++) {
                g.drawText(labels[i], data[i]);
            }
        } else {
            if (y == null) {
                for (int i = 0; i < data.length; i++) {
                    g.drawPoint(legend, data[i]);
                }
            } else {
                for (int i = 0; i < data.length; i++) {
                    if (palette != null) {
                        g.setColor(palette[valueLookupTable.get(y[i])]);
                    }
                    
                    if (legends != null) {
                        g.drawPoint(legends[valueLookupTable.get(y[i])], data[i]);
                    } else {
                        g.drawPoint(legend, data[i]);
                    }
                }
            }
        }

        g.setColor(c);
    }

    /**
     * Set the legend of points.
     */
    public void setLegend(char legend) {
        this.legend = legend;
    }

    /**
     * Get the legend of points.
     */
    public char getLegend() {
        return legend;
    }
    /**
     * Create a plot canvas with the scatter plot of given data.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of n points.
     */
    public static PlotCanvas plot(double[]... data) {
        return plot(null, data);
    }

    /**
     * Create a plot canvas with the scatter plot of given data.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of n points.
     */
    public static PlotCanvas plot(String id, double[]... data) {
        if (data[0].length != 2 && data[0].length != 3) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        ScatterPlot plot = new ScatterPlot(data);
        plot.setID(id);
        canvas.add(plot);
        return canvas;
    }

    /**
     * Create a plot canvas with the scatter plot of given data.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of n points.
     * @param labels labels of points.
     */
    public static PlotCanvas plot(double[][] data, String[] labels) {
        return plot(null, data, labels);
    }

    /**
     * Create a plot canvas with the scatter plot of given data.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of n points.
     * @param labels labels of points.
     */
    public static PlotCanvas plot(String id, double[][] data, String[] labels) {
        if (data[0].length != 2 && data[0].length != 3) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        if (data.length != labels.length) {
            throw new IllegalArgumentException("The number of points and that of labels are not same.");
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        ScatterPlot plot = new ScatterPlot(data, labels);
        plot.setID(id);
        canvas.add(plot);
        return canvas;
    }

    /**
     * Create a plot canvas with the scatter plot of given data and specific color.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param color the color used to draw points.
     */
    public static PlotCanvas plot(double[][] data, Color color) {
        return plot(null, data, color);
    }

    /**
     * Create a plot canvas with the scatter plot of given data and specific color.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param color the color used to draw points.
     */
    public static PlotCanvas plot(String id, double[][] data, Color color) {
        if (data[0].length != 2 && data[0].length != 3) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        ScatterPlot plot = new ScatterPlot(data);
        plot.setID(id);
        plot.setColor(color);
        canvas.add(plot);

        return canvas;
    }

    /**
     * Create a plot canvas with the scatter plot of given data with specific legend.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param legend the legend used to draw points.
     * <ul>
     * <li> . : dot
     * <li> + : +
     * <li> - : -
     * <li> | : |
     * <li> * : star
     * <li> x : x
     * <li> o : circle
     * <li> O : large circle
     * <li> @ : solid circle
     * <li> # : large solid circle
     * <li> s : square
     * <li> S : large square
     * <li> q : solid square
     * <li> Q : large solid square
     * <li> others : dot
     * </ul>
     */
    public static PlotCanvas plot(double[][] data, char legend) {
        return plot(null, data, legend);
    }

    /**
     * Create a plot canvas with the scatter plot of given data with specific legend.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param legend the legend used to draw points.
     * <ul>
     * <li> . : dot
     * <li> + : +
     * <li> - : -
     * <li> | : |
     * <li> * : star
     * <li> x : x
     * <li> o : circle
     * <li> O : large circle
     * <li> @ : solid circle
     * <li> # : large solid circle
     * <li> s : square
     * <li> S : large square
     * <li> q : solid square
     * <li> Q : large solid square
     * <li> others : dot
     * </ul>
     */
    public static PlotCanvas plot(String id, double[][] data, char legend) {
        if (data[0].length != 2 && data[0].length != 3) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        ScatterPlot plot = new ScatterPlot(data, legend);
        plot.setID(id);
        canvas.add(plot);

        return canvas;
    }

    /**
     * Create a plot canvas with the scatter plot of given data with specific legend and color.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param color the color used to draw points.
     * @param legend the legend used to draw points.
     * <ul>
     * <li> . : dot
     * <li> + : +
     * <li> - : -
     * <li> | : |
     * <li> * : star
     * <li> x : x
     * <li> o : circle
     * <li> O : large circle
     * <li> @ : solid circle
     * <li> # : large solid circle
     * <li> s : square
     * <li> S : large square
     * <li> q : solid square
     * <li> Q : large solid square
     * <li> others : dot
     * </ul>
     */
    public static PlotCanvas plot(double[][] data, char legend, Color color) {
        return plot(null, data, legend, color);
    }

    /**
     * Create a plot canvas with the scatter plot of given data with specific legend and color.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param color the color used to draw points.
     * @param legend the legend used to draw points.
     * <ul>
     * <li> . : dot
     * <li> + : +
     * <li> - : -
     * <li> | : |
     * <li> * : star
     * <li> x : x
     * <li> o : circle
     * <li> O : large circle
     * <li> @ : solid circle
     * <li> # : large solid circle
     * <li> s : square
     * <li> S : large square
     * <li> q : solid square
     * <li> Q : large solid square
     * <li> others : dot
     * </ul>
     */
    public static PlotCanvas plot(String id, double[][] data, char legend, Color color) {
        if (data[0].length != 2 && data[0].length != 3) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        ScatterPlot plot = new ScatterPlot(data, legend);
        plot.setID(id);
        plot.setColor(color);
        canvas.add(plot);

        return canvas;
    }

    /**
     * Create a plot canvas with the scatter plot of given data with specific legend and color.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param y the class labels of data.
     * @param palette the colors for each class.
     * @param legend the legend used to draw points.
     * <ul>
     * <li> . : dot
     * <li> + : +
     * <li> - : -
     * <li> | : |
     * <li> * : star
     * <li> x : x
     * <li> o : circle
     * <li> O : large circle
     * <li> @ : solid circle
     * <li> # : large solid circle
     * <li> s : square
     * <li> S : large square
     * <li> q : solid square
     * <li> Q : large solid square
     * <li> others : dot
     * </ul>
     */
    public static PlotCanvas plot(double[][] data, int[] y, char legend, Color[] palette) {
        return plot(null, data, y, legend, palette);
    }

    /**
     * Create a plot canvas with the scatter plot of given data with specific legend and color.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param y the class labels of data.
     * @param palette the colors for each class.
     * @param legend the legend used to draw points.
     * <ul>
     * <li> . : dot
     * <li> + : +
     * <li> - : -
     * <li> | : |
     * <li> * : star
     * <li> x : x
     * <li> o : circle
     * <li> O : large circle
     * <li> @ : solid circle
     * <li> # : large solid circle
     * <li> s : square
     * <li> S : large square
     * <li> q : solid square
     * <li> Q : large solid square
     * <li> others : dot
     * </ul>
     */
    public static PlotCanvas plot(String id, double[][] data, int[] y, char legend, Color[] palette) {
        if (data[0].length != 2 && data[0].length != 3) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        ScatterPlot plot = new ScatterPlot(data, y, legend, palette);
        plot.setID(id);
        canvas.add(plot);

        return canvas;
    }

    /**
     * Create a plot canvas with the scatter plot of given data with specific legend and color.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param y the class labels of data.
     * @param palette the colors for each class.
     * @param legend the legend used to draw points.
     * <ul>
     * <li> . : dot
     * <li> + : +
     * <li> - : -
     * <li> | : |
     * <li> * : star
     * <li> x : x
     * <li> o : circle
     * <li> O : large circle
     * <li> @ : solid circle
     * <li> # : large solid circle
     * <li> s : square
     * <li> S : large square
     * <li> q : solid square
     * <li> Q : large solid square
     * <li> others : dot
     * </ul>
     */
    public static PlotCanvas plot(double[][] data, int[] y, char[] legend, Color[] palette) {
        return plot(null, data, y, legend, palette);
    }

    /**
     * Create a plot canvas with the scatter plot of given data with specific legend and color.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param y the class labels of data.
     * @param palette the colors for each class.
     * @param legend the legend used to draw points.
     * <ul>
     * <li> . : dot
     * <li> + : +
     * <li> - : -
     * <li> | : |
     * <li> * : star
     * <li> x : x
     * <li> o : circle
     * <li> O : large circle
     * <li> @ : solid circle
     * <li> # : large solid circle
     * <li> s : square
     * <li> S : large square
     * <li> q : solid square
     * <li> Q : large solid square
     * <li> others : dot
     * </ul>
     */
    public static PlotCanvas plot(String id, double[][] data, int[] y, char[] legend, Color[] palette) {
        if (data[0].length != 2 && data[0].length != 3) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        ScatterPlot plot = new ScatterPlot(data, y, legend, palette);
        plot.setID(id);
        canvas.add(plot);

        return canvas;
    }
}