/*
 * Copyright (C) 2011 Dr. John Lindsay <jlindsay@uoguelph.ca>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package plugins;

import java.util.Date;
import java.text.DecimalFormat;
import whitebox.geospatialfiles.WhiteboxRaster;
import whitebox.interfaces.WhiteboxPlugin;
import whitebox.interfaces.WhiteboxPluginHost;
/**
 * WhiteboxPlugin is used to define a plugin tool for Whitebox GIS.
 * @author Dr. John Lindsay <jlindsay@uoguelph.ca>
 */
public class FractalDimension implements WhiteboxPlugin {

    private WhiteboxPluginHost myHost;
    private String[] args;
    /**
     * Used to retrieve the plugin tool's name. This is a short, unique name
     * containing no spaces.
     *
     * @return String containing plugin name.
     */
    @Override
    public String getName() {
        return "FractalDimension";
    }
    /**
     * Used to retrieve the plugin tool's descriptive name. This can be a longer name (containing spaces) and is used in the interface to list the tool.
     * @return String containing the plugin descriptive name.
     */
    @Override
    public String getDescriptiveName() {
    	return "Fractal Dimension";
    }
    /**
     * Used to retrieve a short description of what the plugin tool does.
     * @return String containing the plugin's description.
     */
    @Override
    public String getToolDescription() {
    	return "Measures the fractal "
                + "dimension of patch raster polygons.";
    }
    /**
     * Used to identify which toolboxes this plugin tool should be listed in.
     * @return Array of Strings.
     */
    @Override
    public String[] getToolbox() {
    	String[] ret = { "PatchShapeTools" };
    	return ret;
    }

    /**
     * Sets the WhiteboxPluginHost to which the plugin tool is tied. This is the class
     * that the plugin will send all feedback messages, progress updates, and return objects.
     * @param host The WhiteboxPluginHost that called the plugin tool.
     */  
    @Override
    public void setPluginHost(WhiteboxPluginHost host) {
        myHost = host;
    }
    /**
     * Used to communicate feedback pop-up messages between a plugin tool and the main Whitebox user-interface.
     * @param feedback String containing the text to display.
     */
    private void showFeedback(String feedback) {
        if (myHost != null) {
            myHost.showFeedback(feedback);
        } else {
            System.out.println(feedback);
        }
    }
    /**
     * Used to communicate a return object from a plugin tool to the main Whitebox user-interface.
     * @return Object, such as an output WhiteboxRaster.
     */
    private void returnData(Object ret) {
        if (myHost != null) {
            myHost.returnData(ret);
        }
    }
    /**
     * Used to communicate a progress update between a plugin tool and the main Whitebox user interface.
     * @param progressLabel A String to use for the progress label.
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(String progressLabel, int progress) {
        if (myHost != null) {
            myHost.updateProgress(progressLabel, progress);
        } else {
            System.out.println(progressLabel + " " + progress + "%");
        }
    }
    /**
     * Used to communicate a progress update between a plugin tool and the main Whitebox user interface.
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(int progress) {
        if (myHost != null) {
            myHost.updateProgress(progress);
        } else {
            System.out.print("Progress: " + progress + "%");
        }
    }
    /**
     * Sets the arguments (parameters) used by the plugin.
     * @param args 
     */
    @Override
    public void setArgs(String[] args) {
        this.args = args.clone();
    }
    
    private boolean cancelOp = false;
    /**
     * Used to communicate a cancel operation from the Whitebox GUI.
     * @param cancel Set to true if the plugin should be canceled.
     */
    @Override
    public void setCancelOp(boolean cancel) {
        cancelOp = cancel;
    }
    
    private void cancelOperation() {
        showFeedback("Operation cancelled.");
        updateProgress("Progress: ", 0);
    }
    
    private boolean amIActive = false;
    /**
     * Used by the Whitebox GUI to tell if this plugin is still running.
     * @return a boolean describing whether or not the plugin is actively being used.
     */
    @Override
    public boolean isActive() {
        return amIActive;
    }
    
