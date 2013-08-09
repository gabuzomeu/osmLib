package eu.ttbox.osm.tiles;

import android.content.Context;

import org.osmdroid.ResourceProxy;
import org.osmdroid.tileprovider.tilesource.CloudmadeTileSource;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.tileprovider.tilesource.bing.BingMapTileSource;
import org.osmdroid.tileprovider.util.CloudmadeUtil;

import java.util.ArrayList;

import eu.ttbox.osm.tiles.sourcebase.XYTileSourceScaleTTBox;
import eu.ttbox.osm.tiles.sourcebase.XYTileSourceTTBox;
import eu.ttbox.osm.tiles.svg.CloudmadeTileSourceVector;

/**
 * @see http://code.google.com/p/osmdroid/issues/detail?id=135
 * <p/>
 * Own tile @see
 * http://stackoverflow.com/questions/8136775/how-can-i-implement
 * -offline-maps-using-osmdroid-by-saving-map-tiles-images-into DB Tile
 * Provider @see DatabaseFileArchive
 * @see http://wiki.openstreetmap.org/wiki/SVG
 * @see http://wiki.openstreetmap.org/wiki/Osmarender/SVG
 * <p/>
 * Tiles Chages @see http://wiki.openstreetmap.org/wiki/API_v0.5#
 * Getting_list_of_changed_tiles Disk Usage @see
 * http://wiki.openstreetmap.org/wiki/Tile_Disk_Usage
 * <p/>
 * Somes Tiles @see http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
 * <a href="http://wiki.openstreetmap.org/wiki/Tiles">List of Avaialable TIles</a>
 */
public class MyAppTilesProviders {

    public static final OnlineTileSourceBase MAPQUESTOSM = new XYTileSource( //
            "MapquestOSM", ResourceProxy.string.mapquest_osm, 0, 18, 256, ".png", //
            "http://otile1.mqcdn.com/tiles/1.0.0/osm/", //
            "http://otile2.mqcdn.com/tiles/1.0.0/osm/", //
            "http://otile3.mqcdn.com/tiles/1.0.0/osm/", //
            "http://otile4.mqcdn.com/tiles/1.0.0/osm/");

    public static final OnlineTileSourceBase PISTEMAP = new XYTileSource( //
            "OpenPisteMap", ResourceProxy.string.cyclemap, 0, 17, 256, ".png", //
            "http://tiles2.openpistemap.org/landshaded/");
    /**
     * {link
     * http://developers.cloudmade.com/wiki/vector-stream-server/Documentation}
     */
    public static final OnlineTileSourceBase CLOUDMADE_VECTOR_TILES = new CloudmadeTileSourceVector( //
            "CloudMadeVectorTiles", ResourceProxy.string.cloudmade_standard, 0, 21, 256, ".svgz", // svg
            "http://alpha.vectors.cloudmade.com/%s/%d/%d/%d/%d/%d%s?token=%s" //
    );

    public static final OnlineTileSourceBase SKECHBOY_TILES = new XYTileSourceTTBox( //
            "Skechboy", "Skechboy SPDY", 0, 17, 256, ".png", //
            "https://skechboy.com/maps/"  //
    );
    public static final OnlineTileSourceBase STAMEN_WATERCOLOR_TILES = new XYTileSourceTTBox( //
            "StamenWaterColor", "Stamen Watercolor", 0, 17, 256, ".jpg", //
            "http://a.tile.stamen.com/watercolor/"  //
    );

