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

package smile.demo.projection;

import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.io.DatasetReader;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

@SuppressWarnings("serial")
public abstract class ProjectionDemo extends JPanel implements Runnable, ActionListener {

    private static String[] datasetName = {
        "IRIS", "US Arrests", "Food Nutrition",
        "Pen Digits", "COMBO-17"
    };

    private static String[] datasource = {
        "classification/iris.txt",
        "projection/USArrests.txt",
        "projection/food.txt",
        "classification/pendigits.txt",
        "projection/COMBO17.dat"
    };

    protected static DataFrame[] dataset = new DataFrame[datasetName.length];
    protected static Formula[] formula = {
            Formula.lhs("class"),
            null,
            null,
            Formula.lhs("class"),
            Formula.lhs("class"),
    };
    protected static int datasetIndex = 0;
    
    JPanel optionPane;
    JComponent canvas;
    private JButton startButton;
    private JComboBox<String> datasetBox;
    protected char pointLegend = '.';

    /**
     * Constructor.
     */
    public ProjectionDemo() {
        startButton = new JButton("Start");
        startButton.setActionCommand("startButton");
        startButton.addActionListener(this);

        datasetBox = new JComboBox<>();
        for (int i = 0; i < datasetName.length; i++) {
            datasetBox.addItem(datasetName[i]);
        }
        datasetBox.setSelectedIndex(0);
        datasetBox.setActionCommand("datasetBox");
        datasetBox.addActionListener(this);

        optionPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPane.setBorder(BorderFactory.createRaisedBevelBorder());
        optionPane.add(startButton);
        optionPane.add(new JLabel("Dataset:"));
        optionPane.add(datasetBox);

        setLayout(new BorderLayout());
        add(optionPane, BorderLayout.NORTH);
    }

    /**
     * Execute the projection algorithm and return a swing JComponent representing
     * the clusters.
     */
    public abstract JComponent learn();

    @Override
    public void run() {
        startButton.setEnabled(false);
        datasetBox.setEnabled(false);

        try {
            JComponent plot = learn();
            if (plot != null) {
                if (canvas != null)
                    remove(canvas);
                canvas = plot;
                add(plot, BorderLayout.CENTER);
            }
            validate();
        } catch (Exception ex) {
            System.err.println(ex);
        }
        
        startButton.setEnabled(true);
        datasetBox.setEnabled(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("startButton".equals(e.getActionCommand())) {
            datasetIndex = datasetBox.getSelectedIndex();
            
            if (dataset[datasetIndex] == null) {
                CSVFormat format = CSVFormat.DEFAULT.withDelimiter('\t');
                if (datasetIndex == 3) {
                    format = format.withFirstRecordAsHeader();
                }

                try {
                    dataset[datasetIndex] = DatasetReader.csv(smile.util.Paths.getTestData(datasource[datasetIndex]), format);
                    if (datasetIndex == 1) {
                        dataset[datasetIndex] = dataset[datasetIndex].drop(0);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "Failed to load dataset.", "ERROR", JOptionPane.ERROR_MESSAGE);
                    System.out.println(ex);
                }
            }

            if (dataset[datasetIndex].size() < 500) {
                pointLegend = 'o';
            } else {
                pointLegend = '.';
            }
            
            Thread thread = new Thread(this);
            thread.start();
        }
    }
}
