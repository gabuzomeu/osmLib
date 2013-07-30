package eu.ttbox.osm.tiles.sourcebase;


import org.osmdroid.ResourceProxy;
import org.osmdroid.tileprovider.tilesource.XYTileSource;

public class XYTileSourceTTBox extends XYTileSource {

   private  String displayName;

    public XYTileSourceTTBox(String aName, String displayName, int aZoomMinLevel, int aZoomMaxLevel, int aTileSizePixels, String aImageFilenameEnding, String... aBaseUrl) {
        super(aName,  ResourceProxy.string.unknown, aZoomMinLevel, aZoomMaxLevel, aTileSizePixels, aImageFilenameEnding, aBaseUrl);
        this.displayName = displayName;
    }


    @Override
    public String localizedName(final ResourceProxy proxy) {
        return displayName;
    }
}
