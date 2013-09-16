package eu.ttbox.osm.ui.map.mylocation.sensor.v2;


import android.app.Application;
import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.ttbox.osm.core.LocationUtils;
import eu.ttbox.osm.ui.map.core.MapViewUtils;

public class OsmAndLocationProvider implements SensorEventListener {

    private static String TAG = "OsmAndLocationProvider";

    public interface OsmAndLocationListener {
        void onLocationChanged( OsmLocation location);
    }

    public interface OsmAndCompassListener {
        void updateCompassValue(float value);
    }


    private static final int INTERVAL_TO_CLEAR_SET_LOCATION = 30 * 1000;
    private static final int LOST_LOCATION_MSG_ID = 10;
    private static final long LOST_LOCATION_CHECK_DELAY = 18000;

    private static final float ACCURACY_FOR_GPX_AND_ROUTING = 50;

    private static final int GPS_TIMEOUT_REQUEST = 0;
    private static final int GPS_DIST_REQUEST = 0;
    private static final int NOT_SWITCH_TO_NETWORK_WHEN_GPS_LOST_MS = 12000;

    private long lastTimeGPSLocationFixed = 0;

    private boolean sensorRegistered = false;
    private float[] mGravs = new float[3];
    private float[] mGeoMags = new float[3];
    private float previousCorrectionValue = 360;



    private final boolean USE_KALMAN_FILTER = true;
    private final float KALMAN_COEFFICIENT = 0.04f;

    float avgValSin = 0;
    float avgValCos = 0;
    float lastValSin = 0;
    float lastValCos = 0;
    private float[] previousCompassValuesA = new float[50];
    private float[] previousCompassValuesB = new float[50];
    private int previousCompassIndA = 0;
    private int previousCompassIndB = 0;
    private boolean inUpdateValue = false;

    private Float heading = null;

    // Current screen orientation
    // Screen
    private final Display mDisplay;
  //  private int currentScreenOrientation;

    private Application app;


  //  private NavigationInfo navigationInfo;
  //  private CurrentPositionHelper currentPositionHelper;
  //  private OsmAndLocationSimulation locationSimulation;

    private  OsmLocation location = null;

    private GPSInfo gpsInfo = new GPSInfo();

    private List<OsmAndLocationListener> locationListeners = new CopyOnWriteArrayList<OsmAndLocationListener>();
    private List<OsmAndCompassListener> compassListeners = new CopyOnWriteArrayList<OsmAndCompassListener>();
    private GpsStatus.Listener gpsStatusListener;
    private float[] mRotationM =  new float[9];

    // Config
    private AtomicBoolean USE_MAGNETIC_FIELD_SENSOR_COMPASS;
    private AtomicBoolean USE_FILTER_FOR_COMPASS;

    //Status
    private AtomicBoolean isLocationEnable = new AtomicBoolean(false);


    // ===========================================================
    // Constructor
    // ===========================================================

    public OsmAndLocationProvider( Application ctx) {
        this.app = ctx;
       // navigationInfo = new NavigationInfo(app);

        USE_FILTER_FOR_COMPASS = new AtomicBoolean(true);
        USE_MAGNETIC_FIELD_SENSOR_COMPASS = isUseMagneticFieldSensorCompass();
       // currentPositionHelper = new CurrentPositionHelper(app);
       // locationSimulation = new OsmAndLocationSimulation(app, this);
        // Service
        // Screen
        final WindowManager windowManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        mDisplay = windowManager.getDefaultDisplay();
    }

    private AtomicBoolean isUseMagneticFieldSensorCompass() {
        SensorManager sensorMgr = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
        Sensor s = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (s != null ) {
            return new AtomicBoolean(true);
        }
        s = sensorMgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (s != null ) {
            return new AtomicBoolean(true);
        }
        return new AtomicBoolean(false);
    }


    // ===========================================================
    // Life Cycle
    // ===========================================================

    public void resumeAllUpdates() {
        isLocationEnable.set(true);

        final LocationManager service = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        service.addGpsStatusListener(getGpsStatusListener(service));
        try {
            service.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_TIMEOUT_REQUEST, GPS_DIST_REQUEST, gpsListener);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "GPS location provider not available"); //$NON-NLS-1$
        }
        // try to always ask for network provide : it is faster way to find location
        try {
            service.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, GPS_TIMEOUT_REQUEST, GPS_DIST_REQUEST, networkListener);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Network location provider not available"); //$NON-NLS-1$
        }
    }

    private void stopLocationRequests() {
        isLocationEnable.set(false);
        LocationManager service = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        service.removeGpsStatusListener(gpsStatusListener);
        service.removeUpdates(gpsListener);
        service.removeUpdates(networkListener);
    }

    public void pauseAllUpdates() {
        stopLocationRequests();
        SensorManager sensorMgr = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
        sensorMgr.unregisterListener(this);
        sensorRegistered = false;
    }





    // ===========================================================
    // Accessor
    // ===========================================================

    public GPSInfo getGPSInfo(){
        return gpsInfo;
    }

