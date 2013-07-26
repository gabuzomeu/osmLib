package eu.ttbox.osm.ui.map.mylocation.bubble;

import java.util.Locale;

import android.content.Context;
import android.location.Address;
import android.location.Location;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import eu.ttbox.osm.R;
import eu.ttbox.osm.core.ExternalIntents;
import eu.ttbox.osm.ui.map.mylocation.CompassEnum;

public class MyLocationBubble extends FrameLayout {

    private final static String TAG = "MyLocationBubble";

    // Config
    private int DEFAULT_BUBBLE_WIDTH = 300;

    // Datas
    private Location location;

    // Display
    private LinearLayout layout;
    private TextView coordTextView;
    private TextView accuracyTextView;
    private TextView altitudeTextView;
    private View altitudeBlock;
    private TextView speedTextView;
    private TextView bearingTextView;
    private TextView providerTextView;
    private TextView addressTextView;
    private ImageView gpsStatusImageView;

    // Config
    private boolean displayGeoLoc = true;

    public MyLocationBubble(Context context) {
        super(context);
        layout = new LinearLayout(context);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.map_mylocation_bubble, layout);

        // Init fields
        this.coordTextView = (TextView) v.findViewById(R.id.map_mylocation_dialogView_coord);
        this.accuracyTextView = (TextView) v.findViewById(R.id.map_mylocation_dialogView_accuracy);
        this.addressTextView = (TextView) v.findViewById(R.id.map_mylocation_dialogView_address);
        this.altitudeTextView = (TextView) v.findViewById(R.id.map_mylocation_dialogView_altitude);
        this.altitudeBlock = v.findViewById(R.id.map_mylocation_dialogView_block_altitude);
        this.speedTextView = (TextView) v.findViewById(R.id.map_mylocation_dialogView_speed);
        this.bearingTextView = (TextView) v.findViewById(R.id.map_mylocation_dialogView_bearing);
        this.providerTextView = (TextView) v.findViewById(R.id.map_mylocation_dialogView_provider);
        this.gpsStatusImageView = (ImageView) v.findViewById(R.id.map_mylocation_dialogView_gpsStatus_image);
        this.gpsStatusImageView.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                startGpsStatus();
            }
        });
        // Frame
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.NO_GRAVITY;
        params.width = DEFAULT_BUBBLE_WIDTH;
        addView(layout, params);
    }

    private void startGpsStatus() {
        ExternalIntents.startActivityGpsStatus(getContext());
    }

    private boolean isSamelocation(Location location, Location other) {
        if (location != null && other != null) {
            // TODO
        }
        return false;
    }

    public void setData(Location location) {
        if (this.location == null || !isSamelocation(this.location, location)) {
            this.location = location;
            setAddress(null);
        }
        boolean hasLatLng = false;
        boolean hasAccuracy = false;
        boolean hasAltitude = false;
        boolean hasSpeed = false;
        boolean hasProvider = false;
        boolean hasTime = false;
        boolean hasBearing = false;
        if (location != null) {
            hasLatLng = true;
            hasTime = true;
            hasProvider = true;
            hasAccuracy = location.hasAccuracy();
            hasAltitude = location.hasAltitude();
            hasSpeed = location.hasSpeed();
            hasBearing = location.hasBearing();
        }
        // Provider
        if (hasProvider) {
            providerTextView.setText(location.getProvider());
            providerTextView.setVisibility(VISIBLE);
        } else {
            providerTextView.setVisibility(GONE);
        }
        // Coord
        if (hasLatLng && displayGeoLoc) {
            double lat = location.getLatitude();
            double lng = location.getLongitude();
            String coordString = String.format(Locale.US, "(%.6f, %.6f)", lat, lng);
            coordTextView.setText(coordString);
            coordTextView.setVisibility(VISIBLE);
        } else {
            coordTextView.setVisibility(GONE);
        }

        // Accuracy
        if (hasAccuracy) {
            accuracyTextView.setText(String.format("%s m", (int) location.getAccuracy()));
            accuracyTextView.setVisibility(VISIBLE);
        } else {
            accuracyTextView.setText("");
            accuracyTextView.setVisibility(GONE);
        }
        // Altitude
        if (hasAltitude) {
            altitudeTextView.setText(String.format("%s m", (int) location.getAltitude()));
            altitudeBlock.setVisibility(VISIBLE);
        } else {
            altitudeTextView.setText("");
            altitudeBlock.setVisibility(GONE);
        }
        // Speed
        if (hasSpeed) {
            float speedMs = location.getSpeed();
            int speedKh = (int) (speedMs * 3.6);
            speedTextView.setText(String.format("%s km/h", speedKh));
            speedTextView.setVisibility(VISIBLE);
        } else {
            speedTextView.setText("");
            speedTextView.setVisibility(GONE);
        }
        // Bearing
        if (hasBearing) {
            String compassAsString = null;
            float bearing = (float) location.getBearing();
            CompassEnum compass = CompassEnum.getCardinalPoint(bearing);
            if (compass != null) {
                compassAsString = compass.getI18nLabelShort(getContext());
            }
            bearingTextView.setText(String.format("%s° %s", (int) bearing, compassAsString));
            bearingTextView.setVisibility(VISIBLE);
        } else {
            bearingTextView.setText("");
            bearingTextView.setVisibility(GONE);
        }
    }

    public void setAddress(Address addr) {
        if (addr != null) {
            StringBuilder addrBuilder = new StringBuilder();
            boolean isNotFist = false;
            for (int i = 0; i < addr.getMaxAddressLineIndex(); i++) {
                if (isNotFist) {
                    addrBuilder.append(", ");
                } else {
                    isNotFist = true;
                }
                String addrLine = addr.getAddressLine(i);
                addrBuilder.append(addrLine);
            }
            // addrBuilder.append(addr.getPostalCode()).append(", ");
            // addrBuilder.append(addr.getLocality()).append("\n");
            // addrBuilder.append(addr.getCountryName());

            addressTextView.setText(addrBuilder);
            addressTextView.setVisibility(VISIBLE);
        } else {
            addressTextView.setVisibility(GONE);
        }

    }

    public void setDisplayGeoLoc(boolean displayGeoLoc) {
        this.displayGeoLoc = displayGeoLoc;
    }

}
