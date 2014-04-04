package eu.ttbox.osm.core;

import android.app.AlarmManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import org.osmdroid.util.GeoPoint;

import java.util.List;

public class LocationUtils {

    private static final String TAG = "LocationUtils";

    private static final int LOCALISATION_SIGNIFICATY_NEWER_IN_MS = 1000 * 60 * 1;
    private static final int MAX_DISTANCE = 75;

    private LocationUtils() {
    }



    // ===========================================================
    //   Static Utils
    // ===========================================================



    public static GeoPoint convertLocationAsGeoPoint(Location location) {
        return GeoLocHelper.convertLocationAsGeoPoint(location);
    }

    // ===========================================================
    //  Sensor
    // ===========================================================


    public static boolean isGpsLocationProviderIsEnable(LocationManager locationManage) {
        return locationManage.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }


    /** Checks whether two providers are the same */
    private static boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }


    // ===========================================================
    //   Last Loc V1
    // ===========================================================


    public static Location getLastKnownLocation(LocationManager locationManager) {
        Location lastKnownLocation = null;
        // Check all localisation Provider
        List<String> providers = locationManager.getProviders(false);
        if (providers != null && !providers.isEmpty()) {
            for (final String provider : providers) {
                Location providerLoc = locationManager.getLastKnownLocation(provider);
                if (providerLoc != null) {
                     if (isBetterLocation(providerLoc, lastKnownLocation)) {
                        lastKnownLocation = providerLoc;
                    }
                }
            }
        }
        return lastKnownLocation;
    }


    /**
     * Determines whether one Location reading is better than the current
     * Location fix
     *
     * @param location
     *            The new Location that you want to evaluate
     * @param currentBestLocation
     *            The current Location fix, to which you want to compare the new
     *            one
     */
    public static boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        } else if (location == null) {
            return false;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > LOCALISATION_SIGNIFICATY_NEWER_IN_MS;
        boolean isSignificantlyOlder = timeDelta < -LOCALISATION_SIGNIFICATY_NEWER_IN_MS;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use
        // the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be
            // worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200; // MAX_DISTANCE

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(), currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and
        // accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }


    public static boolean isLocationTooOld(Location location,  long nowInMs) {
        long timeDelta = nowInMs - location.getTime();
        boolean isSignificantlyOlder = timeDelta >=  LOCALISATION_SIGNIFICATY_NEWER_IN_MS;
        Log.d(TAG, "isLocationTooOld for delta : " + timeDelta + " ==> " + isSignificantlyOlder);
        return isSignificantlyOlder;
    }

    // ===========================================================
    //   Last Loc V2
    // ===========================================================


    /**
     * Returns the most accurate and timely previously detected location.
     * Where the last result is beyond the specified maximum distance or latency.
     * With minDistance = 75 m
     * With minTime = Now - 15 minutes
     * @param locationManager The location Manager Service
     * @return The most accurate and / or timely previously detected location.
     */
    public static Location getLastBestLocation(LocationManager locationManager ) {
        long minTime = System.currentTimeMillis()- AlarmManager.INTERVAL_FIFTEEN_MINUTES;
        return getLastBestLocation(locationManager, MAX_DISTANCE, minTime);
    }
    /**
     * Returns the most accurate and timely previously detected location.
     * Where the last result is beyond the specified maximum distance or
     * latency
     * @param locationManager The location Manager Service
     * @param minDistance Minimum distance before we require a location update.
     * @param minTime Minimum time required between location updates.
     * @return The most accurate and / or timely previously detected location.
     */
    public static Location getLastBestLocation(LocationManager locationManager, int minDistance, long minTime) {
        Location bestResult = null;
        float bestAccuracy = Float.MAX_VALUE;
        long bestTime = Long.MAX_VALUE;

        // Iterate through all the providers on the system, keeping
        // note of the most accurate result within the acceptable time limit.
        // If no result is found within maxTime, return the newest Location.
        List<String> matchingProviders = locationManager.getAllProviders();
        for (String provider: matchingProviders) {
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null) {
                float accuracy = location.getAccuracy();
                long time = location.getTime();

                if ((time < minTime && accuracy < bestAccuracy)) {
                    bestResult = location;
                    bestAccuracy = accuracy;
                    bestTime = time;
                }
                else if (time > minTime && bestAccuracy == Float.MAX_VALUE && time < bestTime) {
                    bestResult = location;
                    bestTime = time;
                }
            }
        }


        return bestResult;
    }

    public static GeoPoint getLastBestLocationAsGeoPoint(LocationManager locationManager, int minDistance, long minTime) {
        Location lastKnownLocation = getLastBestLocation(locationManager);
        GeoPoint myGeoPoint = GeoLocHelper.convertLocationAsGeoPoint(lastKnownLocation);
        return myGeoPoint;
    }



    public static GeoPoint getLastKnownLocationAsGeoPoint(LocationManager locationManager) {
        Location lastKnownLocation = getLastKnownLocation(locationManager);
        GeoPoint myGeoPoint = GeoLocHelper.convertLocationAsGeoPoint(lastKnownLocation);
        return myGeoPoint;
    }


    // ===========================================================
    //   Other
    // ===========================================================



}