//    public void updateScreenOrientation(int orientation) {
//        currentScreenOrientation = orientation;
//    }

    public void addLocationListener(OsmAndLocationListener listener){
        if(!locationListeners.contains(listener)) {
            locationListeners.add(listener);
        }
    }

    public void removeLocationListener(OsmAndLocationListener listener){
        locationListeners.remove(listener);
    }

    public void addCompassListener(OsmAndCompassListener listener){
        if(!compassListeners.contains(listener)) {
            compassListeners.add(listener);
        }
    }

    public void removeCompassListener(OsmAndCompassListener listener){
        compassListeners.remove(listener);
    }

    public OsmLocation getFirstTimeRunDefaultLocation() {
        LocationManager service = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = new ArrayList<String>(service.getProviders(true));
        // note, passive provider is from API_LEVEL 8 but it is a constant, we can check for it.
        // constant should not be changed in future
        int passiveFirst = providers.indexOf("passive"); // LocationManager.PASSIVE_PROVIDER
        // put passive provider to first place
        if (passiveFirst > -1) {
            providers.add(0, providers.remove(passiveFirst));
        }
        // find location
        for (String provider : providers) {
            Location locProvider = service.getLastKnownLocation(provider);
            if (locProvider != null) {
                OsmLocation location = convertLocation(locProvider);
                return location;
            }
        }
        return null;
    }

    public void registerOrUnregisterCompassListener(boolean register) {
        if (sensorRegistered && !register) {
            Log.d(TAG, "Disable sensor"); //$NON-NLS-1$
            ((SensorManager) app.getSystemService(Context.SENSOR_SERVICE)).unregisterListener(this);
            sensorRegistered = false;
            heading = null;
        } else if (!sensorRegistered && register) {
            Log.d(TAG, "Enable sensor"); //$NON-NLS-1$
            SensorManager sensorMgr = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
            if (USE_MAGNETIC_FIELD_SENSOR_COMPASS.get()) {
                Sensor s = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (s == null || !sensorMgr.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)) {
                    Log.e(TAG, "Sensor accelerometer could not be enabled");
                }
                s = sensorMgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                if (s == null || !sensorMgr.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)) {
                    Log.e(TAG, "Sensor magnetic field could not be enabled");
                }
            } else {
                Sensor s = sensorMgr.getDefaultSensor(Sensor.TYPE_ORIENTATION);
                if (s == null || !sensorMgr.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)) {
                    Log.e(TAG, "Sensor orientation could not be enabled");
                }
            }
            sensorRegistered = true;
        }
    }


    public static boolean isPointAccurateForRouting(OsmLocation loc) {
        return loc != null && (!loc.hasAccuracy() || loc.getAccuracy() < ACCURACY_FOR_GPX_AND_ROUTING * 3 / 2);
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Attention : sensor produces a lot of events & can hang the system
        if(inUpdateValue) {
            return;
        }
        synchronized (this) {
            inUpdateValue = true;
            try {
                float val = 0;
                switch (event.sensor.getType()) {
                    case Sensor.TYPE_ACCELEROMETER:
                        System.arraycopy(event.values, 0, mGravs, 0, 3);
                        break;
                    case Sensor.TYPE_MAGNETIC_FIELD:
                        System.arraycopy(event.values, 0, mGeoMags, 0, 3);
                        break;
                    case Sensor.TYPE_ORIENTATION:
                        val = event.values[0];
                        break;
                    default:
                        return;
                }
                if (USE_MAGNETIC_FIELD_SENSOR_COMPASS.get()) {
                    if (mGravs != null && mGeoMags != null) {
                        boolean success = SensorManager.getRotationMatrix(mRotationM, null, mGravs, mGeoMags);
                        if (!success) {
                            return;
                        }
                        float[] orientation = SensorManager.getOrientation(mRotationM, new float[3]);
                        val = (float) Math.toDegrees(orientation[0]);
                    } else {
                        return;
                    }
                }
                //val = calcScreenOrientationCorrection(val);
                val = calcGeoMagneticCorrection(val);

                float valRad = (float) (val / 180f * Math.PI);
                lastValSin = (float) Math.sin(valRad);
                lastValCos = (float) Math.cos(valRad);
                // lastHeadingCalcTime = System.currentTimeMillis();
                boolean filter = USE_FILTER_FOR_COMPASS.get(); //USE_MAGNETIC_FIELD_SENSOR_COMPASS.get();
                if (filter) {
                    filterCompassValue();
                } else {
                    avgValSin = lastValSin;
                    avgValCos = lastValCos;
                }

                updateCompassVal();
            } finally {
                inUpdateValue = false;
            }
        }
    }


    // ===========================================================
    // Compute Correction
    // ===========================================================


    private float calcGeoMagneticCorrection(float val) {
        if (previousCorrectionValue == 360 && getLastKnownLocation() != null) {
            OsmLocation l = getLastKnownLocation();
            GeomagneticField gf = new GeomagneticField((float) l.getLatitude(), (float) l.getLongitude(), (float) l.getAltitude(),
                    System.currentTimeMillis());
            previousCorrectionValue = gf.getDeclination();
        }
        if (previousCorrectionValue != 360) {
            val += previousCorrectionValue;
        }
        return val;
    }

    public float calcScreenOrientationCorrection(float val) {
        int currentScreenOrientation = mDisplay.getRotation();
        if (currentScreenOrientation == Surface.ROTATION_90) {
            val += 90;
        } else if (currentScreenOrientation == Surface.ROTATION_180 ) {
            val += 180;
        } else if (currentScreenOrientation == Surface.ROTATION_270 ) {
            val -= 90;
        }
        return val;
    }

    private void filterCompassValue() {
        if(heading == null && previousCompassIndA == 0) {
            Arrays.fill(previousCompassValuesA, lastValSin);
            Arrays.fill(previousCompassValuesB, lastValCos);
            avgValSin = lastValSin;
            avgValCos = lastValCos;
        } else {
            if (USE_KALMAN_FILTER) {
                avgValSin = KALMAN_COEFFICIENT * lastValSin + avgValSin * (1 - KALMAN_COEFFICIENT);
                avgValCos = KALMAN_COEFFICIENT * lastValCos + avgValCos * (1 - KALMAN_COEFFICIENT);
            } else {
                int l = previousCompassValuesA.length;
                previousCompassIndA = (previousCompassIndA + 1) % l;
                previousCompassIndB = (previousCompassIndB + 1) % l;
                // update average
                avgValSin = avgValSin + (-previousCompassValuesA[previousCompassIndA] + lastValSin) / l;
                previousCompassValuesA[previousCompassIndA] = lastValSin;
                avgValCos = avgValCos + (-previousCompassValuesB[previousCompassIndB] + lastValCos) / l;
                previousCompassValuesB[previousCompassIndB] = lastValCos;
            }
        }
    }

    private void updateCompassVal() {
        heading = (float) getAngle(avgValSin, avgValCos);
        if (!compassListeners.isEmpty()) {
//            if (compassListeners.size()==1) {
//                OsmAndCompassListener c = compassListeners.get(0);
//                c.updateCompassValue(heading.floatValue());
//            } else {
                for(OsmAndCompassListener c : compassListeners){
                    c.updateCompassValue(heading.floatValue());
                }
//            }
        }
    }


    // ===========================================================
    // Azimuth
    // ===========================================================

    public Float getHeading() {
        return heading;
    }

    public float getDisplayAzimut() {
        float val = 0;
        if (heading!=null) {
            val = calcScreenOrientationCorrection(heading.floatValue());
        }
        return val;
    }

    private float getAngle(float sinA, float cosA) {
        return MapViewUtils.unifyRotationTo360((float) (Math.atan2(sinA, cosA) * 180 / Math.PI));
    }


    private void updateLocation(OsmLocation loc ) {
        if (!locationListeners.isEmpty()) {
//            if (locationListeners.size()==1) {
//                OsmAndLocationListener l = locationListeners.get(0);
//                l.onLocationChanged(loc);
//            } else {
                for(OsmAndLocationListener l : locationListeners){
                    l.onLocationChanged(loc);
                }
//            }
        }
    }


    private boolean useOnlyGPS() {
//        if(app.getRoutingHelper().isFollowingMode()) {
//            return true;
//        }
        if((System.currentTimeMillis() - lastTimeGPSLocationFixed) < NOT_SWITCH_TO_NETWORK_WHEN_GPS_LOST_MS) {
            return true;
        }
        return false;
    }


    public static OsmLocation convertLocation(Location l) {
        if (l == null) {
            return null;
        }
        OsmLocation r = new OsmLocation(l);
//        OsmLocation r = new OsmLocation(l.getProvider());
//        r.setLatitude(l.getLatitude());
//        r.setLongitude(l.getLongitude());
//        r.setTime(l.getTime());
//        if (l.hasAccuracy()) {
//            r.setAccuracy(l.getAccuracy());
//        }
//        if (l.hasSpeed()) {
//            r.setSpeed(l.getSpeed());
//        }
//        if (l.hasAltitude()) {
//            r.setAltitude(l.getAltitude());
//        }
//        if (l.hasBearing()) {
//            r.setBearing(l.getBearing());
//        }
//        if (l.hasAltitude() && app != null) {
//            double alt = l.getAltitude();
//            final GeoidAltitudeCorrection geo = app.getResourceManager().getGeoidAltitudeCorrection();
//            if (geo != null) {
//                alt -= geo.getGeoidHeight(l.getLatitude(), l.getLongitude());
//                r.setAltitude(alt);
//            }
//        }
        return r;
    }


    private void scheduleCheckIfGpsLost(OsmLocation location) {
//        final RoutingHelper routingHelper = app.getRoutingHelper();
//        if (location != null) {
//            final long fixTime = location.getTime();
//            app.runMessageInUIThreadAndCancelPrevious(LOST_LOCATION_MSG_ID, new Runnable() {
//
//                @Override
//                public void run() {
//                    OsmLocation lastKnown = getLastKnownLocation();
//                    if (lastKnown != null && lastKnown.getTime() > fixTime) {
//                        // false positive case, still strange how we got here with removeMessages
//                        return;
//                    }
//                    if (routingHelper.isFollowingMode() && routingHelper.getLeftDistance() > 0) {
//                        routingHelper.getVoiceRouter().gpsLocationLost();
//                    }
//                    setLocation(null);
//                }
//            }, LOST_LOCATION_CHECK_DELAY);
//        }
    }
    public void setLocationFromService(OsmLocation location, boolean continuous) {
        // if continuous notify about lost location
        if (continuous) {
            scheduleCheckIfGpsLost(location);
        }
       // app.getSavingTrackHelper().updateLocation(location);
       // app.getLiveMonitoringHelper().updateLocation(location);
        // 2. accessibility routing
        // navigationInfo.setLocation(location);

        // app.getRoutingHelper().updateLocation(location);
    }



    public void checkIfLastKnownLocationIsValid() {
        OsmLocation loc = getLastKnownLocation();
        if (loc != null && (System.currentTimeMillis() - loc.getTime()) > INTERVAL_TO_CLEAR_SET_LOCATION) {
            setLocation(null, false);
        }
    }