    @Override
    public void run() {
        amIActive = true;

        String inputHeader = null;
        String outputHeader = null;
        int col;
        int row;
        int i;
        int j;
        int numCols;
        int numRows;
        int a;
        int[] dX = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dY = {-1, -1, -1, 0, 0, 1, 1, 1};
        int[] val = {1, 2, 4, 8, 16, 32, 64, 128};
        double z;
        int val1;
        int val2;
        float progress;
        int range;
        boolean zeroAsBackground = false;
        boolean blnTextOutput = false;
        double gridRes;
        String XYUnits;

        double[] LUT = {4.000000000, 2.828427125, 2.236067977, 2.414213562, 2.828427125, 3.000000000,
            2.414213562, 2.236067977, 2.236067977, 2.414213562, 2.000000000,
            2.000000000, 2.828427125, 1.414213562, 1.414213562, 1.414213562,
            2.236067977, 2.828427125, 2.000000000, 1.414213562, 2.414213562,
            1.414213562, 2.000000000, 1.414213562, 2.000000000, 2.000000000,
            1.000000000, 2.000000000, 2.000000000, 2.000000000, 2.000000000,
            1.000000000, 2.828427125, 3.000000000, 2.828427125, 1.414213562,
            2.000000000, 4.000000000, 2.236067977, 2.236067977, 2.414213562,
            2.236067977, 1.414213562, 1.414213562, 2.236067977, 2.236067977,
            1.414213562, 1.414213562, 2.828427125, 2.236067977, 1.414213562,
            1.414213562, 2.236067977, 2.414213562, 2.000000000, 1.414213562, 2.000000000, 2.000000000, 1.000000000,
            1.414213562, 2.000000000, 2.000000000, 1.000000000, 1.000000000, 2.236067977, 2.828427125, 2.000000000,
            2.000000000, 2.828427125, 2.236067977, 2.000000000, 2.000000000, 2.000000000, 1.414213562, 1.000000000,
            2.000000000, 1.414213562, 1.414213562, 1.000000000, 1.414213562, 2.000000000, 1.414213562,
            1.000000000, 1.000000000, 1.414213562, 1.414213562, 2.000000000, 1.414213562, 1.000000000, 1.000000000,
            0.000000000, 0.000000000, 1.000000000, 1.000000000, 0.000000000, 0.000000000, 2.414213562, 1.414213562,
            2.000000000, 2.000000000, 2.236067977, 2.414213562, 2.000000000, 2.000000000, 2.000000000, 1.414213562,
            2.000000000, 1.000000000, 2.000000000, 1.414213562, 1.000000000, 1.000000000, 1.414213562, 1.414213562,
            1.000000000, 1.000000000, 1.414213562, 1.414213562, 1.000000000, 1.000000000, 2.000000000, 1.414213562,
            0.000000000, 0.000000000, 1.000000000, 1.000000000, 0.000000000, 0.000000000, 2.828427125, 2.000000000,
            2.828427125, 2.236067977, 3.000000000, 4.000000000, 1.414213562, 2.236067977,
            2.828427125, 2.236067977, 1.414213562, 2.000000000, 2.236067977, 2.414213562, 1.414213562, 1.414213562,
            2.414213562, 2.236067977, 1.414213562, 1.414213562, 2.236067977, 2.236067977, 1.414213562, 1.414213562,
            2.000000000, 2.000000000, 1.000000000, 1.000000000, 2.000000000, 2.000000000, 1.414213562, 1.000000000,
            3.000000000, 4.000000000, 2.236067977, 2.414213562, 4.000000000, 4.000000000, 2.414213562, 2.236067977,
            1.414213562, 2.236067977, 1.414213562, 1.414213562, 2.414213562, 2.236067977, 1.414213562, 1.414213562,
            1.414213562, 2.414213562, 1.414213562, 1.414213562, 2.236067977, 2.236067977,
            1.414213562, 1.414213562, 2.000000000, 2.000000000, 1.000000000, 1.000000000, 2.000000000, 2.000000000,
            1.000000000, 1.000000000, 2.414213562, 2.000000000, 2.236067977, 2.000000000, 1.414213562, 2.414213562,
            2.000000000, 2.000000000, 1.414213562, 1.414213562, 1.000000000, 1.000000000, 1.414213562, 1.414213562,
            1.000000000, 1.000000000, 2.000000000, 2.000000000, 2.000000000, 1.000000000, 1.414213562, 1.414213562,
            1.000000000, 1.000000000, 2.000000000, 1.000000000, 0.000000000, 0.000000000, 1.414213562, 1.000000000,
            0.000000000, 0.000000000, 2.236067977, 2.236067977, 2.000000000, 2.000000000, 2.236067977, 2.236067977,
            2.000000000, 2.000000000, 1.414213562, 1.414213562, 1.414213562, 1.000000000, 1.414213562, 1.414213562,
            1.000000000, 1.000000000, 1.414213562, 1.414213562, 1.414213562, 1.000000000, 1.414213562, 1.414213562,
            1.000000000, 1.000000000, 1.000000000, 1.000000000, 0.000000000, 0.000000000, 1.000000000, 1.000000000,
            0.000000000, 0.000000000};


        if (args.length <= 0) {
            showFeedback("Plugin parameters have not been set.");
            return;
        }

        for (i = 0; i < args.length; i++) {
            if (i == 0) {
                inputHeader = args[i];
            } else if (i == 1) {
                outputHeader = args[i];
            } else if (i == 2) {
                blnTextOutput = Boolean.parseBoolean(args[i]);
            } else if (i == 3) {
                zeroAsBackground = Boolean.parseBoolean(args[i]);
            }
        }

        // check to see that the inputHeader and outputHeader are not null.
        if ((inputHeader == null) || (outputHeader == null)) {
            showFeedback("One or more of the input parameters have not been set properly.");
            return;
        }

        try {
            WhiteboxRaster image = new WhiteboxRaster(inputHeader, "r");

            numRows = image.getNumberRows();
            numCols = image.getNumberColumns();
            double noData = image.getNoDataValue();
            gridRes = (image.getCellSizeX() + image.getCellSizeY()) / 2;
            XYUnits = image.getXYUnits();
            
            WhiteboxRaster output = new WhiteboxRaster(outputHeader, "rw", inputHeader, WhiteboxRaster.DataType.FLOAT, noData);
            output.setPreferredPalette("spectrum.pal");
            output.setDataScale(WhiteboxRaster.DataScale.CONTINUOUS);

            int minValue = (int)(image.getMinimumValue());
            int maxValue = (int)(image.getMaximumValue());
            range = maxValue - minValue;

            double[] perimeter = new double[range + 1];
            double[] area = new double[range + 1];

            updateProgress("Loop 1 of 3:", 0);
            double[] data = null;
            for (row = 0; row < numRows; row++) {
                //data = image.getRowValues(row);
                for (col = 0; col < numCols; col++) {
                    z = image.getValue(row, col);
                    if (z != noData) {
                        val2 = 0;
                        for (a = 0; a < 8; a++) {
                            i = col + dX[a];
                            j = row + dY[a];
                            if (image.getValue(j, i) == z) {
                                val2 += val[a];
                            }
                        }
                        output.setValue(row, col, LUT[val2]);
                    } else {
                        output.setValue(row, col, noData);
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * row / (numRows - 1));
                updateProgress("Loop 1 of 3:", (int) progress);
            }

            updateProgress("Loop 2 of 3:", 0);
            for (row = 0; row < numRows; row++) {
                data = image.getRowValues(row);
                for (col = 0; col < numCols; col++) {
                    if (data[col] != noData) {
                        val1 = (int) (data[col] - minValue);
                        perimeter[val1] += output.getValue(row, col);
                        area[val1]++;
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * row / (numRows - 1));
                updateProgress("Loop 2 of 3:", (int) progress);
            }

            if (zeroAsBackground) {
                perimeter[0 - minValue] = 0;
                for (a = 1; a < perimeter.length; a++) {
                    perimeter[a] = (double) ((2 * Math.log(perimeter[a] * gridRes)) / (Math.log(area[a] * gridRes * gridRes)));
                }
            } else {
                for (a = 0; a < perimeter.length; a++) {
                    perimeter[a] = (double) ((2 * Math.log(perimeter[a] * gridRes)) / (Math.log(area[a] * gridRes * gridRes)));
                }
            }
        
            updateProgress("Loop 3 of 3:", 0);
            for (row = 0; row < numRows; row++) {
                data = image.getRowValues(row);
                for (col = 0; col < numCols; col++) {
                    if (data[col] != noData) {
                        val1 = (int) (data[col] - minValue);
                        output.setValue(row, col, perimeter[val1]);
                    } else {
                        output.setValue(row, col, noData);
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * row / (numRows - 1));
                updateProgress("Loop 3 of 3:", (int) progress);
            }
         
            output.addMetadataEntry("Created by the "
                    + getDescriptiveName() + " tool.");
            output.addMetadataEntry("Created on " + new Date());

            image.close();
            output.close();

            if (blnTextOutput) {
                DecimalFormat df;
                df = new DecimalFormat("0.0000");

                String retstr = "Fractal Dimension Analysis";

                for (a = 0; a < perimeter.length; a++) {
                    if (perimeter[a] > 0) {
                        retstr = retstr + "\n" + (minValue + a) + "\t" + df.format(perimeter[a]);
                    }
                }

                returnData(retstr);
            }

            // returning a header file string displays the image.
            returnData(outputHeader);
            
            
        } catch (Exception e) {
            showFeedback(e.getMessage());
            showFeedback(e.getCause().toString());
        } finally {
            updateProgress("Progress: ", 0);
            // tells the main application that this process is completed.
            amIActive = false;
            myHost.pluginComplete();
        }
    }
}