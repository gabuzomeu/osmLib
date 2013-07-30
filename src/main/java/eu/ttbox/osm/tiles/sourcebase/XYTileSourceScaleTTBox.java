package eu.ttbox.osm.tiles.sourcebase;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;

import org.osmdroid.tileprovider.ExpirableBitmapDrawable;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class XYTileSourceScaleTTBox extends XYTileSourceTTBox {

    private static final Logger logger = LoggerFactory.getLogger(XYTileSourceScaleTTBox.class);

    private final String originalPathBase;
    private final int scaleFactor;
    private final int scaleTileSizePixels;


    public XYTileSourceScaleTTBox(int aScaleFactor, String aName, String displayName, OnlineTileSourceBase otherTiles, String... aBaseUrl) {
        this(otherTiles.pathBase(), aScaleFactor, aName, displayName, otherTiles.getMinimumZoomLevel(), otherTiles.getMaximumZoomLevel(), otherTiles.getTileSizePixels(), otherTiles.imageFilenameEnding(), aBaseUrl);
    }

    public XYTileSourceScaleTTBox(String originalPathBase, int aScaleFactor, String aName, String displayName, int aZoomMinLevel, int aZoomMaxLevel, int aTileSizePixels, String aImageFilenameEnding, String... aBaseUrl) {
        super(aName, displayName, aZoomMinLevel, aZoomMaxLevel, aTileSizePixels, aImageFilenameEnding, aBaseUrl);
        this.scaleFactor = aScaleFactor;
        this.scaleTileSizePixels = aTileSizePixels * scaleFactor;
        this.originalPathBase = originalPathBase;

    }

    @Override
    public int getTileSizePixels() {
        return scaleTileSizePixels;
    }

    public String pathBase() {
        return originalPathBase;
    }

    @Override
    public Drawable getDrawable(final String aFilePath) {
        try {
            final Bitmap originalBitmap = BitmapFactory.decodeFile(aFilePath);
            Drawable scaleTile = null;
            if (originalBitmap != null) {
                Bitmap scaleBitmapTile = Bitmap.createScaledBitmap(originalBitmap, scaleTileSizePixels, scaleTileSizePixels, false);
                return new ExpirableBitmapDrawable(scaleBitmapTile);
            } else {
                // if we couldn't load it then it's invalid - delete it
                try {
                    new File(aFilePath).delete();
                } catch (final Throwable e) {
                    logger.error("Error deleting invalid file: " + aFilePath, e);
                }
            }
        } catch (final OutOfMemoryError e) {
            logger.error("OutOfMemoryError loading bitmap: " + aFilePath);
            System.gc();
        }
        return null;
    }

}