//    public NavigationInfo getNavigationInfo() {
//        return navigationInfo;
//    }
//
//    public String getNavigationHint(LatLon point) {
//        String hint = navigationInfo.getDirectionString(point, getHeading());
//        if (hint == null)
//            hint = app.getString(R.string.no_info);
//        return hint;
//    }
//
//    public boolean emitNavigationHint() {
//        final LatLon point = app.getTargetPointsHelper().getPointToNavigate();
//        if (point != null) {
//            if (app.getRoutingHelper().isRouteCalculated()) {
//                app.getRoutingHelper().getVoiceRouter().announceCurrentDirection(getLastKnownLocation());
//            } else {
//                app.showToastMessage(getNavigationHint(point));
//            }
//            return true;
//        } else {
//            app.showShortToastMessage(R.string.access_mark_final_location_first);
//            return false;
//        }
//    }
//
//    public RouteDataObject getLastKnownRouteSegment() {
//        return currentPositionHelper.getLastKnownRouteSegment(getLastKnownLocation());
//    }

    public OsmLocation getLastKnownLocation() {
        // Ask All Sensor, This is not the last know location
        if (location== null) {
            LocationManager locationManager = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
            Location lastKnownLocation = LocationUtils.getLastKnownLocation(locationManager);
            if (lastKnownLocation != null) {
                Log.d(TAG, String.format("Use LastKnownLocation with provider [%s] : %s", lastKnownLocation.getProvider(), lastKnownLocation));
                if (location == null) {
                    OsmLocation loc = convertLocation(lastKnownLocation);
                    setLocation(loc, false);
                }
            }
        }
        return location;
    }


