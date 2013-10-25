package eu.ttbox.osm.core;

import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import org.osmdroid.util.GeoPoint;

import java.util.List;

public class LocationUtils {

    private static final String TAG = "LocationUtils";

    private static final int LOCALISATION_SIGNIFICATY_NEWER_IN_MS = 1000 * 60 * 1;

    private LocationUtils() {
    }

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

    public static GeoPoint getLastKnownLocationAsGeoPoint(LocationManager locationManager) {
        Location lastKnownLocation = getLastKnownLocation(locationManager);
        GeoPoint myGeoPoint = GeoLocHelper.convertLocationAsGeoPoint(lastKnownLocation);
        return myGeoPoint;
    }


    public static boolean isGpsLocationProviderIsEnable(LocationManager locationManage) {
        return locationManage.isProviderEnabled(LocationManager.GPS_PROVIDER);
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
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

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

    /** Checks whether two providers are the same */
    private static boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    public static boolean isLocationTooOld(Location location,  long nowInMs) {
        long timeDelta = nowInMs - location.getTime();
        boolean isSignificantlyOlder = timeDelta >=  LOCALISATION_SIGNIFICATY_NEWER_IN_MS;
        Log.d(TAG, "isLocationTooOld for delta : " + timeDelta + " ==> " + isSignificantlyOlder);
        return isSignificantlyOlder;
    }
}
