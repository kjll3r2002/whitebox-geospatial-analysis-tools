package whiteboxgis;

import java.awt.Point;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.MemoryImageSource;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.File;
import java.text.DecimalFormat;
import javax.imageio.ImageIO;
import javax.swing.*;
import whitebox.cartographic.PointMarkers;
import whitebox.geospatialfiles.shapefile.*;
import whitebox.interfaces.MapLayer.MapLayerType;
import whitebox.interfaces.WhiteboxPluginHost;
import whitebox.structures.DimensionBox;
import whitebox.structures.GridCell;
import whitebox.utilities.OSFinder;

/**
 * @author johnlindsay
 */
public class MapRenderer extends JPanel implements Printable, MouseMotionListener, 
        MouseListener, ImageObserver {
    private int borderWidth = 20;
    private DimensionBox mapExtent = new DimensionBox();
    public MapInfo mapinfo = null;
    private StatusBar status = null;
    private WhiteboxPluginHost host = null;
    public static final int MOUSE_MODE_ZOOM = 0;
    public static final int MOUSE_MODE_PAN = 1;
    public static final int MOUSE_MODE_GETINFO = 2;
    public static final int MOUSE_MODE_SELECT = 3;
    private int myMode = 0;
    private boolean modifyingPixels = false;
    private int modifyPixelsX = -1;
    private int modifyPixelsY = -1;
    private boolean usingDistanceTool = false;
    private Cursor zoomCursor = null;
    private Cursor panCursor = null;
    private Cursor panClosedHandCursor = null;
    private Cursor selectCursor = null;
    private String graphicsDirectory;
    
    public MapRenderer() {
        init();
    }

    private void init() {
        try {
            String applicationDirectory = java.net.URLDecoder.decode(getClass().getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF-8");
            if (applicationDirectory.endsWith(".exe") || applicationDirectory.endsWith(".jar")) {
                applicationDirectory = new File(applicationDirectory).getParent();
            } else {
                // Add the path to the class files
                applicationDirectory += getClass().getName().replace('.', File.separatorChar);

                // Step one level up as we are only interested in the
                // directory containing the class files
                applicationDirectory = new File(applicationDirectory).getParent();
            }
            graphicsDirectory = applicationDirectory + File.separator +
                    "resources" + File.separator + "Images" + File.separator;

            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Point cursorHotSpot = new Point(0, 0);
            if (!OSFinder.isWindows()) {
                zoomCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "ZoomToBoxCursor.png"), cursorHotSpot, "zoomCursor");
                panCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "Pan3.png"), cursorHotSpot, "panCursor");
                panClosedHandCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "Pan4.png"), cursorHotSpot, "panCursor");
                selectCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "select.png"), cursorHotSpot, "selectCursor");
            } else {
                // windows requires 32 x 32 cursors. Otherwise they look terrible.
                zoomCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "ZoomToBoxCursorWin.png"), cursorHotSpot, "zoomCursor");
                panCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "Pan3Win.png"), cursorHotSpot, "panCursor");
                panClosedHandCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "Pan4Win.png"), cursorHotSpot, "panCursor");
                selectCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "selectWin.png"), cursorHotSpot, "selectCursor");
            }
            this.setCursor(zoomCursor);
            this.addMouseMotionListener(this);
            this.addMouseListener(this);
        } catch (Exception e) {
            
        }
    }
    
    public MapInfo getMapInfo() {
        return mapinfo;
    }
    
    public void setMapInfo(MapInfo mapinfo) {
        this.mapinfo = mapinfo;
    }

    public void setStatusBar(StatusBar status) {
        this.status = status;
    }

    public int getMouseMode() {
        return myMode;
    }

    public void setMouseMode(int mouseMode) {
        myMode = mouseMode;
        
        switch (myMode) {
            case MOUSE_MODE_ZOOM:
                this.setCursor(zoomCursor);
                break;
            case MOUSE_MODE_PAN:
                this.setCursor(panCursor);
                break;
            case MOUSE_MODE_SELECT:
                this.setCursor(selectCursor);
                break;
        }
        
    }

    public void setHost(WhiteboxPluginHost host) {
        this.host = host;
    }
    
    public void setModifyingPixels(boolean val) {
        modifyingPixels = val;
    }

    public boolean isModifyingPixels() {
        return modifyingPixels;
    }
    
    public void setUsingDistanceTool(boolean val) {
        usingDistanceTool = val;
    }
    
    public boolean isUsingDistanceTool() {
        return usingDistanceTool;
    }
    
    @Override
    public void paint (Graphics g) {
        if (mapinfo.isCartoView()) {
            drawMapCartoView(g);
        } else {
            drawMapDataView(g);
        }
    }
    
    private void drawMapCartoView(Graphics g) {
        try {
            Graphics2D g2d = (Graphics2D)g;
            RenderingHints rh = new RenderingHints(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHints(rh);
            rh = new RenderingHints(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHints(rh);
            
            g2d.setColor(Color.white);
            g2d.fillRect(0, 0, getWidth(), getHeight());
            
            if (mapinfo != null) {
                
                // get the drawing area's width and height
                double myWidth = this.getWidth();
                double myHeight = this.getHeight();
                

                // get the page size information
                PageFormat pageFormat = mapinfo.getPageFormat();
                double pageWidth = pageFormat.getWidth();// / 72.0;
                double pageHeight = pageFormat.getHeight();// / 72.0;
                //double pageAspect = pageWidth / pageHeight;
                
                // set the scale
                int pageShadowSize = 5;
                double scale = Math.min(((myWidth - 4.0 * pageShadowSize) / pageWidth), 
                        ((myHeight - 4.0 * pageShadowSize) / pageHeight));

                // what are the margins of the page on the drawing area?
                
                double pageTop = myHeight / 2.0 - pageHeight / 2.0 * scale;
                double pageLeft = myWidth / 2.0 - pageWidth / 2.0 * scale;
                
                // draw the page on the drawing area if it is visible
                if (mapinfo.isCartoView()) {
                   
                    g2d.setColor(Color.GRAY);
                    g2d.fillRect((int) (pageLeft + pageShadowSize),
                            (int) (pageTop + pageShadowSize), (int) (pageWidth * scale),
                            (int) (pageHeight * scale));
                    g2d.setColor(Color.WHITE);
                    g2d.fillRect((int) pageLeft, (int) pageTop, (int) (pageWidth * scale),
                            (int) (pageHeight * scale));

                    g2d.setColor(Color.DARK_GRAY);
                    g2d.drawRect((int) pageLeft, (int) pageTop, (int) (pageWidth * scale),
                            (int) (pageHeight * scale));

                }
                
                int numLayers = mapinfo.getNumLayers();
                if (numLayers == 0) {
                    return;
                }
                
            }
        } catch (Exception e) {
            host.showFeedback(e.getMessage());
            //System.out.println(e.getMessage());
        }
    }
    
    private void drawMapDataView(Graphics g) {
        try {
            Graphics2D g2d = (Graphics2D)g;
            RenderingHints rh = new RenderingHints(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHints(rh);
            rh = new RenderingHints(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHints(rh);
            
            g2d.setColor(Color.white);
            g2d.fillRect(0, 0, getWidth(), getHeight());
            if (mapinfo != null) {
                int numLayers = mapinfo.getNumLayers();
                if (numLayers == 0) {
                    return;
                }

                double myWidth = this.getWidth() - borderWidth * 2;
                double myHeight = this.getHeight() - borderWidth * 2;
                int width, height;
                double scale;
                int x, y;

                DimensionBox currentExtent = mapinfo.getCurrentExtent();
                double xRange = Math.abs(currentExtent.getRight() - currentExtent.getLeft());
                double yRange = Math.abs(currentExtent.getTop() - currentExtent.getBottom());
                scale = Math.min((myWidth / xRange), (myHeight / yRange));

                int left = (int) (borderWidth + ((myWidth - xRange * scale) / 2));
                int top = (int) (borderWidth + ((myHeight - yRange * scale) / 2));
                
                // what are the edge coordinates of the actual map area
                double deltaY = (top - borderWidth) / scale;
                double deltaX = (left - borderWidth) / scale;
                mapExtent.setTop(currentExtent.getTop() + deltaY);
                mapExtent.setBottom(currentExtent.getBottom() - deltaY);
                mapExtent.setLeft(currentExtent.getLeft() - deltaX); 
                mapExtent.setRight(currentExtent.getRight() + deltaX);
                

                String XYUnits = "";
                
                for (int i = 0; i < numLayers; i++) {
                    if (mapinfo.getLayer(i).getLayerType() == MapLayerType.RASTER) {
                        RasterLayerInfo layer = (RasterLayerInfo) mapinfo.getLayer(i);
                        if (layer.getXYUnits().toLowerCase().contains("met")) {
                            XYUnits = " m";
                        } else if (layer.getXYUnits().toLowerCase().contains("deg")) {
                            XYUnits = "\u00B0";
                        } else if (!layer.getXYUnits().toLowerCase().contains("not specified")) {
                            XYUnits = " " + layer.getXYUnits();
                        }

                        if (layer.isVisible()) {

                            DimensionBox fe = layer.getFullExtent();

                            // does the layer even overlay with the mapExtent?
                            boolean flag = true;
                            if (fe.getTop() < mapExtent.getBottom()
                                    || fe.getRight() < mapExtent.getLeft()
                                    || fe.getBottom() > mapExtent.getTop()
                                    || fe.getLeft() > mapExtent.getRight()) {
                                flag = false;
                                
                            }
                            if (flag) {
                                DimensionBox layerCE = new DimensionBox();

                                layerCE.setTop(Math.min(fe.getTop(), mapExtent.getTop()));
                                layerCE.setRight(Math.min(fe.getRight(), mapExtent.getRight()));
                                layerCE.setBottom(Math.max(fe.getBottom(), mapExtent.getBottom()));
                                layerCE.setLeft(Math.max(fe.getLeft(), mapExtent.getLeft()));

                                layer.setCurrentExtent(layerCE);

                                x = (int) (left + (layerCE.getLeft() - currentExtent.getLeft()) * scale);
                                int layerWidth = (int) ((Math.abs(layerCE.getRight() - layerCE.getLeft())) * scale);
                                y = (int) (top + (currentExtent.getTop() - layerCE.getTop()) * scale);
                                int layerHeight = (int) ((Math.abs(layerCE.getTop() - layerCE.getBottom())) * scale);


                                int startR = (int) (Math.abs(layer.fullExtent.getTop() - layerCE.getTop()) / layer.getCellSizeY());
                                int endR = (int) (layer.getNumberRows() - (Math.abs(layer.fullExtent.getBottom() - layerCE.getBottom()) / layer.getCellSizeY()));
                                int startC = (int) (Math.abs(layer.fullExtent.getLeft() - layerCE.getLeft()) / layer.getCellSizeX());
                                int endC = (int) (layer.getNumberColumns() - (Math.abs(layer.fullExtent.getRight() - layerCE.getRight()) / layer.getCellSizeX()));
                                int numRows = endR - startR;
                                int numCols = endC - startC;

                                int res = (int) (Math.min(numRows / (double)layerHeight, numCols / (double)layerWidth));
                                
                                layer.setResolutionFactor(res);

                                if (layer.isDirty()) {
                                    layer.createPixelData();
                                }

                                width = layer.getImageWidth();
                                height = layer.getImageHeight();
                                Image image = createImage(new MemoryImageSource(width, height, layer.getPixelData(), 0, width));
                                if (!g2d.drawImage(image, x, y, layerWidth + 1, layerHeight + 1, this)) {
                                    // do nothing
                                }
                                
                            }
                        }
                    } else if (mapinfo.getLayer(i).getLayerType() == MapLayerType.VECTOR) {
                        VectorLayerInfo layer = (VectorLayerInfo) mapinfo.getLayer(i);
                        if (layer.getXYUnits().toLowerCase().contains("met")) {
                            XYUnits = " m";
                        } else if (layer.getXYUnits().toLowerCase().contains("deg")) {
                            XYUnits = "\u00B0";
                        } else if (!layer.getXYUnits().toLowerCase().contains("not specified")) {
                            XYUnits = " " + layer.getXYUnits();
                        }

                        
                        if (layer.isVisible()) {
                            DimensionBox fe = layer.getFullExtent();

                            // does the layer even overlay with the mapExtent?
                            boolean flag = true;
                            if (fe.getTop() < mapExtent.getBottom()
                                    || fe.getRight() < mapExtent.getLeft()
                                    || fe.getBottom() > mapExtent.getTop()
                                    || fe.getLeft() > mapExtent.getRight()) {
                                flag = false;
                                
                            }
                            if (flag) {
                                
                                // get the colours and adjust for transparency.
                                int r1 = layer.getFillColour().getRed();
                                int g1 = layer.getFillColour().getGreen();
                                int b1 = layer.getFillColour().getBlue();
                                int a1 = layer.getAlpha();
                                Color fillColour = new Color(r1, g1, b1, a1);
                                r1 = layer.getLineColour().getRed();
                                g1 = layer.getLineColour().getGreen();
                                b1 = layer.getLineColour().getBlue();
                                Color lineColour = new Color(r1, g1, b1, a1);
                                
                                ShapeType shapeType = layer.getShapeType();
                                ShapeFileRecord[] records = layer.getData();
                                double x1, y1;
                                //int xInt, yInt, x2Int, y2Int;
                                double topCoord = mapExtent.getTop();
                                double bottomCoord = mapExtent.getBottom();
                                double leftCoord = mapExtent.getLeft();
                                double rightCoord = mapExtent.getRight();
                                double EWRange = rightCoord - leftCoord;
                                double NSRange = topCoord - bottomCoord;
                                
                                if (shapeType == ShapeType.POINT) {
                                    double[][] xyData = PointMarkers.getMarkerData(layer.getMarkerStyle(), layer.getMarkerSize());
                                    GeneralPath gp;
                                    BasicStroke myStroke = new BasicStroke(layer.getLineThickness());
                                    Stroke oldStroke = g2d.getStroke();
                                    g2d.setStroke(myStroke);
                                    Color[] colours = layer.getColourData();
                                        
                                    boolean isFilled = layer.isFilled();
                                    boolean isOutlined = layer.isOutlined();
                                    for (int r = 0; r < records.length; r++) {
                                        if (records[r].getShapeType() != ShapeType.NULLSHAPE) {
                                            whitebox.geospatialfiles.shapefile.Point rec = (whitebox.geospatialfiles.shapefile.Point) (records[r].getData());
                                            x1 = rec.getX();
                                            y1 = rec.getY();
                                            if (y1 < bottomCoord || x1 < leftCoord
                                                    || y1 > topCoord || x1 > rightCoord) {
                                                // It's not within the map area; do nothing.
                                            } else {
                                                x1 = (borderWidth + (x1 - leftCoord) / EWRange * myWidth);
                                                y1 = (borderWidth + (topCoord - y1) / NSRange * myHeight);
                                                gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 1);
                                                for (int a = 0; a < xyData.length; a++) {
                                                    if (xyData[a][0] == 0) { // moveTo
                                                        gp.moveTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                    } else if (xyData[a][0] == 1) { // lineTo
                                                        gp.lineTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                    } else if (xyData[a][0] == 2) { // elipse2D
                                                        Ellipse2D circle = new Ellipse2D.Double((x1 - xyData[a][1]), (y1 - xyData[a][1]), xyData[a][2], xyData[a][2]);

                                                        gp.append(circle, true);
                                                    }
                                                }
                                                //circle = new Ellipse2D.Double((x1 - halfMS), (y1 - halfMS), markerSize, markerSize);
                                                if (isFilled) {
                                                    g2d.setColor(colours[r]);
                                                    g2d.fill(gp); 
                                                }
                                                if (isOutlined) {
                                                    g2d.setColor(lineColour);
                                                    g2d.draw(gp); 
                                                }
                                                
                                            }
                                        }
                                    }
                                    g2d.setStroke(oldStroke);
                                } else if (shapeType == ShapeType.POINTZ) {
                                    double[][] xyData = PointMarkers.getMarkerData(layer.getMarkerStyle(), layer.getMarkerSize());
                                    GeneralPath gp;
                                    BasicStroke myStroke = new BasicStroke(layer.getLineThickness());
                                    Stroke oldStroke = g2d.getStroke();
                                    g2d.setStroke(myStroke);
                                    Color[] colours = layer.getColourData();
                                        
                                    boolean isFilled = layer.isFilled();
                                    boolean isOutlined = layer.isOutlined();
                                    for (int r = 0; r < records.length; r++) {
                                        if (records[r].getShapeType() != ShapeType.NULLSHAPE) {
                                            PointZ rec = (PointZ) (records[r].getData());
                                            x1 = rec.getX();
                                            y1 = rec.getY();
                                            if (y1 < bottomCoord || x1 < leftCoord
                                                    || y1 > topCoord || x1 > rightCoord) {
                                                // It's not within the map area; do nothing.
                                            } else {
                                                x1 = (borderWidth + (x1 - leftCoord) / EWRange * myWidth);
                                                y1 = (borderWidth + (topCoord - y1) / NSRange * myHeight);
                                                gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 1);
                                                for (int a = 0; a < xyData.length; a++) {
                                                    if (xyData[a][0] == 0) { // moveTo
                                                        gp.moveTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                    } else if (xyData[a][0] == 1) { // lineTo
                                                        gp.lineTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                    } else if (xyData[a][0] == 2) { // elipse2D
                                                        Ellipse2D circle = new Ellipse2D.Double((x1 - xyData[a][1]), (y1 - xyData[a][1]), xyData[a][2], xyData[a][2]);

                                                        gp.append(circle, true);
                                                    }
                                                }
                                                //circle = new Ellipse2D.Double((x1 - halfMS), (y1 - halfMS), markerSize, markerSize);
                                                if (isFilled) {
                                                    g2d.setColor(colours[r]);
                                                    g2d.fill(gp); 
                                                }
                                                if (isOutlined) {
                                                    g2d.setColor(lineColour);
                                                    g2d.draw(gp); 
                                                }
                                                
                                            }
                                        }
                                    }
                                    g2d.setStroke(oldStroke);
                                } else if (shapeType == ShapeType.POINTM) {
                                    double[][] xyData = PointMarkers.getMarkerData(layer.getMarkerStyle(), layer.getMarkerSize());
                                    GeneralPath gp;
                                    BasicStroke myStroke = new BasicStroke(layer.getLineThickness());
                                    Stroke oldStroke = g2d.getStroke();
                                    g2d.setStroke(myStroke);
                                    Color[] colours = layer.getColourData();
                                        
                                    boolean isFilled = layer.isFilled();
                                    boolean isOutlined = layer.isOutlined();
                                    for (int r = 0; r < records.length; r++) {
                                        if (records[r].getShapeType() != ShapeType.NULLSHAPE) {
                                            PointM rec = (PointM) (records[r].getData());
                                            x1 = rec.getX();
                                            y1 = rec.getY();
                                            if (y1 < bottomCoord || x1 < leftCoord
                                                    || y1 > topCoord || x1 > rightCoord) {
                                                // It's not within the map area; do nothing.
                                            } else {
                                                x1 = (borderWidth + (x1 - leftCoord) / EWRange * myWidth);
                                                y1 = (borderWidth + (topCoord - y1) / NSRange * myHeight);
                                                gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 1);
                                                for (int a = 0; a < xyData.length; a++) {
                                                    if (xyData[a][0] == 0) { // moveTo
                                                        gp.moveTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                    } else if (xyData[a][0] == 1) { // lineTo
                                                        gp.lineTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                    } else if (xyData[a][0] == 2) { // elipse2D
                                                        Ellipse2D circle = new Ellipse2D.Double((x1 - xyData[a][1]), (y1 - xyData[a][1]), xyData[a][2], xyData[a][2]);

                                                        gp.append(circle, true);
                                                    }
                                                }
                                                //circle = new Ellipse2D.Double((x1 - halfMS), (y1 - halfMS), markerSize, markerSize);
                                                if (isFilled) {
                                                    g2d.setColor(colours[r]);
                                                    g2d.fill(gp); 
                                                }
                                                if (isOutlined) {
                                                    g2d.setColor(lineColour);
                                                    g2d.draw(gp); 
                                                }
                                                
                                            }
                                        }
                                    }
                                    g2d.setStroke(oldStroke);
                                } else if (shapeType == ShapeType.MULTIPOINT) {
                                    double[][] xyData = PointMarkers.getMarkerData(layer.getMarkerStyle(), layer.getMarkerSize());
                                    GeneralPath gp;
                                    BasicStroke myStroke = new BasicStroke(layer.getLineThickness());
                                    Stroke oldStroke = g2d.getStroke();
                                    g2d.setStroke(myStroke);
                                    Color[] colours = layer.getColourData();
                                        
                                    double[][] recPoints;
                                    boolean isFilled = layer.isFilled();
                                    boolean isOutlined = layer.isOutlined();
                                    for (int r = 0; r < records.length; r++) {
                                        if (records[r].getShapeType() != ShapeType.NULLSHAPE) {
                                            MultiPoint rec = (MultiPoint) (records[r].getData());
                                            recPoints = rec.getPoints();
                                            for (int p = 0; p < recPoints.length; p++) {
                                                x1 = recPoints[p][0];
                                                y1 = recPoints[p][1];
                                                if (y1 < bottomCoord || x1 < leftCoord
                                                        || y1 > topCoord || x1 > rightCoord) {
                                                    // It's not within the map area; do nothing.
                                                } else {
                                                    x1 = (borderWidth + (x1 - leftCoord) / EWRange * myWidth);
                                                    y1 = (borderWidth + (topCoord - y1) / NSRange * myHeight);

                                                    gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 1);
                                                    for (int a = 0; a < xyData.length; a++) {
                                                        if (xyData[a][0] == 0) { // moveTo
                                                            gp.moveTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                        } else if (xyData[a][0] == 1) { // lineTo
                                                            gp.lineTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                        } else if (xyData[a][0] == 2) { // elipse2D
                                                            Ellipse2D circle = new Ellipse2D.Double((x1 - xyData[a][1]), (y1 - xyData[a][1]), xyData[a][2], xyData[a][2]);

                                                            gp.append(circle, true);
                                                        }
                                                    }
                                                    if (isFilled) {
                                                        g2d.setColor(colours[r]);
                                                        g2d.fill(gp);
                                                    }
                                                    if (isOutlined) {
                                                        g2d.setColor(lineColour);
                                                        g2d.draw(gp);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    g2d.setStroke(oldStroke);
                                } else if (shapeType == ShapeType.MULTIPOINTZ) {
                                    double[][] xyData = PointMarkers.getMarkerData(layer.getMarkerStyle(), layer.getMarkerSize());
                                    GeneralPath gp;
                                    BasicStroke myStroke = new BasicStroke(layer.getLineThickness());
                                    Stroke oldStroke = g2d.getStroke();
                                    g2d.setStroke(myStroke);
                                    Color[] colours = layer.getColourData();
                                        
                                    double[][] recPoints;
                                    boolean isFilled = layer.isFilled();
                                    boolean isOutlined = layer.isOutlined();
                                    for (int r = 0; r < records.length; r++) {
                                        if (records[r].getShapeType() != ShapeType.NULLSHAPE) {
                                            MultiPointZ rec = (MultiPointZ) (records[r].getData());
                                            recPoints = rec.getPoints();
                                            for (int p = 0; p < recPoints.length; p++) {
                                                x1 = recPoints[p][0];
                                                y1 = recPoints[p][1];
                                                if (y1 < bottomCoord || x1 < leftCoord
                                                        || y1 > topCoord || x1 > rightCoord) {
                                                    // It's not within the map area; do nothing.
                                                } else {
                                                    x1 = (borderWidth + (x1 - leftCoord) / EWRange * myWidth);
                                                    y1 = (borderWidth + (topCoord - y1) / NSRange * myHeight);

                                                    gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 1);
                                                    for (int a = 0; a < xyData.length; a++) {
                                                        if (xyData[a][0] == 0) { // moveTo
                                                            gp.moveTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                        } else if (xyData[a][0] == 1) { // lineTo
                                                            gp.lineTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                        } else if (xyData[a][0] == 2) { // elipse2D
                                                            Ellipse2D circle = new Ellipse2D.Double((x1 - xyData[a][1]), (y1 - xyData[a][1]), xyData[a][2], xyData[a][2]);

                                                            gp.append(circle, true);
                                                        }
                                                    }
                                                    if (isFilled) {
                                                        g2d.setColor(colours[r]);
                                                        g2d.fill(gp);
                                                    }
                                                    if (isOutlined) {
                                                        g2d.setColor(lineColour);
                                                        g2d.draw(gp);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    g2d.setStroke(oldStroke);
                                } else if (shapeType == ShapeType.MULTIPOINTM) {
                                    double[][] xyData = PointMarkers.getMarkerData(layer.getMarkerStyle(), layer.getMarkerSize());
                                    GeneralPath gp;
                                    BasicStroke myStroke = new BasicStroke(layer.getLineThickness());
                                    Stroke oldStroke = g2d.getStroke();
                                    g2d.setStroke(myStroke);
                                    Color[] colours = layer.getColourData();
                                        
                                    double[][] recPoints;
                                    boolean isFilled = layer.isFilled();
                                    boolean isOutlined = layer.isOutlined();
                                    for (int r = 0; r < records.length; r++) {
                                        if (records[r].getShapeType() != ShapeType.NULLSHAPE) {
                                            MultiPointM rec = (MultiPointM) (records[r].getData());
                                            recPoints = rec.getPoints();
                                            for (int p = 0; p < recPoints.length; p++) {
                                                x1 = recPoints[p][0];
                                                y1 = recPoints[p][1];
                                                if (y1 < bottomCoord || x1 < leftCoord
                                                        || y1 > topCoord || x1 > rightCoord) {
                                                    // It's not within the map area; do nothing.
                                                } else {
                                                    x1 = (borderWidth + (x1 - leftCoord) / EWRange * myWidth);
                                                    y1 = (borderWidth + (topCoord - y1) / NSRange * myHeight);

                                                    gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 1);
                                                    for (int a = 0; a < xyData.length; a++) {
                                                        if (xyData[a][0] == 0) { // moveTo
                                                            gp.moveTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                        } else if (xyData[a][0] == 1) { // lineTo
                                                            gp.lineTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                        } else if (xyData[a][0] == 2) { // elipse2D
                                                            Ellipse2D circle = new Ellipse2D.Double((x1 - xyData[a][1]), (y1 - xyData[a][1]), xyData[a][2], xyData[a][2]);

                                                            gp.append(circle, true);
                                                        }
                                                    }
                                                    if (isFilled) {
                                                        g2d.setColor(colours[r]);
                                                        g2d.fill(gp);
                                                    }
                                                    if (isOutlined) {
                                                        g2d.setColor(lineColour);
                                                        g2d.draw(gp);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    g2d.setStroke(oldStroke);
                                } else if (shapeType == ShapeType.POLYLINE) {
                                    //g2d.setColor(lineColour);
                                    int[] partStart;
                                    double[][] points;
                                    int pointSt;
                                    int pointEnd;
                                    float xPoints[];
                                    float yPoints[];
                                    GeneralPath polyline;
                                    BasicStroke myStroke = new BasicStroke(layer.getLineThickness());
                                    if (layer.isDashed()) {
                                        myStroke =
                                            new BasicStroke(layer.getLineThickness(),
                                            BasicStroke.CAP_BUTT,
                                            BasicStroke.JOIN_MITER,
                                            10.0f, layer.getDashArray(), 0.0f);
                                    }
                                    Stroke oldStroke = g2d.getStroke();
                                    g2d.setStroke(myStroke);
                                    Color[] colours = layer.getColourData();
                                    for (int r = 0; r < records.length; r++) {
                                        if (records[r].getShapeType() != ShapeType.NULLSHAPE) {
                                            PolyLine rec = (PolyLine) (records[r].getData());
                                            partStart = rec.getParts();
                                            points = rec.getPoints();
                                            for (int p = 0; p < rec.getNumParts(); p++) {
                                                pointSt = partStart[p];
                                                if (p < rec.getNumParts() - 1) {
                                                    pointEnd = partStart[p + 1];
                                                } else {
                                                    pointEnd = points.length;
                                                }
                                                xPoints = new float[pointEnd - pointSt];
                                                yPoints = new float[pointEnd - pointSt];
                                                for (int k = pointSt; k < pointEnd; k++) {
                                                    xPoints[k - pointSt] = (float)(borderWidth + (points[k][0] - leftCoord) / EWRange * myWidth);
                                                    yPoints[k - pointSt] = (float)(borderWidth + (topCoord - points[k][1]) / NSRange * myHeight);
                                                }
                                                polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xPoints.length);

                                                polyline.moveTo(xPoints[0], yPoints[0]);

                                                for (int index = 1; index < xPoints.length; index++) {
                                                    polyline.lineTo(xPoints[index], yPoints[index]);
                                                }
                                                g2d.setColor(colours[r]);
                                                g2d.draw(polyline);
                                            }
                                        }
                                    }
                                    g2d.setStroke(oldStroke);
                                } else if (shapeType == ShapeType.POLYLINEZ) {
                                    //g2d.setColor(lineColour);
                                    int[] partStart;
                                    double[][] points;
                                    int pointSt;
                                    int pointEnd;
                                    float xPoints[];
                                    float yPoints[];
                                    GeneralPath polyline;
                                    BasicStroke myStroke = new BasicStroke(layer.getLineThickness());
                                    if (layer.isDashed()) {
                                        myStroke =
                                            new BasicStroke(layer.getLineThickness(),
                                            BasicStroke.CAP_BUTT,
                                            BasicStroke.JOIN_MITER,
                                            10.0f, layer.getDashArray(), 0.0f);
                                    }
                                    Stroke oldStroke = g2d.getStroke();
                                    g2d.setStroke(myStroke);
                                    Color[] colours = layer.getColourData();
                                    for (int r = 0; r < records.length; r++) {
                                        if (records[r].getShapeType() != ShapeType.NULLSHAPE) {
                                            PolyLineZ rec = (PolyLineZ) (records[r].getData());
                                            partStart = rec.getParts();
                                            points = rec.getPoints();
                                            for (int p = 0; p < rec.getNumParts(); p++) {
                                                pointSt = partStart[p];
                                                if (p < rec.getNumParts() - 1) {
                                                    pointEnd = partStart[p + 1];
                                                } else {
                                                    pointEnd = points.length;
                                                }
                                                xPoints = new float[pointEnd - pointSt];
                                                yPoints = new float[pointEnd - pointSt];
                                                for (int k = pointSt; k < pointEnd; k++) {
                                                    xPoints[k - pointSt] = (float)(borderWidth + (points[k][0] - leftCoord) / EWRange * myWidth);
                                                    yPoints[k - pointSt] = (float)(borderWidth + (topCoord - points[k][1]) / NSRange * myHeight);
                                                }
                                                polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xPoints.length);

                                                polyline.moveTo(xPoints[0], yPoints[0]);

                                                for (int index = 1; index < xPoints.length; index++) {
                                                    polyline.lineTo(xPoints[index], yPoints[index]);
                                                }
                                                g2d.setColor(colours[r]);
                                                g2d.draw(polyline);
                                            }
                                        }
                                    }
                                    g2d.setStroke(oldStroke);
                                } else if (shapeType == ShapeType.POLYLINEM) {
                                    //g2d.setColor(lineColour);
                                    int[] partStart;
                                    double[][] points;
                                    int pointSt;
                                    int pointEnd;
                                    float xPoints[];
                                    float yPoints[];
                                    GeneralPath polyline;
                                    BasicStroke myStroke = new BasicStroke(layer.getLineThickness());
                                    if (layer.isDashed()) {
                                        myStroke =
                                            new BasicStroke(layer.getLineThickness(),
                                            BasicStroke.CAP_BUTT,
                                            BasicStroke.JOIN_MITER,
                                            10.0f, layer.getDashArray(), 0.0f);
                                    }
                                    Stroke oldStroke = g2d.getStroke();
                                    g2d.setStroke(myStroke);
                                    
                                    Color[] colours = layer.getColourData();
                                    
                                    for (int r = 0; r < records.length; r++) {
                                        if (records[r].getShapeType() != ShapeType.NULLSHAPE) {
                                            PolyLineM rec = (PolyLineM) (records[r].getData());
                                            partStart = rec.getParts();
                                            points = rec.getPoints();
                                            for (int p = 0; p < rec.getNumParts(); p++) {
                                                pointSt = partStart[p];
                                                if (p < rec.getNumParts() - 1) {
                                                    pointEnd = partStart[p + 1];
                                                } else {
                                                    pointEnd = points.length;
                                                }
                                                xPoints = new float[pointEnd - pointSt];
                                                yPoints = new float[pointEnd - pointSt];
                                                for (int k = pointSt; k < pointEnd; k++) {
                                                    xPoints[k - pointSt] = (float)(borderWidth + (points[k][0] - leftCoord) / EWRange * myWidth);
                                                    yPoints[k - pointSt] = (float)(borderWidth + (topCoord - points[k][1]) / NSRange * myHeight);
                                                }
                                                polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xPoints.length);

                                                polyline.moveTo(xPoints[0], yPoints[0]);

                                                for (int index = 1; index < xPoints.length; index++) {
                                                    polyline.lineTo(xPoints[index], yPoints[index]);
                                                }
                                                g2d.setColor(colours[r]);
                                                g2d.draw(polyline);
                                            }
                                        }
                                    }
                                    g2d.setStroke(oldStroke);
                                } else if (shapeType == ShapeType.POLYGON) {
                                    int[] partStart;
                                    double[][] points;
                                    int pointSt;
                                    int pointEnd;
                                    float xPoints[];
                                    float yPoints[];
                                    GeneralPath polyline;
                                    
                                    if (layer.isFilled()) {
                                        Color[] colours = layer.getColourData();
                                        for (int r = 0; r < records.length; r++) {
                                            if (records[r].getShapeType() != ShapeType.NULLSHAPE) {
                                                whitebox.geospatialfiles.shapefile.Polygon rec = (whitebox.geospatialfiles.shapefile.Polygon) (records[r].getData());
                                                partStart = rec.getParts();
                                                points = rec.getPoints();
                                                polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, points.length);
                                                for (int p = 0; p < rec.getNumParts(); p++) {
                                                    pointSt = partStart[p];
                                                    if (p < rec.getNumParts() - 1) {
                                                        pointEnd = partStart[p + 1];
                                                    } else {
                                                        pointEnd = points.length;
                                                    }
                                                    xPoints = new float[pointEnd - pointSt];
                                                    yPoints = new float[pointEnd - pointSt];
                                                    for (int k = pointSt; k < pointEnd; k++) {
                                                        xPoints[k - pointSt] = (float) (borderWidth + (points[k][0] - leftCoord) / EWRange * myWidth);
                                                        yPoints[k - pointSt] = (float) (borderWidth + (topCoord - points[k][1]) / NSRange * myHeight);
                                                    }
                                                    polyline.moveTo(xPoints[0], yPoints[0]);

                                                    for (int index = 1; index < xPoints.length; index++) {
                                                        polyline.lineTo(xPoints[index], yPoints[index]);
                                                    }
                                                    polyline.closePath();
                                                }
                                                g2d.setColor(colours[r]);
                                                g2d.fill(polyline);
                                            }
                                        }
                                    }

                                    if (layer.isOutlined()) {
                                        g2d.setColor(lineColour);
                                        BasicStroke myStroke = new BasicStroke(layer.getLineThickness());
                                        if (layer.isDashed()) {
                                            myStroke =
                                                    new BasicStroke(layer.getLineThickness(),
                                                    BasicStroke.CAP_BUTT,
                                                    BasicStroke.JOIN_MITER,
                                                    10.0f, layer.getDashArray(), 0.0f);
                                        }
                                        Stroke oldStroke = g2d.getStroke();
                                        g2d.setStroke(myStroke);
                                        for (int r = 0; r < records.length; r++) {
                                            if (records[r].getShapeType() != ShapeType.NULLSHAPE) {
                                                whitebox.geospatialfiles.shapefile.Polygon rec = (whitebox.geospatialfiles.shapefile.Polygon) (records[r].getData());
                                                partStart = rec.getParts();
                                                points = rec.getPoints();
                                                for (int p = 0; p < rec.getNumParts(); p++) {
                                                    pointSt = partStart[p];
                                                    if (p < rec.getNumParts() - 1) {
                                                        pointEnd = partStart[p + 1];
                                                    } else {
                                                        pointEnd = points.length;
                                                    }
                                                    xPoints = new float[pointEnd - pointSt];
                                                    yPoints = new float[pointEnd - pointSt];
                                                    for (int k = pointSt; k < pointEnd; k++) {
                                                        xPoints[k - pointSt] = (float) (borderWidth + (points[k][0] - leftCoord) / EWRange * myWidth);
                                                        yPoints[k - pointSt] = (float) (borderWidth + (topCoord - points[k][1]) / NSRange * myHeight);
                                                    }
                                                    polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xPoints.length);
                                                    polyline.moveTo(xPoints[0], yPoints[0]);

                                                    for (int index = 1; index < xPoints.length; index++) {
                                                        polyline.lineTo(xPoints[index], yPoints[index]);
                                                    }
                                                    g2d.draw(polyline);
                                                }
                                            }
                                        }
                                        g2d.setStroke(oldStroke);
                                    }
                                } else if (shapeType == ShapeType.POLYGONZ) {
                                    int[] partStart;
                                    double[][] points;
                                    int pointSt;
                                    int pointEnd;
                                    float xPoints[];
                                    float yPoints[];
                                    GeneralPath polyline;
                                    
                                    if (layer.isFilled()) {
                                        Color[] colours = layer.getColourData();
                                        for (int r = 0; r < records.length; r++) {
                                            if (records[r].getShapeType() != ShapeType.NULLSHAPE) {
                                                PolygonZ rec = (PolygonZ) (records[r].getData());
                                                partStart = rec.getParts();
                                                points = rec.getPoints();
                                                polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, points.length);
                                                for (int p = 0; p < rec.getNumParts(); p++) {
                                                    pointSt = partStart[p];
                                                    if (p < rec.getNumParts() - 1) {
                                                        pointEnd = partStart[p + 1];
                                                    } else {
                                                        pointEnd = points.length;
                                                    }
                                                    xPoints = new float[pointEnd - pointSt];
                                                    yPoints = new float[pointEnd - pointSt];
                                                    for (int k = pointSt; k < pointEnd; k++) {
                                                        xPoints[k - pointSt] = (float) (borderWidth + (points[k][0] - leftCoord) / EWRange * myWidth);
                                                        yPoints[k - pointSt] = (float) (borderWidth + (topCoord - points[k][1]) / NSRange * myHeight);
                                                    }
                                                    polyline.moveTo(xPoints[0], yPoints[0]);

                                                    for (int index = 1; index < xPoints.length; index++) {
                                                        polyline.lineTo(xPoints[index], yPoints[index]);
                                                    }
                                                    polyline.closePath();
                                                }
                                                g2d.setColor(colours[r]);
                                                g2d.fill(polyline);
                                            }
                                        }
                                    }

                                    if (layer.isOutlined()) {
                                        g2d.setColor(lineColour);
                                        BasicStroke myStroke = new BasicStroke(layer.getLineThickness());
                                        if (layer.isDashed()) {
                                            myStroke =
                                                    new BasicStroke(layer.getLineThickness(),
                                                    BasicStroke.CAP_BUTT,
                                                    BasicStroke.JOIN_MITER,
                                                    10.0f, layer.getDashArray(), 0.0f);
                                        }
                                        Stroke oldStroke = g2d.getStroke();
                                        g2d.setStroke(myStroke);
                                        for (int r = 0; r < records.length; r++) {
                                            if (records[r].getShapeType() != ShapeType.NULLSHAPE) {
                                                PolygonZ rec = (PolygonZ) (records[r].getData());
                                                partStart = rec.getParts();
                                                points = rec.getPoints();
                                                for (int p = 0; p < rec.getNumParts(); p++) {
                                                    pointSt = partStart[p];
                                                    if (p < rec.getNumParts() - 1) {
                                                        pointEnd = partStart[p + 1];
                                                    } else {
                                                        pointEnd = points.length;
                                                    }
                                                    xPoints = new float[pointEnd - pointSt];
                                                    yPoints = new float[pointEnd - pointSt];
                                                    for (int k = pointSt; k < pointEnd; k++) {
                                                        xPoints[k - pointSt] = (float) (borderWidth + (points[k][0] - leftCoord) / EWRange * myWidth);
                                                        yPoints[k - pointSt] = (float) (borderWidth + (topCoord - points[k][1]) / NSRange * myHeight);
                                                    }
                                                    polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xPoints.length);
                                                    polyline.moveTo(xPoints[0], yPoints[0]);

                                                    for (int index = 1; index < xPoints.length; index++) {
                                                        polyline.lineTo(xPoints[index], yPoints[index]);
                                                    }
                                                    g2d.draw(polyline);
                                                }
                                            }
                                        }
                                        g2d.setStroke(oldStroke);
                                    }
                                } else if (shapeType == ShapeType.POLYGONM) {
                                    int[] partStart;
                                    double[][] points;
                                    int pointSt;
                                    int pointEnd;
                                    float xPoints[];
                                    float yPoints[];
                                    GeneralPath polyline;
                                    
                                    if (layer.isFilled()) {
                                        Color[] colours = layer.getColourData();
                                        for (int r = 0; r < records.length; r++) {
                                            if (records[r].getShapeType() != ShapeType.NULLSHAPE) {
                                                PolygonM rec = (PolygonM) (records[r].getData());
                                                partStart = rec.getParts();
                                                points = rec.getPoints();
                                                polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, points.length);
                                                for (int p = 0; p < rec.getNumParts(); p++) {
                                                    pointSt = partStart[p];
                                                    if (p < rec.getNumParts() - 1) {
                                                        pointEnd = partStart[p + 1];
                                                    } else {
                                                        pointEnd = points.length;
                                                    }
                                                    xPoints = new float[pointEnd - pointSt];
                                                    yPoints = new float[pointEnd - pointSt];
                                                    for (int k = pointSt; k < pointEnd; k++) {
                                                        xPoints[k - pointSt] = (float) (borderWidth + (points[k][0] - leftCoord) / EWRange * myWidth);
                                                        yPoints[k - pointSt] = (float) (borderWidth + (topCoord - points[k][1]) / NSRange * myHeight);
                                                    }
                                                    polyline.moveTo(xPoints[0], yPoints[0]);

                                                    for (int index = 1; index < xPoints.length; index++) {
                                                        polyline.lineTo(xPoints[index], yPoints[index]);
                                                    }
                                                    polyline.closePath();
                                                }
                                                g2d.setColor(colours[r]);
                                                g2d.fill(polyline);
                                            }
                                        }
                                    }

                                    if (layer.isOutlined()) {
                                        g2d.setColor(lineColour);
                                        BasicStroke myStroke = new BasicStroke(layer.getLineThickness());
                                        if (layer.isDashed()) {
                                            myStroke =
                                                    new BasicStroke(layer.getLineThickness(),
                                                    BasicStroke.CAP_BUTT,
                                                    BasicStroke.JOIN_MITER,
                                                    10.0f, layer.getDashArray(), 0.0f);
                                        }
                                        Stroke oldStroke = g2d.getStroke();
                                        g2d.setStroke(myStroke);
                                        for (int r = 0; r < records.length; r++) {
                                            if (records[r].getShapeType() != ShapeType.NULLSHAPE) {
                                                PolygonM rec = (PolygonM) (records[r].getData());
                                                partStart = rec.getParts();
                                                points = rec.getPoints();
                                                for (int p = 0; p < rec.getNumParts(); p++) {
                                                    pointSt = partStart[p];
                                                    if (p < rec.getNumParts() - 1) {
                                                        pointEnd = partStart[p + 1];
                                                    } else {
                                                        pointEnd = points.length;
                                                    }
                                                    xPoints = new float[pointEnd - pointSt];
                                                    yPoints = new float[pointEnd - pointSt];
                                                    for (int k = pointSt; k < pointEnd; k++) {
                                                        xPoints[k - pointSt] = (float) (borderWidth + (points[k][0] - leftCoord) / EWRange * myWidth);
                                                        yPoints[k - pointSt] = (float) (borderWidth + (topCoord - points[k][1]) / NSRange * myHeight);
                                                    }
                                                    polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xPoints.length);
                                                    polyline.moveTo(xPoints[0], yPoints[0]);

                                                    for (int index = 1; index < xPoints.length; index++) {
                                                        polyline.lineTo(xPoints[index], yPoints[index]);
                                                    }
                                                    g2d.draw(polyline);
                                                }
                                            }
                                        }
                                        g2d.setStroke(oldStroke);
                                    }
                                } else if (shapeType == ShapeType.MULTIPATCH) {
                                    // unsupported
                                }
                            }
                        }
                    }
                }

                int innerBorderWidth = borderWidth - 4;
                int neatLineWidth = borderWidth - 2;

                g2d.setColor(Color.white);
                g2d.fillRect(0, 0, getWidth(), borderWidth);
                g2d.fillRect(0, 0, borderWidth, getHeight());
                g2d.fillRect(0, getHeight() - borderWidth, getWidth(), getHeight());
                g2d.fillRect(getWidth() - borderWidth, 0, getWidth(), getHeight());

                g2d.setColor(Color.black);

                // draw the neat line
                g2d.drawRect(borderWidth - neatLineWidth, borderWidth - neatLineWidth,
                        (int) (myWidth + 2 * neatLineWidth), (int) (myHeight + 2 * neatLineWidth));

                // draw the corner boxes
                int leftEdge = borderWidth;
                int topEdge = borderWidth;
                int rightEdge = borderWidth + (int)myWidth;
                int bottomEdge = borderWidth + (int)myHeight;


                // draw the innermost line
                g2d.drawRect(borderWidth, borderWidth, (int) (myWidth), (int) (myHeight));

                // draw the graticule line
                g2d.drawRect(borderWidth - innerBorderWidth, borderWidth - innerBorderWidth,
                        (int) (myWidth + 2 * innerBorderWidth), (int) (myHeight + 2 * innerBorderWidth));


                g2d.drawRect(leftEdge - innerBorderWidth, topEdge - innerBorderWidth, innerBorderWidth, innerBorderWidth);
                g2d.drawRect(leftEdge - innerBorderWidth, bottomEdge, innerBorderWidth, innerBorderWidth);
                g2d.drawRect(rightEdge, topEdge - innerBorderWidth, innerBorderWidth, innerBorderWidth);
                g2d.drawRect(rightEdge, bottomEdge, innerBorderWidth, innerBorderWidth);


                // labels
                DecimalFormat df = new DecimalFormat("###,###,###.#");
                Font font = new Font("SanSerif", Font.PLAIN, 11);
                g2d.setFont(font);
                FontMetrics metrics = g.getFontMetrics(font);
                int hgt, adv;

                double x2 = currentExtent.getLeft() - (left - borderWidth) / scale;
                String label = df.format(x2) + XYUnits;
                g2d.drawString(label, leftEdge + 3, topEdge - 4);
                g2d.drawString(label, leftEdge + 3, bottomEdge + 13);

                label = df.format(currentExtent.getRight()) + XYUnits;
                hgt = metrics.getHeight();
                adv = metrics.stringWidth(label);
                Dimension size = new Dimension(adv + 2, hgt + 2);

                g2d.drawString(label, rightEdge - size.width, topEdge - 4);
                g2d.drawString(label, rightEdge - size.width, bottomEdge + 13);

                // rotate the font
                Font oldFont = g.getFont();
                Font f = oldFont.deriveFont(AffineTransform.getRotateInstance(-Math.PI / 2.0));
                g2d.setFont(f);

                double y2 = currentExtent.getTop() + (top - borderWidth) / scale;

                label = df.format(y2) + XYUnits;
                hgt = metrics.getHeight();
                adv = metrics.stringWidth(label);
                size = new Dimension(adv + 2, hgt + 2);

                g2d.drawString(label, leftEdge - 3, topEdge + size.width);
                g2d.drawString(label, rightEdge + 11, topEdge + size.width);

                y2 = currentExtent.getBottom() - (top - borderWidth) / scale;
                label = df.format(y2) + XYUnits;
                hgt = metrics.getHeight();
                adv = metrics.stringWidth(label);
                size = new Dimension(adv + 2, hgt + 2);

                g2d.drawString(label, leftEdge - 3, bottomEdge - 2);
                g2d.drawString(label, rightEdge + 11, bottomEdge - 2);
               
                // replace the rotated font.
                g2d.setFont(oldFont);

                if (mouseDragged && myMode == MOUSE_MODE_ZOOM && !usingDistanceTool) {
                    g2d.setColor(Color.black);
                    int boxWidth = (int) (Math.abs(startCol - endCol));
                    int boxHeight = (int) (Math.abs(startRow - endRow));
                    x = Math.min(startCol, endCol);
                    y = Math.min(startRow, endRow);
                    g2d.drawRect(x, y, boxWidth, boxHeight);
                    g2d.setColor(Color.white);
                    boxWidth += 2;
                    boxHeight += 2;
                    g2d.drawRect(x - 1, y - 1, boxWidth, boxHeight);

                } else if (mouseDragged && myMode == MOUSE_MODE_PAN && !usingDistanceTool) {
                    g2d.setColor(Color.white);
                    g2d.drawLine(startCol, startRow, endCol, endRow);
                } else if (mouseDragged && usingDistanceTool) {
                    int radius = 3;
                    g2d.setColor(Color.white);
                    g2d.drawOval(startCol - radius - 1, startRow - radius - 1, 2 * radius + 2, 2 * radius + 2);
                    g2d.setColor(Color.black);
                    g2d.drawOval(startCol - radius, startRow - radius, 2 * radius, 2 * radius);
                    g2d.setColor(Color.white);
                    g2d.drawLine(startCol, startRow, endCol, endRow);
                    g2d.setColor(Color.black);
                    g2d.drawLine(startCol - 1, startRow - 1, endCol - 1, endRow - 1);
                    DecimalFormat df2 = new DecimalFormat("###,###,###.##");
                    
                    double dist = Math.sqrt((endCol - startCol) * (endCol - startCol) + (endRow - startRow) * (endRow - startRow)) / scale;
                    status.setMessage("Distance: " + df2.format(dist) + XYUnits);
                }
                
                if (modifyingPixels) {
                    if (modifyPixelsX > 0 && modifyPixelsY > 0) {
                        int crosshairlength = 13;
                        int radius = 9;
                        g2d.setColor(Color.white);
                        g2d.drawOval(modifyPixelsX - radius - 1, modifyPixelsY - radius - 1, 2 * radius + 2, 2 * radius + 2);
                        g2d.setColor(Color.black);
                        g2d.drawOval(modifyPixelsX - radius, modifyPixelsY - radius, 2 * radius, 2 * radius);
                        
                        
                        g2d.setColor(Color.white);
                        g2d.drawRect(modifyPixelsX - 1, modifyPixelsY - crosshairlength - 1, 2, crosshairlength * 2 + 2);
                        g2d.drawRect(modifyPixelsX - crosshairlength - 1, modifyPixelsY - 1, crosshairlength * 2 + 2, 2);
                        g2d.setColor(Color.black);
                        g2d.drawLine(modifyPixelsX, modifyPixelsY - crosshairlength, modifyPixelsX, modifyPixelsY + crosshairlength);
                        g2d.drawLine(modifyPixelsX - crosshairlength, modifyPixelsY, modifyPixelsX + crosshairlength, modifyPixelsY);
                        
                    }

                }
                
                /*if (cursorX > leftEdge && cursorX < rightEdge
                        && cursorY > topEdge && cursorY < bottomEdge) {
                    g2d.setColor(Color.RED);
                    g2d.drawLine(cursorX, topEdge - 2, cursorX, topEdge - innerBorderWidth + 2);
                    g2d.drawLine(leftEdge - 2, cursorY, leftEdge - innerBorderWidth + 2, cursorY);
                    g2d.drawLine(cursorX, bottomEdge + 2, cursorX, bottomEdge + innerBorderWidth - 2);
                    g2d.drawLine(rightEdge + 2, cursorY, rightEdge + innerBorderWidth - 2, cursorY);
                }*/
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }
    
    /** Override the ImageObserver imageUpdate method and monitor
    * the loading of the image. Set a flag when it is loaded.
   **/
    @Override
  public boolean imageUpdate (Image img, int info_flags,
                             int x, int y, int w, int h) {
    if (info_flags != ALLBITS) {
        // Indicates image has not finished loading
        // Returning true will tell the image loading
        // thread to keep drawing until image fully
        // drawn loaded.
        this.repaint();
        return true;
    } else {
        
        return false;
    }
  } // imageUpdate

    
    @Override
    public int print(Graphics g, PageFormat pf, int page)
            throws PrinterException {
        if (page > 0) {
            return NO_SUCH_PAGE;
        }

        int i = pf.getOrientation();
        
        // get the size of the page
        double pageWidth = pf.getImageableWidth();
        double pageHeight = pf.getImageableHeight();
        double myWidth = this.getWidth();// - borderWidth * 2;
        double myHeight = this.getHeight();// - borderWidth * 2;
        double scaleX = pageWidth / myWidth;
        double scaleY = pageHeight / myHeight;
        double minScale = Math.min(scaleX, scaleY);
                
        Graphics2D g2d = (Graphics2D) g;
        g2d.translate(pf.getImageableX(), pf.getImageableY());
        g2d.scale(minScale, minScale);
        
        drawMapDataView(g);

        return PAGE_EXISTS;
    }
    
    public boolean saveToImage(String fileName) {
        try {
            int width = (int)this.getWidth();
            int height =(int)this.getHeight();
            // TYPE_INT_ARGB specifies the image format: 8-bit RGBA packed
            // into integer pixels
            BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            Graphics ig = bi.createGraphics();
            drawMapDataView(ig);
            int i = fileName.lastIndexOf(".");
            String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toUpperCase();
            if (!ImageIO.write(bi, extension, new File(fileName))) {
                return false;
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    //int cursorY;
    //int cursorX;
    @Override
    public void mouseMoved(MouseEvent e) {
        //cursorY = e.getY();
        //cursorX = e.getX();
        //this.repaint();
        updateStatus(e);
    }

    boolean mouseDragged = false;
    @Override
    public void mouseDragged(MouseEvent e) {
        mouseDragged = true;
        if (myMode == MOUSE_MODE_ZOOM || usingDistanceTool) {
            endRow = e.getY();
            endCol = e.getX();
            this.repaint();
        }
    }

    double startX;
    double startY;
    double endX;
    double endY;
    int startCol;
    int startRow;
    int endCol;
    int endRow;

    @Override
    public void mousePressed(MouseEvent e) {
        if (status != null && mapExtent.getBottom() != mapExtent.getTop()) {
            double myWidth = this.getWidth() - borderWidth * 2;
            double myHeight = this.getHeight() - borderWidth * 2;
            startRow = e.getY();
            startCol = e.getX();
            startY = mapExtent.getTop() - (startRow - borderWidth) / myHeight * (mapExtent.getTop() - mapExtent.getBottom());
            startX = mapExtent.getLeft() + (startCol - borderWidth) / myWidth * (mapExtent.getRight() - mapExtent.getLeft());
            
            if (myMode == MOUSE_MODE_PAN) { this.setCursor(panClosedHandCursor); }
        }
        //int clickCount = e.getClickCount();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (status != null && mapExtent.getBottom() != mapExtent.getTop()) {
            //int clickCount = e.getClickCount();
            double myWidth = this.getWidth() - borderWidth * 2;
            double myHeight = this.getHeight() - borderWidth * 2;
            endY = mapExtent.getTop() - (e.getY() - borderWidth) / myHeight * (mapExtent.getTop() - mapExtent.getBottom());
            endX = mapExtent.getLeft() + (e.getX() - borderWidth) / myWidth * (mapExtent.getRight() - mapExtent.getLeft());

            if (mouseDragged && myMode == MOUSE_MODE_ZOOM && !usingDistanceTool) {
                // move the current extent such that it is centered on the point
                DimensionBox db = mapinfo.getCurrentExtent();
                db.setTop(Math.max(startY, endY));
                db.setBottom(Math.min(startY, endY));
                db.setLeft(Math.min(startX, endX));
                db.setRight(Math.max(startX, endX));
                mapinfo.setCurrentExtent(db);
                modifyPixelsX = -1;
                modifyPixelsY = -1;
                host.refreshMap(false);
            }  else if (mouseDragged && myMode == MOUSE_MODE_PAN && !usingDistanceTool) {
                // move the current extent such that it is centered on the point
                DimensionBox db = mapinfo.getCurrentExtent();
                double deltaY = startY - endY;
                double deltaX = startX - endX;
                double z = db.getTop();
                db.setTop(z + deltaY);
                z = db.getBottom();
                db.setBottom(z + deltaY);
                z = db.getLeft();
                db.setLeft(z + deltaX);
                z = db.getRight();
                db.setRight(z + deltaX);
                mapinfo.setCurrentExtent(db);
                modifyPixelsX = -1;
                modifyPixelsY = -1;
                host.refreshMap(false);
            } else if (usingDistanceTool) {
                host.refreshMap(false);
            }

            if (myMode == MOUSE_MODE_PAN) { this.setCursor(panCursor); }
            mouseDragged = false;
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (status != null) {
            status.setMessage("Ready");
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        int clickCount = e.getClickCount();
        if (clickCount == 1 && modifyingPixels) {
            modifyPixelsX = e.getX();
            modifyPixelsY = e.getY();
            double myWidth = this.getWidth() - borderWidth * 2;
            double myHeight = this.getHeight() - borderWidth * 2;
            double y = mapExtent.getTop() - (modifyPixelsY - borderWidth) / myHeight * (mapExtent.getTop() - mapExtent.getBottom());
            double x = mapExtent.getLeft() + (modifyPixelsX - borderWidth) / myWidth * (mapExtent.getRight() - mapExtent.getLeft());
            GridCell point = mapinfo.getRowAndColumn(x, y);
            if (point.row >= 0) {
                host.refreshMap(false);
                RasterLayerInfo rli = (RasterLayerInfo)(mapinfo.getLayer(point.layerNum));
                String fileName = new File(rli.getHeaderFile()).getName();
                ModifyPixel mp = new ModifyPixel((Frame)findWindow(this), true, point, fileName);
                if (mp.wasSuccessful()) {
                    point = mp.getValue();
                    rli.setDataValue(point.row, point.col, point.z);
                    rli.update();
                    host.refreshMap(false);
                    //mapinfo.setRowAndColumn(mp.getValue());
                }
            } else {
                modifyPixelsX = -1;
                modifyPixelsY = -1;
                host.refreshMap(false);
            }
        } else if (clickCount == 1) {
            // move the current extent such that it is centered on the point
            DimensionBox db = mapinfo.getCurrentExtent();
            double halfYRange = Math.abs(db.getTop() - db.getBottom()) / 2;
            double halfXRange = Math.abs(db.getRight() - db.getLeft()) / 2;
            db.setTop(startY + halfYRange);
            db.setBottom(startY - halfYRange);
            db.setLeft(startX - halfXRange);
            db.setRight(startX + halfXRange);
            mapinfo.setCurrentExtent(db);
            if (e.getButton() == 1) {
                mapinfo.zoomIn();
                host.refreshMap(false);
            } else if (e.getButton() == 3) {
                mapinfo.zoomOut();
                host.refreshMap(false);
            }
        } else if ((clickCount == 2) && (e.getButton() == 3)) {
            mapinfo.setCurrentExtent(mapinfo.getFullExtent());
            host.refreshMap(false);
        }
    }

    private void updateStatus(MouseEvent e) {
        if (status != null && mapExtent.getBottom() != mapExtent.getTop()) {
            double myWidth = this.getWidth() - borderWidth * 2;
            double myHeight = this.getHeight() - borderWidth * 2;
            double y = mapExtent.getTop() - (e.getY() - borderWidth) / myHeight * (mapExtent.getTop() - mapExtent.getBottom());
            double x = mapExtent.getLeft() + (e.getX() - borderWidth) / myWidth * (mapExtent.getRight() - mapExtent.getLeft());
            DecimalFormat df = new DecimalFormat("###,###,###.0");
            String xStr = df.format(x);
            String yStr = df.format(y);
            GridCell point = mapinfo.getRowAndColumn(x, y);
            if (point.row >= 0) {
                //double noDataValue = point.noDataValue;
                DecimalFormat dfZ = new DecimalFormat("###,###,###.####");
                String zStr;
                if (!point.isValueNoData() && !Double.isNaN(point.z)) {
                    zStr = dfZ.format(point.z);
                } else if (Double.isNaN(point.z)) {
                    zStr = "Not Available";
                } else {
                    zStr = "NoData";
                }
                if (!point.isRGB || point.isValueNoData()) {
                    status.setMessage("E: " + xStr + "  N: " + yStr + 
                            "  Row: " + (int)(point.row) + "  Col: " + 
                            (int)(point.col) + "  Z: " + zStr);
                } else {
                    String r = String.valueOf((int)point.z & 0xFF);
                    String g = String.valueOf(((int)point.z >> 8) & 0xFF);
                    String b = String.valueOf(((int)point.z >> 16) & 0xFF);
                    String a = String.valueOf(((int)point.z >> 24) & 0xFF);
                    if (a.equals("255")) {
                        status.setMessage("E: " + xStr + "  N: " + yStr
                                + "  Row: " + (int) (point.row) + "  Col: "
                                + (int) (point.col) + "  R: " + r + "  G: " + g
                                + "  B: " + b);
                    } else {
                        status.setMessage("E: " + xStr + "  N: " + yStr
                                + "  Row: " + (int) (point.row) + "  Col: "
                                + (int) (point.col) + "  R: " + r + "  G: " + g
                                + "  B: " + b + "  A: " + a);
                    } 
                }
            } else {
                status.setMessage("E: " + xStr + "  N: " + yStr);
            }
        }
    }
    
    private static Window findWindow(Component c) {
        if (c == null) {
            return JOptionPane.getRootFrame();
        } else if (c instanceof Window) {
            return (Window) c;
        } else {
            return findWindow(c.getParent());
        }
    }
    
    class ModifyPixel extends JDialog implements ActionListener {
        GridCell point = null;
        JTextField tf = null;
        JTextField tfR = null;
        JTextField tfG = null;
        JTextField tfB = null;
        JTextField tfA = null;
        
        private ModifyPixel(Frame owner, boolean modal, GridCell point, String fileName) {
            super(owner, modal);
            this.setTitle(fileName);
            this.point = point;
            
            createGui();
        }
        
        private void createGui() {
            if (System.getProperty("os.name").contains("Mac")) {
                this.getRootPane().putClientProperty("apple.awt.brushMetalLook", Boolean.TRUE);
            }
            
            
            JPanel mainPane = new JPanel();
            mainPane.setLayout(new BoxLayout(mainPane, BoxLayout.Y_AXIS));
            mainPane.setBorder(BorderFactory.createEmptyBorder(10, 15, 0, 15));
            
            JPanel rowAndColPane = new JPanel();
            rowAndColPane.setLayout(new BoxLayout(rowAndColPane, BoxLayout.X_AXIS));
            rowAndColPane.add(new JLabel("Row: " + point.row));
            rowAndColPane.add(Box.createHorizontalStrut(15));
            rowAndColPane.add(new JLabel("Column: " + point.col));
            rowAndColPane.add(Box.createHorizontalGlue());
            mainPane.add(rowAndColPane);
            mainPane.add(Box.createVerticalStrut(5));
     
            tf = new JTextField(15);
            tf.setHorizontalAlignment(JTextField.RIGHT);
            if (!point.isValueNoData()) {
                tf.setText(String.valueOf(point.z));
            } else {
                tf.setText("NoData");
            }
            tf.setMaximumSize(new Dimension(35, 22));

            if (!point.isRGB) {
                JPanel valPane = new JPanel();
                valPane.setLayout(new BoxLayout(valPane, BoxLayout.X_AXIS));
                valPane.add(new JLabel("Value: "));
                valPane.add(tf);
                valPane.add(Box.createHorizontalGlue());
                mainPane.add(valPane);
                mainPane.add(Box.createVerticalStrut(5));
                
            } else {
                JPanel valPane = new JPanel();
                valPane.setLayout(new BoxLayout(valPane, BoxLayout.X_AXIS));
                valPane.add(new JLabel("Value: "));
                valPane.add(tf);
                valPane.add(Box.createHorizontalGlue());
                mainPane.add(valPane);
                mainPane.add(Box.createVerticalStrut(5));
                
                String r = "";
                String g = "";
                String b = "";
                String a = "";
                
                if (!point.isValueNoData()) {
                    r = String.valueOf((int)point.z & 0xFF);
                    g = String.valueOf(((int)point.z >> 8) & 0xFF);
                    b = String.valueOf(((int)point.z >> 16) & 0xFF);
                    a = String.valueOf(((int)point.z >> 24) & 0xFF);
                }
                
                tfR = new JTextField(5);
                tfG = new JTextField(5);
                tfB = new JTextField(5);
                tfA = new JTextField(5);
                
                tfR.setHorizontalAlignment(JTextField.RIGHT);
                tfG.setHorizontalAlignment(JTextField.RIGHT);
                tfB.setHorizontalAlignment(JTextField.RIGHT);
                tfA.setHorizontalAlignment(JTextField.RIGHT);
             
                tfR.setText(r);
                tfG.setText(g);
                tfB.setText(b);
                tfA.setText(a);
                
                JPanel rgbPane = new JPanel();
                
                rgbPane.setLayout(new BoxLayout(rgbPane, BoxLayout.X_AXIS));
                rgbPane.add(new JLabel("R: "));
                rgbPane.add(tfR);
                rgbPane.add(Box.createHorizontalGlue());
                
                rgbPane.setLayout(new BoxLayout(rgbPane, BoxLayout.X_AXIS));
                rgbPane.add(new JLabel(" G: "));
                rgbPane.add(tfG);
                rgbPane.add(Box.createHorizontalGlue());
                
                rgbPane.setLayout(new BoxLayout(rgbPane, BoxLayout.X_AXIS));
                rgbPane.add(new JLabel(" B: "));
                rgbPane.add(tfB);
                rgbPane.add(Box.createHorizontalGlue());
                
                rgbPane.setLayout(new BoxLayout(rgbPane, BoxLayout.X_AXIS));
                rgbPane.add(new JLabel(" a: "));
                rgbPane.add(tfA);
                rgbPane.add(Box.createHorizontalGlue());
                
                mainPane.add(rgbPane);
                mainPane.add(Box.createVerticalStrut(5));
                
            }

            // buttons
            JButton ok = new JButton("OK");
            ok.addActionListener(this);
            ok.setActionCommand("ok");
            JButton cancel = new JButton("Cancel");
            cancel.addActionListener(this);
            cancel.setActionCommand("cancel");
            
            JPanel buttonPane = new JPanel();
            buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.LINE_AXIS));
            buttonPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
            buttonPane.add(Box.createHorizontalGlue());
            buttonPane.add(ok);
            buttonPane.add(Box.createHorizontalStrut(5));
            buttonPane.add(cancel);
            buttonPane.add(Box.createHorizontalGlue());
            
            Container contentPane = getContentPane();
            contentPane.add(mainPane, BorderLayout.CENTER);
            contentPane.add(buttonPane, BorderLayout.PAGE_END);

            pack();
            
            this.setVisible(true);
        }
        
        private void confirmValue() {
            try {
                if (!point.isRGB) {
                    if (tf.getText().toLowerCase().contains("nodata")) {
                        point.z = point.noDataValue;
                    } else {
                        double z = Double.parseDouble(tf.getText());
                        point.z = z;
                    }
                    successful = true;
                } else {
                    if (tf.getText().toLowerCase().contains("nodata")) {
                        point.z = point.noDataValue;
                    } else {
                        int r = Integer.parseInt(tfR.getText());
                        int g = Integer.parseInt(tfG.getText());
                        int b = Integer.parseInt(tfB.getText());
                        int a = Integer.parseInt(tfA.getText());
                        double z = (double) ((a << 24) | (b << 16) | (g << 8) | r);
                        point.z = z;
                    }
                    successful = true;
                }
            } catch (Exception e) {
                System.out.println(e);
                successful = false;
            }
        }
        
        boolean successful = false;
        private boolean wasSuccessful() {
            return successful;
        }
        
        private GridCell getValue() {
            return point;
        }
        
        @Override
        public void actionPerformed(ActionEvent e) {
            Object source = e.getSource();
            String actionCommand = e.getActionCommand();
            if (actionCommand.equals("ok")) {
                confirmValue();
                this.dispose();
            } else if (actionCommand.equals("cancel")) {
                this.dispose();
            }
        }
    }
}