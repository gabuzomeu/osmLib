package eu.ttbox.osm.tiles.sourcebase;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.Log;

import org.osmdroid.ResourceProxy;
import org.osmdroid.tileprovider.ExpirableBitmapDrawable;
import org.osmdroid.tileprovider.MapTile;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class XYTileSourceScaleTTBox extends XYTileSourceTTBox {


    private static final Logger logger = LoggerFactory.getLogger(XYTileSourceScaleTTBox.class);

    private static final String TAG = "XYTileSourceScaleTTBox";


    private final String originalPathBase;
    private final float scaleFactor;
    private final int scaleTileSizePixels;

    private final boolean isScale;

    public XYTileSourceScaleTTBox(float aScaleFactor, String aName,   XYTileSourceTTBox otherTiles ) {
        this(otherTiles.pathBase(), aScaleFactor, aName,  otherTiles.getDisplayName(), otherTiles.getMinimumZoomLevel(), otherTiles.getMaximumZoomLevel(), otherTiles.getTileSizePixels(), otherTiles.imageFilenameEnding(), otherTiles.getBaseUrls() );
    }


    public XYTileSourceScaleTTBox(float aScaleFactor, String aName, String displayName, OnlineTileSourceBase otherTiles, String... aBaseUrl) {
        this(otherTiles.pathBase(), aScaleFactor, aName,  displayName, otherTiles.getMinimumZoomLevel(), otherTiles.getMaximumZoomLevel(), otherTiles.getTileSizePixels(), otherTiles.imageFilenameEnding(), aBaseUrl);
    }

    public XYTileSourceScaleTTBox(String originalPathBase, float aScaleFactor, String aName, String displayName, int aZoomMinLevel, int aZoomMaxLevel, int aTileSizePixels, String aImageFilenameEnding, String... aBaseUrl) {
        super(aName, displayName, aZoomMinLevel, aZoomMaxLevel, aTileSizePixels, aImageFilenameEnding, aBaseUrl);
        this.scaleFactor = aScaleFactor;
        this.isScale = aScaleFactor!= 1;
        this.scaleTileSizePixels = Math.round( aTileSizePixels * scaleFactor);
        this.originalPathBase = originalPathBase;
    }

    @Override
    public int getTileSizePixels() {
        return scaleTileSizePixels;
    }

    @Override
    public String pathBase() {
        return originalPathBase;
    }

    @Override
    public String localizedName(final ResourceProxy proxy) {
        String displayName = super.localizedName(proxy);
        if (isScale) {
            displayName  = displayName + "(x" + scaleFactor + ")";
        }
        return displayName;
    }

    @Override
    public String getTileRelativeFilenameString(final MapTile tile) {
        String relativePath = super.getTileRelativeFilenameString(tile);
        Log.d(TAG, "relativePath : " + relativePath);
        return relativePath;
    }

    @Override
    public Drawable getDrawable(final String aFilePath) {
        Log.d(TAG, "getDrawable : " + aFilePath);
        try {
            final Bitmap originalBitmap = BitmapFactory.decodeFile(aFilePath);
            Drawable scaleTile = null;
            if (  originalBitmap != null) {
                Bitmap scaleBitmapTile =originalBitmap;
                if (isScale) {
                    scaleBitmapTile = Bitmap.createScaledBitmap(originalBitmap, scaleTileSizePixels, scaleTileSizePixels, true);
                }
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
