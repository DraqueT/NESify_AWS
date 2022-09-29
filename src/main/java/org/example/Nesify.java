package org.example;


import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

/**
 *
 * @author draque
 */
public class Nesify {
    // RES = 256 × 240 pixels (ignore height)
    // Tiles 16x16
    // 25 color master palette:
    public int MASTER_PALETTE_SIZE = 25;
    public int TILE_PALETTE_SIZE = 3;
    public int X_RES = 256;
    public int TILE_SIZE = 16;
    private final static double MAX_PIXELS = 20000000; // a 1000 X 2000 image is the max size, for example
    private Pixel[][] fullImage;
    private Pixel[][] finalImage;
    private Pixel[] masterPalette;
    private double pixelRatio = 0;
    private int pixelRatioCeiling = 0;

    public void go(byte[] rawImage) throws Exception {
    	if (MASTER_PALETTE_SIZE < 2 || MASTER_PALETTE_SIZE > 1023) {
    		throw new Exception("Master palette size must be greater than 1 and less than 1024.");
    	}
    	
    	if (TILE_PALETTE_SIZE < 2 || TILE_PALETTE_SIZE > 255) {
    		throw new Exception("Tile palette size must be greater than 1 and less than 256.");
    	}
    	
    	if (X_RES < 1 || X_RES > 511) {
    		throw new Exception("X resolution size must be greater than 0 and less than 512.");
    	}
    	
    	if (TILE_SIZE < 1 || TILE_SIZE > 511) {
    		throw new Exception("Tile size must be greater than 0 and less than 512.");
    	}
    	
    	masterPalette = new Pixel[MASTER_PALETTE_SIZE];
        processImage(rawImage);
    }
    
    private void processImage(byte[] rawImage) throws Exception {
        BufferedImage image;

        try {           
            image = ImageIO.read(new ByteArrayInputStream(rawImage));
            
            if (image == null) {
            	throw new Exception("Unable to read image format.");
            }
            
            if (image.getWidth() * image.getHeight() > MAX_PIXELS) {
            	throw new Exception("Image exceeds maximum pixel count. Max is 20000000, or a 10000 x 2000 image.");
            }
        } catch (IOException e) {
            System.out.println("FAILURE");
            e.printStackTrace();
            return;
        }

        pixelRatio = (double)image.getWidth() / X_RES;
        pixelRatioCeiling = ((int)Math.ceil(pixelRatio));
        populateFullImageGrid(image);
        populateMasterPalette();
        translateImage();
    }

