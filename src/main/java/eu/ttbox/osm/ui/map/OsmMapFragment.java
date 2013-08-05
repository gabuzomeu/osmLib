package eu.ttbox.osm.ui.map;

import android.app.ActivityManager;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MinimapOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.ScaleBarOverlay;

import java.util.List;

import eu.ttbox.osm.ui.map.mylocation.MyLocationOverlay2;


public abstract class OsmMapFragment extends Fragment {

    private static final String TAG = "OsmMapFragment";
    
    // Map
    public MapController mapController;
    public MapView mapView;
    public ResourceProxy mResourceProxy;

    // Overlay
    public MyLocationOverlay2 myLocation = null;
    public MinimapOverlay miniMapOverlay = null;
    public ScaleBarOverlay mScaleBarOverlay= null;


    // ===========================================================
    // Constructor
    // ===========================================================


    public void initMap(){
        ActivityManager activityManager = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        // Map Controler
        this.mResourceProxy = new DefaultResourceProxyImpl(getActivity().getApplicationContext());
        ITileSource tileSource = getPreferenceMapViewTileSource();
        this.mapView = MapViewFactory.createOsmMapView(getActivity().getApplicationContext(), mResourceProxy, tileSource, activityManager);
        this.mapController = mapView.getController();

    }


    abstract ITileSource getPreferenceMapViewTileSource();


    // ===========================================================
    // Life Cycle
    // ===========================================================

    @Override
    public void onResume() {

        Log.i(TAG, "### ### ### ### ### onResume call ### ### ### ### ###");

        super.onResume();

        // read preference
        ITileSource tileSource = getPreferenceMapViewTileSource();
        mapView.setTileSource(tileSource);



        // Overlay MyLocation
        if (myLocation != null) {
            myLocation.onResume();
        }
    }


    @Override
    public void onPause() {
        Log.i(TAG, "### ### ### ### ### onPause call ### ### ### ### ###");

        // Overlay May Location
        if (myLocation != null) {
            myLocation.onPause();
        }
        super.onPause();
    }

    public void restoreMapPreference(SharedPreferences prefs) {
        // --- Map Preference
        // --- -----------------
        ITileSource tileSource = getPreferenceMapViewTileSource(prefs);
        mapView.setTileSource(tileSource);
        // Zoon 1 is world view
        mapController.setZoom(prefs.getInt(MapConstants.PREFS_ZOOM_LEVEL, tileSource.getMaximumZoomLevel()));

        // Overlay
        addOverlayMyLocation(prefs.getBoolean(MapConstants.PREFS_SHOW_LOCATION, false));
        addOverlayMinimap(prefs.getBoolean(MapConstants.PREFS_SHOW_OVERLAY_MINIMAP, false));
        addOverlayScaleBar(prefs.getBoolean(MapConstants.PREFS_SHOW_OVERLAY_SCALEBAR, false));

        // Prefernece
        if (this.myLocation!=null) {
            this.myLocation.enableCompass(prefs.getBoolean(MapConstants.PREFS_SHOW_COMPASS, false));
        }


        // Center
        int scrollX = prefs.getInt(MapConstants.PREFS_SCROLL_X, Integer.MIN_VALUE);
        int scrollY = prefs.getInt(MapConstants.PREFS_SCROLL_Y, Integer.MIN_VALUE);
        if (Integer.MIN_VALUE != scrollX && Integer.MIN_VALUE != scrollY) {
            Log.d(TAG, "CenterMap onResumeCenterOnLastPosition : " + scrollX + ";" + scrollY);
            mapView.scrollTo(scrollX, scrollY);
        } else {
            GeoPoint geoPoint = myLocation.getLastKnownLocationAsGeoPoint();
            if (geoPoint != null) {
                Log.d(TAG, "CenterMap on LastKnownLocation : " + geoPoint);
                mapController.setCenter(geoPoint);
            }
        }
    }

    protected ITileSource getPreferenceMapViewTileSource(SharedPreferences privateSharedPreferences) {
        final String tileSourceName = privateSharedPreferences.getString(MapConstants.PREFS_TILE_SOURCE, TileSourceFactory.DEFAULT_TILE_SOURCE.name());
        ITileSource tileSource = null;
        try {
            tileSource = TileSourceFactory.getTileSource(tileSourceName);
        } catch (final IllegalArgumentException ignore) {
        }
        if (tileSource==null) {
            tileSource = TileSourceFactory.DEFAULT_TILE_SOURCE;
        }
        return tileSource;
    }