    /*public static final OnlineTileSourceBase STAMEN_BLACK_AND_WHITE_TILES =  new XYTileSourceTTBox( //
            "StamenBlackAndWhite", "Stamen Black&White",  0, 17, 256, ".png", //
            "http://toolserver.org/~cmarqu/hill/"  //
    );

    public static final OnlineTileSourceBase STAMEN_HILL_SHADING_TILES =  new XYTileSourceTTBox( //
            "StamenHillShading", "Stamen HillShading",  0, 17, 256, ".png", //
            "http://toolserver.org/~cmarqu/hill/"  //
    );*/
    public static final OnlineTileSourceBase MAPNIK_BLACK_AND_WHITE_TILES = new XYTileSourceTTBox( //
            "MapnikBlackAndWhite", "MapNik Black&White", 0, 17, 256, ".png", //
            "http://a.www.toolserver.org/tiles/bw-mapnik/"  //
    );
    public static final OnlineTileSourceBase MAPNIK_HIKEBIKE_TILES = new XYTileSourceTTBox( //
            "MapnikHikeBike", "MapNik Hike Bike", 0, 17, 256, ".png", //
            "http://toolserver.org/tiles/hikebike/"  //
    );
    public static final OnlineTileSourceBase CLOUDMADE_SSL_TILES = new CloudmadeTileSource(
            "CloudMadeSslTiles", ResourceProxy.string.cloudmade_standard, 0, 18, 256, ".png",
            "https://ssl_tiles.cloudmade.com/%s/%d/%d/%d/%d/%d%s?token=%s");

    public static final OnlineTileSourceBase MAPNIK_SCALE_2 = new XYTileSourceScaleTTBox(
             2, "MapnikScale2", "Mapnik (Zoom x2)", TileSourceFactory.MAPNIK , "http://tile.openstreetmap.org/" );

    public static void initTilesSource(Context context) {
        // Remove Tiles
        ArrayList<ITileSource> tileSources = TileSourceFactory.getTileSources();
        tileSources.remove(TileSourceFactory.PUBLIC_TRANSPORT);
        tileSources.remove(TileSourceFactory.BASE);
        tileSources.remove(TileSourceFactory.TOPO);
        tileSources.remove(TileSourceFactory.HILLS);
        tileSources.remove(TileSourceFactory.CLOUDMADESMALLTILES);
        tileSources.remove(TileSourceFactory.MAPQUESTAERIAL);

        // MAPNIK, CYCLEMAP, CLOUDMADESTANDARDTILES, MAPQUESTOSM,

        // Add Licence Tiles
        // ------------------
        // only do static initialisation if needed
        // http://developers.cloudmade.com/projects/web-maps-api/examples
        if (CloudmadeUtil.getCloudmadeKey().length() == 0) {
            CloudmadeUtil.retrieveCloudmadeKey(context);
        }
        if (BingMapTileSource.getBingKey().length() == 0) {
            BingMapTileSource.retrieveBingKey(context);
        }
        final BingMapTileSource bmts = new BingMapTileSource(null);
        addTilesIfNotContainsInSource(bmts);
        // Add Other Tiles
        // if (!tileSources.contains(CLOUDMADE_VECTOR_TILES)) {
        //	if (!TileSourceFactory.containsTileSource(CLOUDMADE_VECTOR_TILES.name())) {
        //		TileSourceFactory.addTileSource(CLOUDMADE_VECTOR_TILES);
        //	}


        addTilesIfNotContainsInSource(SKECHBOY_TILES);

        addTilesIfNotContainsInSource(STAMEN_WATERCOLOR_TILES);
//        addTilesIfNotContainsInSource(STAMEN_BLACK_AND_WHITE_TILES);
//        addTilesIfNotContainsInSource(STAMEN_HILL_SHADING_TILES);

        addTilesIfNotContainsInSource(MAPNIK_BLACK_AND_WHITE_TILES);
        addTilesIfNotContainsInSource(MAPNIK_HIKEBIKE_TILES);

        addTilesIfNotContainsInSource(MAPNIK_SCALE_2);
      //  addTilesIfNotContainsInSource(MAPNIK_SCALE_3);

        // addTilesIfNotContainsInSource(CLOUDMADE_SSL_TILES);

        // TileSourceFactory.addTileSource(PISTEMAP);

    }

    private static boolean addTilesIfNotContainsInSource(OnlineTileSourceBase tile) {
        boolean result = false;
        if (!TileSourceFactory.containsTileSource(tile.name())) {
            TileSourceFactory.addTileSource(tile);
            result = true;
        }
        return result;
    }

}
