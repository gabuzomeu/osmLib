package eu.ttbox.osm.test.core;


import android.location.Location;


import java.util.GregorianCalendar;

import junit.framework.TestCase;

public class LocationUtilsTest  extends   AndroidTestCase  {


    private static final String TAG = "LocationUtilsTest";

    @SmallTest
    public void testIsLocationTooOld() {
            // Location
            Location location = new Location("gps");
            location.setAccuracy(60);
            // Date
            GregorianCalendar cal = new GregorianCalendar();
            cal.set(2013, 12, 2, 9, 0, 0);
            location.setTime(cal.getTime().getTime());
            // Too Old
            cal.set(2013, 12, 2, 12, 0, 0);
            assertTrue(LocationUtils.isLocationTooOld(location, cal.getTime().getTime() ));
            cal.set(2013, 12, 2, 9, 5, 0);
            assertTrue(LocationUtils.isLocationTooOld(location, cal.getTime().getTime() ));
            cal.set(2013, 12, 2, 9, 1, 0);
            assertTrue(LocationUtils.isLocationTooOld(location, cal.getTime().getTime() ));

            // Is valid
            cal.set(2013, 12, 2, 9, 0, 59);
            assertFalse(LocationUtils.isLocationTooOld(location, cal.getTime().getTime() ));
            cal.set(2013, 12, 2, 8, 59, 59);
            assertFalse(LocationUtils.isLocationTooOld(location, cal.getTime().getTime() ));
    }
}
