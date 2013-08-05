package eu.ttbox.osm.ui.map.mylocation.sensor.v2;

import android.location.Location;

import org.osmdroid.util.GeoPoint;

import eu.ttbox.osm.core.GeoLocHelper;

public class OsmLocation {

    private Location location;
    private GeoPoint locationAsGeoPoint;


    public OsmLocation(Location location) {
        this.location = location;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
        this.locationAsGeoPoint = GeoLocHelper.convertLocationAsGeoPoint(location, locationAsGeoPoint);;
    }

    public float distanceTo(OsmLocation otherLoc) {
        return (location!=null && otherLoc.location!=null )? this.location.distanceTo(otherLoc.location) : -1;
    }

    public long getTime() {
        return location.getTime();
    }

    public boolean hasAccuracy() {
        return location.hasAccuracy();
    }

    public float getAccuracy() {
        return location.getAccuracy();
    }

    public String getProvider() {
        return location.getProvider();
    }

    public double getLatitude() {
        return location.getLatitude();
    }

    public double getLongitude() {
        return location.getLongitude();
    }

    public boolean hasAltitude() {
        return location.hasAltitude();
    }


    public double getAltitude() {
        return location.getAltitude();
    }


    public boolean hasSpeed() {
        return location.hasSpeed();
    }


    public float getSpeed() {
        return location.getSpeed();
    }

    public boolean hasBearing() {
        return location.hasBearing();
    }


    public float getBearing() {
        return location.getBearing();
    }


}