    public void saveMapPreference(SharedPreferences privateSharedPreferences) {
        final SharedPreferences.Editor localEdit = privateSharedPreferences.edit();
        saveMapPreference(localEdit);
        localEdit.commit();
    }

    public void saveMapPreference(SharedPreferences.Editor localEdit) {
        localEdit.putString(MapConstants.PREFS_TILE_SOURCE, mapView.getTileProvider().getTileSource().name());
        localEdit.putInt(MapConstants.PREFS_ZOOM_LEVEL, mapView.getZoomLevel());
        localEdit.putInt(MapConstants.PREFS_SCROLL_X, mapView.getScrollX());
        localEdit.putInt(MapConstants.PREFS_SCROLL_Y, mapView.getScrollY());
        // Status
        localEdit.putBoolean(MapConstants.PREFS_SHOW_LOCATION, myLocation.isMyLocationEnabled());
        localEdit.putBoolean(MapConstants.PREFS_SHOW_COMPASS, myLocation.isCompassEnabled());
        // Overlay
        localEdit.putBoolean(MapConstants.PREFS_SHOW_OVERLAY_MINIMAP, isOverlayMinimap());
        localEdit.putBoolean(MapConstants.PREFS_SHOW_OVERLAY_SCALEBAR, isOverlayScaleBar());
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "### ### ### ### ### onDestroy call ### ### ### ### ###");

        myLocation.disableCompass();
        myLocation.disableMyLocation();

        super.onDestroy();
    }
    // ===========================================================
    // Accessor
    // ===========================================================


    // ===========================================================
    // Map Tile
    // ===========================================================



    public ITileSource getMapViewTileSource() {
        return mapView.getTileProvider().getTileSource();
    }


    public void setMapViewTileSource(ITileSource tileSource) {
        IGeoPoint center =  mapView.getMapCenter();
        mapView.setTileSource(tileSource);
        mapController.setCenter(center);
    }

    // ===========================================================
    // Save
    // ===========================================================

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // outState.putInt(key, value)I
        super.onSaveInstanceState(outState);
    }

    // ===========================================================
    // Map Overlays
    // ===========================================================

    public MyLocationOverlay2 addOverlayMyLocation(boolean toAdd) {
        if (toAdd) {
            // Add
            if ( this.myLocation==null) {
                this.myLocation  = new MyLocationOverlay2(getActivity(), this.mapView);
            }
            List<Overlay> overlays  =mapView.getOverlays();
            if (!overlays.contains(myLocation)) {
                mapView.getOverlays().add(myLocation);
            }
        } else {
            // Delete
            if (myLocation!=null) {
                mapView.getOverlays().remove(myLocation);
            }
        }
        return myLocation;
    }

    public boolean isOverlayMyLocation() {
        boolean result = (myLocation!=null && mapView.getOverlays().contains(myLocation));
        return result;
    }

    public void addOverlayScaleBar(boolean toAdd) {
        if (toAdd) {
            // Add
            if (mScaleBarOverlay==null) {
                this.mScaleBarOverlay = new ScaleBarOverlay(getActivity(), mResourceProxy);
                this.mScaleBarOverlay.setMetric();
                // Scale bar tries to draw as 1-inch, so to put it in the top center, set x offset to
                // half screen width, minus half an inch.
                this.mScaleBarOverlay.setScaleBarOffset(getResources().getDisplayMetrics().widthPixels
                        / 2 - getResources().getDisplayMetrics().xdpi / 2, 10);
            }
            mapView.getOverlays().add(mScaleBarOverlay);
        } else {
            // Delete
            if (mScaleBarOverlay!=null) {
                mapView.getOverlays().remove(mScaleBarOverlay);
            }
        }
    }

    public boolean isOverlayScaleBar() {
        boolean result = (mScaleBarOverlay!=null && mapView.getOverlays().contains(mScaleBarOverlay));
        return result;
    }

    public void addOverlayMinimap(boolean toAdd) {
        if (toAdd) {
            // Add
            if (miniMapOverlay==null) {
                miniMapOverlay = new MinimapOverlay(getActivity(),  mapView.getTileRequestCompleteHandler());
            }
            mapView.getOverlays().add(miniMapOverlay);
        } else {
            // Delete
            if (miniMapOverlay!=null) {
                mapView.getOverlays().remove(miniMapOverlay);
            }
        }
    }

    public boolean isOverlayMinimap() {
        boolean result = (miniMapOverlay!=null && mapView.getOverlays().contains(miniMapOverlay));
        return result;
    }

}