//    public void showNavigationInfo(LatLon pointToNavigate, Context uiActivity) {
//        getNavigationInfo().show(pointToNavigate, getHeading(), uiActivity);
//
//    }
//
//    public OsmAndLocationSimulation getLocationSimulation() {
//        return locationSimulation;
//    }



    public OsmLocation getLastFix() {
        return location;
    }

    public GeoPoint getLastFixAsGeoPoint() {
        GeoPoint geoPoint = location!=null ? location.getLocationAsGeoPoint(): null;
        return geoPoint;
    }

    public Location getLastFixAsLocation() {
        Location loc = location!=null ? location.getLocation(): null;
        return loc;
    }

    public boolean isFixLocation() {
        return location!=null;
    }

    public boolean isProviderEnabled(String provider){
        LocationManager locationManager = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(provider);
    }

    public boolean isMyLocationEnabled() {
        return isLocationEnable.get();
    }


    // ===========================================================
    // Update Location Event
    // ===========================================================

    private void setLocation(OsmLocation location, boolean dispatchEvent) {
        if(location == null){
            updateGPSInfo(null);
        }
        //       enhanceLocation(location);
        //       scheduleCheckIfGpsLost(location);
        //final RoutingHelper routingHelper = app.getRoutingHelper();
        // 1. Logging services
//        if (location != null) {
//            app.getSavingTrackHelper().updateLocation(location);
//            app.getLiveMonitoringHelper().updateLocation(location);
//        }
        // 2. accessibility routing
//        navigationInfo.setLocation(location);

        // 3. routing
        OsmLocation updatedLocation = location;
//        if (routingHelper.isFollowingMode()) {
//            if (location == null || isPointAccurateForRouting(location)) {
        // Update routing position and get location for sticking mode
//                updatedLocation = routingHelper.setCurrentLocation(location, settings.SNAP_TO_ROAD.get());
//            }
//        }
        this.location = updatedLocation;

        // Update information
        if (dispatchEvent) {
            updateLocation(updatedLocation);
        }
    }

    // ===========================================================
    // Gps Info Event
    // ===========================================================


    private void updateGPSInfo(GpsStatus s) {
        boolean fixed = false;
        int n = 0;
        int u = 0;
        if (s != null) {
            Iterator<GpsSatellite> iterator = s.getSatellites().iterator();
            while (iterator.hasNext()) {
                GpsSatellite g = iterator.next();
                n++;
                if (g.usedInFix()) {
                    u++;
                    fixed = true;
                }
            }
        }
        gpsInfo.fixed = fixed;
        gpsInfo.foundSatellites = n;
        gpsInfo.usedSatellites = u;
        Log.d(TAG, "updateGPSInfo : " + gpsInfo);
    }



    // ===========================================================
    // Listener
    // ===========================================================

    private GpsStatus.Listener getGpsStatusListener(final LocationManager service) {
        gpsStatusListener = new GpsStatus.Listener() {
            private GpsStatus gpsStatus;
            @Override
            public void onGpsStatusChanged(int event) {
                gpsStatus = service.getGpsStatus(gpsStatus);
                updateGPSInfo(gpsStatus);
                updateLocation(location);
            }
        };
        return gpsStatusListener;
    }

    private LocationListener gpsListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location locationChanged) {
            if (locationChanged != null) {
                lastTimeGPSLocationFixed = locationChanged.getTime();
            }
            Location lastFix = location!=null ? location.getLocation() : null;
            if (!LocationUtils.isBetterLocation(locationChanged,lastFix )) {
                return;
            }
            //if(!locationSimulation.isRouteAnimating()) {
            Log.d(TAG, "gpsListener : " + locationChanged);
            setLocation(convertLocation(locationChanged), true);
            //}
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    // Working with location listeners
    private LocationListener networkListener = new LocationListener() {

        @Override
        public void onLocationChanged(Location locationChanged) {
            // double check about use only gps
            // that strange situation but it could happen?
            //if (!useOnlyGPS() && !locationSimulation.isRouteAnimating()) {
            Log.d(TAG, "networkListener : " + locationChanged);
            Location lastFix = location!=null ? location.getLocation() : null;
            if (!LocationUtils.isBetterLocation(locationChanged,lastFix )) {
                return;
            }
            setLocation(convertLocation(locationChanged), true);
            //}
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

    };


    // ===========================================================
    // Other
    // ===========================================================

}