    private void populateFullImageGrid(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        fullImage = new Pixel[width][height];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                fullImage[x][y] = new Pixel((pixel & 0x00ff0000) >> 16, (pixel & 0x0000ff00) >> 8, pixel & 0x000000ff);
            }
        }
    }
    
    private void populateMasterPalette() {
        List<Pixel> pixels = new ArrayList<Pixel>();
        
        for (int y = 0; y < fullImage[0].length; y++) {
            for (int x = 0; x < fullImage.length; x++) {
                // skip black (always present in palette)
                Pixel pixel = fullImage[x][y];
                
                if (!(pixel.r == 255 && pixel.g == 255 && pixel.b == 255)
                        && !(pixel.r == 0 && pixel.g == 0 && pixel.b == 0)) {
                    pixels.add(pixel);
                }
            }
        }
        
        // sort by luminosity
        Collections.sort(pixels);
        
        int pixelsPerRegion = (int) Math.ceil((double)pixels.size() / MASTER_PALETTE_SIZE);
        
        // first values are always black & white
        masterPalette[0] = new Pixel(255, 255, 255);
        masterPalette[1] = new Pixel(0, 0, 0);
        for (int region = 2; region < MASTER_PALETTE_SIZE; region++) {
            masterPalette[region] = getMasterPixelForRange(pixels, region * pixelsPerRegion, (region + 1) * pixelsPerRegion);
        }
    }
    
    /**
     * Translates full image to NESified image
     */
    private void translateImage() {
        int height = getFinalImageHeight();
        finalImage = new Pixel[X_RES][height];
        
        // initialize to all white
        Pixel background = new Pixel(255, 255, 255);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < X_RES; x++) {
            	finalImage[x][y] = background;
            }
        }
        
        int xTiles = (int)Math.ceil(X_RES/TILE_SIZE);
        int yTiles = (int)Math.ceil(((double)fullImage[0].length / pixelRatio)/TILE_SIZE);
        int nativeTileSize = (int)Math.ceil((double)fullImage.length / xTiles); // native size of tile within original image
        
        for (int x = 0; x < xTiles; x++) {
            for (int y = 0; y < yTiles; y++) {
                Pixel[] localPalette = getLocalPalette(x * nativeTileSize, y * nativeTileSize, nativeTileSize, TILE_PALETTE_SIZE, masterPalette);
                
                Pixel[][] tile = translateTile(x * nativeTileSize, y * nativeTileSize, nativeTileSize, localPalette);
                
                writeTileToFinalImage(x, y, tile);
            }
        }
    }
    
    private void writeTileToFinalImage(int xTile, int yTile, Pixel[][] tile) {  
        for (int x = 0; x < TILE_SIZE; x++) {
            if ((xTile * TILE_SIZE) + x >= finalImage.length) {
                break;
            }
            
            for (int y = 0; y < TILE_SIZE; y++) {
                if ((yTile * TILE_SIZE) + y >= finalImage[0].length) {
                    break;
                }
                
                if (tile[x][y] != null) {
                	finalImage[(xTile * TILE_SIZE) + x][(yTile * TILE_SIZE) + y] = tile[x][y];
                }
            }
        }
    }
    
    // translates original tile to final tile
    private Pixel [][] translateTile(int xLoc, int yLoc, int nativeTileSize, Pixel[] palette) {
        Pixel[][] translatedTile = new Pixel[TILE_SIZE][TILE_SIZE];
        
        for (int xPixel = 0; xPixel < TILE_SIZE; xPixel++){
            for (int yPixel = 0; yPixel< TILE_SIZE; yPixel++) {
                int xStart = (int)Math.floor(xPixel * pixelRatio) + xLoc;
                int yStart = (int)Math.floor(yPixel * pixelRatio) + yLoc;
                
                translatedTile[xPixel][yPixel] = getLocalPalette(xStart, yStart, pixelRatioCeiling, 1, palette)[0];
            }
        }
        
        return translatedTile;
    }
    
    /**
     * Calculates height of final image based on width of X_RES
     * @param initialHeight
     * @return 
     */
    private int getFinalImageHeight() {
        return (X_RES * fullImage[0].length) / fullImage.length;
    }
    
    public Pixel[][] getFinalImage() { 
        return finalImage;
    }
    
    /**
     * Fetches a palette of defined size by analyzing a square of size tileSize
     * with its top left corner at position X Y of the full image. Returned
     * palette is based on an average of which colors are best matched by
     * pixels within it
     * @param _x
     * @param _y
     * @param tileSize
     * @return 
     */
    private Pixel[] getLocalPalette(int _x, int _y, int tileSize, int palletSize, Pixel[] parentPalette) {
        Pixel[] localPalette = new Pixel[palletSize];
        int[] masterPaletteScore = new int[parentPalette.length];
        
        for (int i = 0; i < parentPalette.length; i++) {
            masterPaletteScore[i] = 0;
        }
        
        for (int y = _y; y < (_y + tileSize) && y < fullImage[0].length; y++) {
            for (int x = _x; x < (_x + tileSize) && x < fullImage.length; x++) {
                masterPaletteScore[getNearestMasterPaletteMatchIndex(fullImage[x][y], parentPalette)]++;
            }
        }
        
        // select highest matches and return local palette for image segment
        for (int i = 0; i < palletSize; i++) {
        	int winner = 0;
        	int curHigh = 0;
            for (int j = 0; j < parentPalette.length; j++) {
                if (masterPaletteScore[j] > curHigh) {
                    winner = j;
                    curHigh = masterPaletteScore[j];
                }
            }
            
            localPalette[i] = parentPalette[winner];
            masterPaletteScore[winner] = 0;
        }
        
        return localPalette;
    }
    
    /**
     * Returns index of palette color most closely matching pixel
     * @param p
     * @return 
     */
    private int getNearestMasterPaletteMatchIndex(Pixel p, Pixel[] palette) {
    	int match = 0;
    	int curHigh = 0;
        
        for (int i = 0; i < palette.length; i++) {
        	int totalMatch = getColorSimilarity(p, palette[i]);
            
            if (totalMatch > curHigh) {
                curHigh = totalMatch;
                match = i;
            }
        }
        
        return match;
    }
    
    /**
     * Calculates the similarity of two 8 bit RGB pixels
     * Higher = more similar
     * @return 
     */
    private int getColorSimilarity(Pixel a, Pixel b) {
        final int RANGE = 255;
        
        return (RANGE - Math.abs(a.r - b.r)) 
                + (RANGE - Math.abs(a.g - b.g))
                + (RANGE - Math.abs(a.b - b.b));
    }

    private Pixel getMasterPixelForRange(List<Pixel> pixels, int start, int end) {
        long r = 0;
        long g = 0;
        long b = 0;
        int diff = end - start;
        
        for (int i = start; i < end && i < pixels.size(); i++) {
            Pixel pixel = pixels.get(i);
            
            r += pixel.r;
            g += pixel.g;
            b += pixel.b;
        }
        
        return new Pixel((int)r/diff, (int)g/diff, (int)b/diff);
    }
    
    public Pixel[] getMasterPalette() {
        return masterPalette;
    }
}
