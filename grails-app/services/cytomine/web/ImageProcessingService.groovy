package cytomine.web

import be.cytomine.processing.image.filters.Auto_Threshold
import com.vividsolutions.jts.geom.Coordinate
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.geom.LineString
import com.vividsolutions.jts.geom.MultiPolygon
import grails.util.Holders
import ij.ImagePlus
import ij.process.ImageConverter
import ij.process.ImageProcessor
import ij.process.PolygonFiller
import utils.ProcUtils

import javax.imageio.ImageIO
import java.awt.*
import java.awt.geom.Path2D
import java.awt.image.BufferedImage

class ImageProcessingService {

    static transactional = false

    public BufferedImage dynBinary(String url, BufferedImage bufferedImage, String method) {
        ImagePlus ip = new ImagePlus(url, bufferedImage)
        ImageConverter ic = new ImageConverter(ip)
        ic.convertToGray8()
        def at = new Auto_Threshold()
        Object[] result = at.exec(ip, method, false, false, true, false, false, false)
        ImagePlus ipThresholded = (ImagePlus) result[1]
        return ipThresholded.getBufferedImage()
    }


    public BufferedImage resizeImage(BufferedImage image, int width, int height) {
        int type = image.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : image.getType();
        BufferedImage resizedImage = new BufferedImage(width, height,type);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return resizedImage;
    }

    //deprecated
    public BufferedImage rotate90ToRight( BufferedImage inputImage ){
        int width = inputImage.getWidth();
        int height = inputImage.getHeight();
        BufferedImage returnImage = new BufferedImage( height, width , inputImage.getType()  );

        for( int x = 0; x < width; x++ ) {
            for( int y = 0; y < height; y++ ) {
                returnImage.setRGB( height - y - 1, x, inputImage.getRGB( x, y  )  );
            }
        }
        return returnImage;
    }

    public BufferedImage applyMaskToAlpha(BufferedImage image, BufferedImage mask) {
        //TODO:: document this method
        int width = image.getWidth()
        int height = image.getHeight()
        int[] imagePixels = image.getRGB(0, 0, width, height, null, 0, width)
        int[] maskPixels = mask.getRGB(0, 0, width, height, null, 0, width)
        int black_rgb = Color.BLACK.getRGB()
        for (int i = 0; i < imagePixels.length; i++)
        {
            int color = imagePixels[i] & 0x00FFFFFF; // mask away any alpha present
            int alphaValue = (maskPixels[i] == black_rgb) ? 0x00 : 0xFF
            int maskColor = alphaValue << 24 // shift value into alpha bits
            imagePixels[i] = color | maskColor
        }
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        combined.setRGB(0, 0, width, height, imagePixels, 0, width)
        return combined
    }

    public BufferedImage colorizeWindow(def params, BufferedImage window, Collection<Geometry> geometryCollection, int x, int y, double x_ratio, double y_ratio) {

        for (geometry in geometryCollection) {

            if (geometry instanceof MultiPolygon) {
                MultiPolygon multiPolygon = (MultiPolygon) geometry;
                for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                    window = colorizeWindow(params, window, multiPolygon.getGeometryN(i), x, y, x_ratio, y_ratio)
                }
            } else {
                window = colorizeWindow(params, window, geometry, x, y, x_ratio, y_ratio)
            }
        }

