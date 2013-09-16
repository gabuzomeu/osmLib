package eu.ttbox.osm.ui.map.mylocation.sensor.v2;


public class GPSInfo {
    public int foundSatellites = 0;
    public int usedSatellites = 0;
    public boolean fixed = false;

    @Override
    public String toString() {
        return "GPSInfo{" +
                "fixed=" + fixed +
                ", usedSatellites=" + usedSatellites +
                ", foundSatellites=" + foundSatellites +
                '}';
    }
}
