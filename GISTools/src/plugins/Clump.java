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
import whitebox.geospatialfiles.WhiteboxRaster;
import whitebox.interfaces.WhiteboxPlugin;
import whitebox.interfaces.WhiteboxPluginHost;
/**
 * WhiteboxPlugin is used to define a plugin tool for Whitebox GIS.
 * @author Dr. John Lindsay <jlindsay@uoguelph.ca>
 */
public class Clump implements WhiteboxPlugin {
    
    private WhiteboxPluginHost myHost = null;
    private String[] args;
    double noData = -32768;
    WhiteboxRaster image;
    WhiteboxRaster output;
    double currentPatchNumber = 0;
    double currentImageValue = 0;
    int maxDepth = 1000;
    int depth = 0;
    int[] dX = new int[]{1, 1, 1, 0, -1, -1, -1, 0};
    int[] dY = new int[]{-1, 0, 1, 1, 1, 0, -1, -1};
    int numScanCells = 8;
    /**
     * Used to retrieve the plugin tool's name. This is a short, unique name containing no spaces.
     * @return String containing plugin name.
     */
    @Override
    public String getName() {
        return "Clump";
    }
    /**
     * Used to retrieve the plugin tool's descriptive name. This can be a longer name (containing spaces) and is used in the interface to list the tool.
     * @return String containing the plugin descriptive name.
     */
    @Override
    public String getDescriptiveName() {
    	return "Clump (Group)";
    }
    /**
     * Used to retrieve a short description of what the plugin tool does.
     * @return String containing the plugin's description.
     */
    @Override
    public String getToolDescription() {
    	return "Groups cells that form physically discrete areas, assigning "
                + "them unique identifiers.";
    }
    /**
     * Used to identify which toolboxes this plugin tool should be listed in.
     * @return Array of Strings.
     */
    @Override
    public String[] getToolbox() {
    	String[] ret = { "GISTools" };
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
    private void showFeedback(String message) {
        if (myHost != null) {
            myHost.showFeedback(message);
        } else {
            System.out.println(message);
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

    private int previousProgress = 0;
    private String previousProgressLabel = "";
    /**
     * Used to communicate a progress update between a plugin tool and the main Whitebox user interface.
     * @param progressLabel A String to use for the progress label.
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(String progressLabel, int progress) {
        if (myHost != null && ((progress != previousProgress) || 
                (!progressLabel.equals(previousProgressLabel)))) {
            myHost.updateProgress(progressLabel, progress);
        }
        previousProgress = progress;
        previousProgressLabel = progressLabel;
    }
    /**
     * Used to communicate a progress update between a plugin tool and the main Whitebox user interface.
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(int progress) {
        if (myHost != null && progress != previousProgress) {
            myHost.updateProgress(progress);
        }
        previousProgress = progress;
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
        int row, col;
        float progress = 0;
        double maxPatchValue = -1;
        int x, y;
        boolean blnFoundNeighbour;
        boolean blnIncludeDiagNeighbour = false;
        boolean blnTreatZerosAsBackground = false;
        int i;
        
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
                blnIncludeDiagNeighbour = Boolean.parseBoolean(args[i]);
                if (!blnIncludeDiagNeighbour) {
                    dX = new int[]{0, 1, 0, -1};
                    dY = new int[]{-1, 0, 1, 0};
                }
            } else if (i == 3) {
                blnTreatZerosAsBackground = Boolean.parseBoolean(args[i]);
            }
        }

        // check to see that the inputHeader and outputHeader are not null.
        if ((inputHeader == null) || (outputHeader == null)) {
            showFeedback("One or more of the input parameters have not been set properly.");
            return;
        }

        try {
            numScanCells = dY.length;
        
            image = new WhiteboxRaster(inputHeader, "r");
            int rows = image.getNumberRows();
            int cols = image.getNumberColumns();
            noData = image.getNoDataValue();
            
            double initialValue = -1;
            output = new WhiteboxRaster(outputHeader, "rw", inputHeader, WhiteboxRaster.DataType.FLOAT, initialValue);
            output.setDataScale(WhiteboxRaster.DataScale.CATEGORICAL);
            output.setPreferredPalette("qual.pal");
            
            if (blnTreatZerosAsBackground) {
                for (row = 0; row < rows; row++) {
                    for (col = 0; col < cols; col++) {
                        if (image.getValue(row, col) == 0) {
                            output.setValue(row, col, 0);
                        }
                    }
                    if (cancelOp) {
                        cancelOperation();
                        return;
                    }
                    progress = (float) (100f * row / (rows - 1));
                    updateProgress((int) progress);
                }
                if (output.getValue(0, 0) == -1) {
                    output.setValue(0, 0, 1);
                }
            } else {
                output.setValue(0, 0, 0);
            }
            
            double patchValue = 0;
            double neighbourPatchValue = 0;
            double newPatchValue = 0;
            double imageValue = 0;
            int loopNum = 1;
            updateProgress("Loop " + loopNum + ":", 0);
            for (row = 0; row < rows; row++) {
                for (col = 0; col < cols; col++) {
                    imageValue = image.getValue(row, col);
                    if (imageValue != noData) {
                        patchValue = output.getValue(row, col);
                        if (patchValue == initialValue) {
                            // see if any neighbour has the same value in the input image
                            blnFoundNeighbour = false;
                            for (i = 0; i < numScanCells; i++) {
                                x = col + dX[i];
                                y = row + dY[i];
                                neighbourPatchValue = output.getValue(y, x);
                                if (neighbourPatchValue != initialValue 
                                        && image.getValue(y, x) == imageValue) {
                                    // cell is neighbouring a cell with the same value in image that
                                    // has already been assigned a patch value
                                    output.setValue(row, col, neighbourPatchValue);
                                    newPatchValue = neighbourPatchValue;
                                    blnFoundNeighbour = true;
                                    break;
                                }
                            }
                            if (!blnFoundNeighbour) {
                                // no neighbouring cell has the same value in Image and has 
                                // already been assigned a value. A new one is needed.
                                maxPatchValue++;
                                newPatchValue = maxPatchValue;
                                output.setValue(row, col, newPatchValue);
                            }
                            
                            // recursively scan all connected cells of equal value in Image
                            depth = 0;
                            ScanConnectedCells(row, col, imageValue, initialValue, newPatchValue);
                        }
                    } else {
                        output.setValue(row, col, noData);
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * row / (rows - 1));
                updateProgress((int) progress);
            }
            
            // find all cells with neighbouring cells that have the same value in
            // the input image but different patch values in the output image.
            // Recursively scan them to change the larger patch ID to the lower value.
            // Iterate this process until there are no further changes to the image.
            boolean somethingDone;
            double[] reclass = new double[(int) maxPatchValue + 1];
            // this array is used to keep track of the eliminated patches.
            do {
                loopNum++;
                updateProgress("Loop " + loopNum + ":", 0);
                somethingDone = false;
                for (row = 0; row < rows; row++) {
                    for (col = 0; col < cols; col++) {
                        imageValue = image.getValue(row, col);
                        if (imageValue != noData) {
                            patchValue = output.getValue(row, col);
                            for (i = 0; i < numScanCells; i++) {
                                x = col + dX[i];
                                y = row + dY[i];
                                neighbourPatchValue = output.getValue(y, x);
                                if (neighbourPatchValue != patchValue 
                                        && image.getValue(y, x) == imageValue) {
                                    // The two patches are equivalent. Find the 
                                    // lower valued cell and initiate a recursive
                                    // scan from there.
                                    somethingDone = true;
                                    if (patchValue < neighbourPatchValue) {
                                        reclass[(int)neighbourPatchValue] = -1;
                                        output.setValue(y, x, patchValue);
                                        ScanConnectedCells(y, x, imageValue, neighbourPatchValue, patchValue);
                                    } else {
                                        reclass[(int)patchValue] = -1;
                                        output.setValue(row, col, neighbourPatchValue);
                                        ScanConnectedCells(row, col, imageValue, patchValue, neighbourPatchValue);
                                        patchValue = neighbourPatchValue;
                                    }

                                }
                            }
                        }
                    }
                    if (cancelOp) {
                        cancelOperation();
                        return;
                    }
                    progress = (float) (100f * row / (rows - 1));
                    updateProgress((int) progress);
                }
            } while (somethingDone);
            
            i = 0;
            for (int a = 0; a < maxPatchValue + 1; a++) {
                if (reclass[a] != -1) {
                    reclass[a] = i;
                    i++;
                }
            }
            
            loopNum++;
            updateProgress("Loop " + loopNum + ":", 0);
            for (row = 0; row < rows; row++) {
                for (col = 0; col < cols; col++) {
                    patchValue = output.getValue(row, col);
                    if (patchValue != noData) {
                        output.setValue(row, col, reclass[(int)patchValue]);
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * row / (rows - 1));
                updateProgress((int) progress);
            }
            

            output.addMetadataEntry("Created by the "
                    + getDescriptiveName() + " tool.");
            output.addMetadataEntry("Created on " + new Date());
            
            image.close();
            output.close();
            
            // returning a header file string displays the image.
            returnData(outputHeader);

        } catch (Exception e) {
            showFeedback(e.getMessage());
        } finally {
            updateProgress("Progress: ", 0);
            // tells the main application that this process is completed.
            amIActive = false;
            myHost.pluginComplete();
        }
    }
    
    private void ScanConnectedCells(int row, int col, double imageValue, double currentPatchValue, double newPatchValue) {
        depth++;
        int x, y;
        if (depth < maxDepth) {
            for (int c = 0; c < numScanCells; c++) {
                x = col + dX[c];
                y = row + dY[c];
                if ((output.getValue(y, x) == currentPatchValue) && 
                        (image.getValue(y, x) == imageValue)) {
                    // cell should be assigned the new patch value and has the same value in Image
                    output.setValue(y, x, newPatchValue);
                    ScanConnectedCells(y, x, imageValue, currentPatchValue, newPatchValue);
                }
            }
        }
        depth--;
    }
}