        return window
    }

    public BufferedImage colorizeWindow(def params, BufferedImage window,  Geometry geometry, int x, int y, double x_ratio, double y_ratio) {

        if (geometry instanceof com.vividsolutions.jts.geom.Polygon) {
            com.vividsolutions.jts.geom.Polygon polygon = (com.vividsolutions.jts.geom.Polygon) geometry;
            window = colorizeWindow(params, window, polygon, x, y, x_ratio, y_ratio)
        }

        return window
    }

    public BufferedImage colorizeWindow(def params, BufferedImage window, com.vividsolutions.jts.geom.Polygon polygon, int x, int y, double x_ratio, double y_ratio) {

        window = colorizeWindow(params, window, polygon.getExteriorRing(), Color.WHITE, x, y, x_ratio, y_ratio)
        for (def j = 0; j < polygon.getNumInteriorRing(); j++) {
            window = colorizeWindow(params, window, polygon.getInteriorRingN(j), Color.BLACK, x, y, x_ratio, y_ratio)
        }

        return window
    }

    public BufferedImage colorizeWindow(def params, BufferedImage window, LineString lineString, Color color, int x, int y, double x_ratio, double y_ratio) {
        int imageHeight = params.int('imageHeight')
        ImagePlus imagePlus = new ImagePlus("", window)
        ImageProcessor ip = imagePlus.getProcessor()
        ip.setColor(color)
        //int[] pixels = (int[]) ip.getPixels()

        Collection<Coordinate> coordinates = lineString.getCoordinates()
        int[] _x = new int[coordinates.size()]
        int[] _y = new int[coordinates.size()]
        coordinates.eachWithIndex { coordinate, i ->
            if(i%100==0) {
                //println "*** $i/${coordinates.size()} coordinates"
            }
            int xLocal = Math.min((coordinate.x - x) * x_ratio, window.getWidth());
            xLocal = Math.max(0, xLocal)
            int yLocal = Math.min((imageHeight - coordinate.y - y) * y_ratio, window.getHeight());
            yLocal = Math.max(0, yLocal)
            _x[i] = xLocal
            _y[i] = yLocal
        }
        PolygonFiller polygonFiller = new PolygonFiller()
        polygonFiller.setPolygon(_x, _y, coordinates.size())
        polygonFiller.fill(ip, new Rectangle(window.getWidth(), window.getHeight()))

        //ip.setPixels(pixels)
        ip.getBufferedImage()
    }

    public BufferedImage drawPolygons(def params, BufferedImage bufferedImage, Collection<Geometry> geometryCollection, Color c, int borderWidth,int x, int y, double x_ratio, double y_ratio) {
        for (geometry in geometryCollection) {

            if (geometry instanceof MultiPolygon) {
                MultiPolygon multiPolygon = (MultiPolygon) geometry;
                for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                    bufferedImage = drawPolygon(params, bufferedImage, multiPolygon.getGeometryN(i),c,borderWidth, x, y, x_ratio, y_ratio)
                }
            } else {
                bufferedImage = drawPolygon(params, bufferedImage, geometry,c,borderWidth, x, y, x_ratio, y_ratio)
            }
        }

        return bufferedImage
    }

    public BufferedImage scaleImage(BufferedImage img, Integer width, Integer height) {
        int imgWidth = img.getWidth();
        int imgHeight = img.getHeight();
        if (imgWidth*height < imgHeight*width) {
            width = imgWidth*height/imgHeight;
        } else {
            height = imgHeight*width/imgWidth;
        }
        BufferedImage newImage = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = newImage.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.clearRect(0, 0, width, height);
            g.drawImage(img, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return newImage;
    }

    public BufferedImage drawPolygon(def params, BufferedImage window,  Geometry geometry, Color c, int borderWidth,int x, int y, double x_ratio, double y_ratio) {
        if (geometry instanceof com.vividsolutions.jts.geom.Polygon) {
            com.vividsolutions.jts.geom.Polygon polygon = (com.vividsolutions.jts.geom.Polygon) geometry;
            window = drawPolygon(params, window, polygon,c,borderWidth, x, y, x_ratio, y_ratio)
        }

        return window
    }

    public BufferedImage drawPolygon(def params, BufferedImage window, com.vividsolutions.jts.geom.Polygon polygon, Color c, int borderWidth,int x, int y, double x_ratio, double y_ratio) {
        window = drawPolygon(params, window, polygon.getExteriorRing(), c,borderWidth, x, y, x_ratio, y_ratio)
        for (def j = 0; j < polygon.getNumInteriorRing(); j++) {
            window = drawPolygon(params, window, polygon.getInteriorRingN(j), c,borderWidth, x, y, x_ratio, y_ratio)
        }

        return window
    }

    public BufferedImage drawPolygon(def params, BufferedImage window, LineString lineString, Color c, int borderWidth, int x, int y, double x_ratio, double y_ratio) {

        int imageHeight = params.int('imageHeight')
        Path2D.Float regionOfInterest = new Path2D.Float();
        boolean isFirst = true;

        Coordinate[] coordinates = lineString.getCoordinates();

        for(Coordinate coordinate:coordinates) {
            double xLocal = Math.min((coordinate.x - x) * x_ratio, window.getWidth());
            xLocal = Math.max(0, xLocal)
            double yLocal = Math.min((imageHeight - coordinate.y - y) * y_ratio, window.getHeight());
            yLocal = Math.max(0, yLocal)

            if(isFirst) {
                regionOfInterest.moveTo(xLocal,yLocal);
                isFirst = false;
            }
            regionOfInterest.lineTo(xLocal,yLocal);
        }
        Graphics2D g2d = (Graphics2D)window.getGraphics();
        //g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setStroke(new BasicStroke(borderWidth));
        g2d.setColor(c);

        g2d.draw(regionOfInterest);
        window

    }


    public BufferedImage createMask(BufferedImage bufferedImage, Geometry geometry, def params, boolean withAlpha) {
        int topLeftX = params.int('topLeftX')
        int topLeftY = params.int('topLeftY')
        int width = params.int('width')
        int height = params.int('height')
        int imageWidth = params.int('imageWidth')
        int imageHeight = params.int('imageHeight')
        BufferedImage mask = new BufferedImage(bufferedImage.getWidth(),bufferedImage.getHeight(),BufferedImage.TYPE_INT_ARGB);
        double x_ratio = bufferedImage.getWidth() / width
        double y_ratio = bufferedImage.getHeight() / height

        mask = colorizeWindow(params, mask, [geometry], topLeftX, imageHeight - topLeftY, x_ratio, y_ratio)

        if (withAlpha)
            return applyMaskToAlpha(bufferedImage, mask)
        else
            return mask

    }
    public BufferedImage createCropWithDraw(BufferedImage bufferedImage, Geometry geometry, def params) {
        //AbstractImage image, BufferedImage window, LineString lineString, Color color, int x, int y, double x_ratio, double y_ratio
        int topLeftX = params.int('topLeftX')
        int topLeftY = params.int('topLeftY')
        int width = params.int('width')
        int height = params.int('height')
        int imageWidth = params.int('imageWidth')
        int imageHeight = params.int('imageHeight')
        double x_ratio = bufferedImage.getWidth() / width
        double y_ratio = bufferedImage.getHeight() / height
        //int borderWidth = ((double)annotation.getArea()/(100000000d/50d))
        int borderWidth = ((double)width/(15000/250d))*x_ratio

        //AbstractImage image, BufferedImage window, Collection<Geometry> geometryCollection, Color c, int borderWidth,int x, int y, double x_ratio, double y_ratio
        bufferedImage = drawPolygons(
                params,
                bufferedImage,
                [geometry],
                Color.BLACK,
                borderWidth,
                topLeftX,
                imageHeight - topLeftY,
                x_ratio,
                y_ratio
        )
        bufferedImage
    }



}
