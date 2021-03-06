package eu.ttbox.osm.ui.map.mylocation;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.modules.ConfigurablePriorityThreadFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.ttbox.osm.R;
import eu.ttbox.osm.core.AppConstants;
import eu.ttbox.osm.ui.map.mylocation.bubble.MapCalloutView;
import eu.ttbox.osm.ui.map.mylocation.bubble.MapCalloutView.OnDoubleTapListener;
import eu.ttbox.osm.ui.map.mylocation.bubble.MyLocationBubble;
import eu.ttbox.osm.ui.map.mylocation.drawable.BlinkingDrawable;
import eu.ttbox.osm.ui.map.mylocation.sensor.MyLocationListenerProxy;
import eu.ttbox.osm.ui.map.mylocation.sensor.OrientationSensorEventListenerProxy;
import microsoft.mappoint.TileSystem;

/**
 * <a href="http://android-developers.blogspot.fr/2011/06/deep-dive-into-location.html">Deep dive into location</a>
 */
@Deprecated
public class MyLocationOverlay extends Overlay implements SensorEventListener, LocationListener, SharedPreferences.OnSharedPreferenceChangeListener {

    public static final boolean DEBUGMODE = false;
    // Message Handler
    protected static final int UI_MSG_SET_ADDRESS = 0;
    /**
     * Curious but true: a zero-length array is slightly lighter-weight than
     * merely allocating an Object, and can still be synchronized on.
     */
    static final Object[] sDataLock = new Object[0];
    private static final String TAG = "MyLocationOverlay";
    private static final int INDEX_FIRST = 0;
    private static final int INDEX_SECOND = 1;
    public static boolean isJb16 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    public final Geocoder geocoder;
    // Sensor
    public final MyLocationListenerProxy mLocationListener;
    public final OrientationSensorEventListenerProxy mOrientationListener;
    protected final MapView mMapView;
    private final IMapController mMapController;
    private final Display mDisplay;
    private final LocationManager locationManager;
    private final SensorManager mSensorManager;
    // Config Data
    private final LinkedList<Runnable> runOnFirstFix = new LinkedList<Runnable>();
    // Compass Config
    private final int mCompassCenterX = 35;
    private final int mCompassCenterY = 35;
    private final int mCompassRadius = 20;
    // to avoid allocations during onDraw
    private final Point mMapCoords = new Point();
    private final Matrix directionRotater = new Matrix();
    private final Matrix mCompassMatrix = new Matrix();
    private final Point tapPointScreenCoords = new Point();
    private final Rect tapPointHitTestRect = new Rect();
    private final BlinkingDrawable blinkDrawable;
    private final Callable<Void> blinkCallable = new Callable<Void>() {

        @Override
        public Void call() throws Exception {
            DIRECTION_ARROW_SELECTED = DIRECTION_ARROW_ON;
            if (isMyLocationEnabled() && (runOnFirstFixExecutor != null)) {
                runOnFirstFixExecutor.schedule(blinkCallableOff, 1l, TimeUnit.SECONDS);
            }
            return null;
        }
    };
    private final Callable<Void> blinkCallableOff = new Callable<Void>() {
        @Override
        public Void call() throws Exception {
            DIRECTION_ARROW_SELECTED = DIRECTION_ARROW;
            if (isMyLocationEnabled() && (runOnFirstFixExecutor != null)) {
                doBlink();
            }
            return null;
        }
    };
    private final OnDoubleTapListener mOnDoubleTapListener = new OnDoubleTapListener() {
        @Override
        public void onDoubleTap(View v) {
            // final Annotation annotation = getSelectedAnnotation();
            final Location lastFix = mLocationListener.getLastFix();
            GeoPoint lastFixAsGeoPoint = mLocationListener.getLastKnownLocationAsGeoPoint();
            if (lastFixAsGeoPoint != null) {
                mMapController.zoomToSpan(1, 1);
                mMapController.setCenter(lastFixAsGeoPoint);
            }
        }
    };
    protected boolean mFollow = true; // follow location updates
    protected boolean mDrawAccuracyEnabled = true;
    protected boolean mDrawCompassEnabled = false;
    protected AtomicBoolean mDrawMyLocationEnabled = new AtomicBoolean(false);
    protected Bitmap mCompassRose;
    protected int COMPASS_ROSE_CENTER_X;
    protected int COMPASS_ROSE_CENTER_Y;
    // Paint
    protected Paint mPaint;
    protected Paint mCirclePaint;
    protected Paint mCirclePaintBorder;
    protected Bitmap DIRECTION_ARROW;
    protected Bitmap DIRECTION_ARROW_ON;
    protected Bitmap DIRECTION_ARROW_SELECTED;
    protected int DIRECTION_ARROW_CENTER_X;
    protected int DIRECTION_ARROW_CENTER_Y;
    private Context context;

    // private final BitmapDrawable blinkBitmapDrawable;
    // Bubble
    private MyLocationBubble balloonView;
    private MapView.LayoutParams balloonViewLayoutParams;
    // ===========================================================
    // Constructors
    // ===========================================================
    // Prefs
    private SharedPreferences sharedPreferences;
    // Thread executor
    private ScheduledThreadPoolExecutor runOnFirstFixExecutor;
    // Dirty Box for Post Invalidation
    private Rect dirtyRectForLocationAccuracy = new Rect();
    private Rect dirtyRectForLocationDirection = new Rect();

    private Handler uiHandler = new MyInnerHandler(this);

    static class MyInnerHandler extends Handler{
        WeakReference<MyLocationOverlay> mFrag;

        MyInnerHandler(MyLocationOverlay aFragment) {
            mFrag = new WeakReference<MyLocationOverlay>(aFragment);
        }

        @Override
        public void handleMessage(Message msg) {
            MyLocationOverlay theFrag = mFrag.get();
            switch (msg.what) {
                case UI_MSG_SET_ADDRESS:
                    if (theFrag.balloonView != null) {
                        Address addr = (Address) msg.obj;
                        theFrag.balloonView.setAddress(addr);
                    }
                    break;
            }
        }
    }

    private int dirtyLastAzimuth = 0;
    private MapCalloutView mMapCallouts[] = new MapCalloutView[2];
    // ===========================================================
    // Listener
    // ===========================================================
    private int mMapCalloutIndex;

    // ===========================================================
    // Sensor
    // ===========================================================

    public MyLocationOverlay(final Context ctx, final MapView mapView) {
        this(ctx, mapView, new DefaultResourceProxyImpl(ctx));
    }

    public MyLocationOverlay(final Context ctx, final MapView mapView, final ResourceProxy pResourceProxy) {
        super(pResourceProxy);
        this.context = ctx;
        this.mMapView = mapView;
        this.mMapController = mapView.getController();
        // Screen
        final WindowManager windowManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        mDisplay = windowManager.getDefaultDisplay();
        // Resource
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        // Locate Service
        this.locationManager = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        this.mSensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        if (Geocoder.isPresent()) {
            this.geocoder = new Geocoder(context, Locale.getDefault());
        } else {
            this.geocoder = null;
            Log.w(TAG, "The Geocoder is not Present");
        }
        // Draw
        initDirectionPaint();
        // blink cheveron
        Resources r = context.getResources();
        Drawable onDrawable = r.getDrawable(R.drawable.vm_chevron_obscured_on);
        Drawable offDrawable = r.getDrawable(R.drawable.vm_chevron_obscured_off);
        blinkDrawable = new BlinkingDrawable(onDrawable, offDrawable);
//        blinkDrawable.doBlinkDrawable();
        // blinkBitmapDrawable = new BitmapDrawable(r, blinkDrawable);

        // Init sensor
        mLocationListener = new MyLocationListenerProxy(locationManager);
        mOrientationListener = new OrientationSensorEventListenerProxy(mSensorManager);
        // Threads Executor ?? Executors.newSingleThreadScheduledExecutor( ); ??
        enableThreadExecutors();
//        runOnFirstFixExecutor = new ScheduledThreadPoolExecutor(1, new ConfigurablePriorityThreadFactory(Thread.NORM_PRIORITY, "mylocationThread"));

    }

    public void onResume() {
        Log.d(TAG, "##### onResume ####");
        // enableMyLocation();
        // enableCompass();
        enableThreadExecutors();
    }

    public void onPause() {
        Log.d(TAG, "##### onPause ####");
        disableCompass();
        disableMyLocation();
        disableThreadExecutors();

    }

    // ===========================================================
    // GPS Sensor
    // ===========================================================

    @Override
    public void onDetach(final MapView mapView) {
        disableThreadExecutors();
        disableMyLocation();
        disableCompass();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        hideBubble(mapView);
        super.onDetach(mapView);
    }

    private void initDirectionPaint() {
        // D
        this.mPaint = new Paint();
        this.mPaint.setColor(Color.RED);
        this.mPaint.setStyle(Style.STROKE);


        // Compass
        mCompassRose = CompassPictureFactory.createCompassRoseBitmap(mCompassRadius, mScale);
        COMPASS_ROSE_CENTER_X = mCompassRose.getWidth() / 2;
        COMPASS_ROSE_CENTER_Y = mCompassRose.getHeight() / 2;

        // Localisation
        this.mCirclePaint = new Paint();
        this.mCirclePaint.setARGB(0, 100, 100, 255);
        this.mCirclePaint.setAntiAlias(true);
        this.mCirclePaint.setAlpha(50);
        this.mCirclePaint.setStyle(Style.FILL);

        this.mCirclePaintBorder = new Paint(mCirclePaint);
        this.mCirclePaintBorder.setAlpha(150);
        this.mCirclePaintBorder.setStyle(Style.STROKE);

        // Images

        // Direction Arrow
        this.DIRECTION_ARROW = BitmapFactory.decodeResource(context.getResources(), R.drawable.vm_chevron_obscured_off);
        this.DIRECTION_ARROW_SELECTED = DIRECTION_ARROW;
        this.DIRECTION_ARROW_CENTER_X = DIRECTION_ARROW.getWidth() / 2;
        this.DIRECTION_ARROW_CENTER_Y = DIRECTION_ARROW.getHeight() / 2;
        initDirtyBoxForLocationDirection();

        // For Blink bitmap
        this.DIRECTION_ARROW_ON = BitmapFactory.decodeResource(context.getResources(), R.drawable.vm_chevron_obscured_on);

    }

    private void initDirtyBoxForLocationDirection() {
        dirtyRectForLocationDirection.set(-DIRECTION_ARROW_SELECTED.getWidth(), -DIRECTION_ARROW_SELECTED.getHeight(), DIRECTION_ARROW_SELECTED.getWidth(), DIRECTION_ARROW_SELECTED.getHeight());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (AppConstants.PREFS_KEY_MYLOCATION_DISPLAY_GEOLOC.equals(key)) {
            boolean displayGeoLoc = sharedPreferences.getBoolean(AppConstants.PREFS_KEY_MYLOCATION_DISPLAY_GEOLOC, false);
            if (balloonView != null) {
                balloonView.setDisplayGeoLoc(displayGeoLoc);
            }
        }

    }

    @Override
    public void onSensorChanged(final SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            if (event.values != null) {
                int currentAzimuth = getDisplayAzimuth();
                if (dirtyLastAzimuth != currentAzimuth) {
                    mapViewInvalidate(dirtyRectForLocationDirection);
                    dirtyLastAzimuth = currentAzimuth;
                    //  Log.d(TAG, "onSensorChanged [" + dirtyLastAzimuth + "] Map invalidate : " + dirtyRectForLocationDirection);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onLocationChanged(final Location location) {
        if (mFollow) {
            animateToLastFix();
        } else {
            mapViewInvalidate(dirtyRectForLocationAccuracy); // redraw the my location icon
            // Log.d(TAG, "onLocationChanged [" + location + "] mapViewInvalidate : " + dirtyRectForLocationAccuracy);
        }
        // Update Bubble
        setBubbleDataWithStickyBubble(location);

        // run On first Fix
        if (!runOnFirstFix.isEmpty()) {
            for (final Runnable runnable : runOnFirstFix) {
                runOnFirstFixExecutor.execute(runnable);
            }
            runOnFirstFix.clear();
        }
    }

    // ===========================================================
    // Blink
    // ===========================================================

    private void mapViewInvalidate() {
        mMapView.postInvalidate();
    }

    private void mapViewInvalidate(Rect dirty) {
        //      mMapView.postInvalidate();
        // mMapView.invalidate(dirty);
        //mMapView.postInvalidate(dirty.left, dirty.top, dirty.right, dirty.bottom);
        mMapView.invalidateMapCoordinates(dirty);
    }

    // ===========================================================
    // Drawing On Map
    // ===========================================================

    public void animateToLastFix() {
        GeoPoint geoPoint = mLocationListener.getLastFixAsGeoPoint();
        if (geoPoint != null) {
            // mMapController.animateTo(geoPoint, animateToAnimationType);
            mMapController.setCenter(geoPoint);
            Log.d(TAG, "MyLocation animateToLastFix mapController : animateTo " + geoPoint);
        }
    }

    @Override
    public void onProviderDisabled(final String provider) {
    }

    @Override
    public void onProviderEnabled(final String provider) {
    }

    @Override
    public void onStatusChanged(final String provider, final int status, final Bundle extras) {
    }

    private void doBlink() {
        if (isMyLocationEnabled() && runOnFirstFixExecutor != null) {

            runOnFirstFixExecutor.schedule(blinkCallable, 1l, TimeUnit.SECONDS);
        } else {
            Log.w(TAG, "runOnFirstFixExecutor is null : Could not run doBlink()");
        }
    }

    public void disableThreadExecutors() {
        if (runOnFirstFixExecutor != null) {
            runOnFirstFixExecutor.shutdown();
            runOnFirstFixExecutor = null;
            DIRECTION_ARROW_SELECTED = DIRECTION_ARROW;
        }
    }

    public synchronized void enableThreadExecutors() {
        if (runOnFirstFixExecutor == null) {
            // Threads Executor ?? Executors.newSingleThreadScheduledExecutor( ); ??
            runOnFirstFixExecutor = new ScheduledThreadPoolExecutor(1, new ConfigurablePriorityThreadFactory(Thread.NORM_PRIORITY, "mylocationThread"));
            // doBlink();
        }
    }

    public boolean enableCompass(boolean enable) {
        if (enable) {
            boolean isEnable = enableCompass();
            return isEnable;
        } else {
            disableCompass();
            return true;
        }
    }

    // ===========================================================
    // Config Assessors
    // ===========================================================

    // @Override
    public boolean enableCompass() {
        boolean result = true;
        result = mOrientationListener.startListening(this);
        return result;
    }

    /**
     * Disable orientation updates
     */
    // @Override
    public void disableCompass() {
        mOrientationListener.stopListening();
    }

    // TODO Bug In null pointer in MyLocationOverlay.java:347
    public boolean enableMyLocation(boolean isEnable) {
        if (isEnable) {
            return enableMyLocation();
        } else {
            disableMyLocation();
        }
        return true;
    }

    // @Override
    public boolean enableMyLocation() {
        if (mDrawMyLocationEnabled.get()) {
            // Is already enabled
            return true;
        }
        boolean result = true;
        this.mDrawMyLocationEnabled.set(true);
        result = mLocationListener.startListening(this);
        enableCompass();

        // set initial location when enabled
        if (isFollowLocationEnabled()) {
            GeoPoint myLocGeoPoint = mLocationListener.getLastKnownLocationAsGeoPoint();
            if (myLocGeoPoint != null) {
                // mMapController.animateTo(myLocGeoPoint,
                // animateToAnimationType);
                mMapController.setCenter(myLocGeoPoint);
                Log.d(TAG, "MyLocation enableMyLocation mapController : setCenter " + myLocGeoPoint);
            }
        }

        // Run On first Fix
        runOnFirstFix.add(new Runnable() {

            @Override
            public void run() {
                Resources r = context.getResources();
                // TODO Remove is Working
                DIRECTION_ARROW = BitmapFactory.decodeResource(context.getResources(), R.drawable.vm_chevron_off);
                DIRECTION_ARROW_CENTER_X = DIRECTION_ARROW.getWidth() / 2;
                DIRECTION_ARROW_CENTER_Y = DIRECTION_ARROW.getHeight() / 2;
                // For Blink bitmap
                DIRECTION_ARROW_ON = BitmapFactory.decodeResource(context.getResources(), R.drawable.vm_chevron_on);

                // Switch Drawable
                Drawable onDrawable = r.getDrawable(R.drawable.vm_chevron_on);
                Drawable offDrawable = r.getDrawable(R.drawable.vm_chevron_off);
                blinkDrawable.setChangeBlinkDrawable(onDrawable, offDrawable);
            }
        });
        // Blink
        doBlink();

        // Invalidate
        mapViewInvalidate();
        return result;
    }

    /**
     * Disable location updates
     */
    // @Override
    public void disableMyLocation() {
//        if (!mDrawMyLocationEnabled.get()) {
//            return  ;
//        }
        disableCompass();
        mLocationListener.stopListening();
        this.mDrawMyLocationEnabled.set(false);
        // Cancel Blink

        // Invalidate
        mapViewInvalidate();
    }

    public boolean isMyLocationEnabled() {
        if (mLocationListener != null) {
            return mLocationListener.isMyLocationEnabled();
        }
        return false;
    }

    // ===========================================================
    // Sensor Assessors
    // ===========================================================

    /**
     * Enables "follow" functionality. The map will center on your current
     * location and automatically scroll as you move. Scrolling the map in the
     * UI will disable.
     */
    public void enableFollowLocation() {
        mFollow = true;
        // set initial location when enabled
        if (isMyLocationEnabled()) {
            GeoPoint lastGeoPoint = mLocationListener.getLastFixAsGeoPoint();
            if (lastGeoPoint != null) {
                // mMapController.animateTo( lastGeoPoint,
                // animateToAnimationType);
                mMapController.setCenter(lastGeoPoint);
                Log.d(TAG, "MyLocation enableFollowLocation mapController : animateTo " + lastGeoPoint);
            }
        }

        // Update the screen to see changes take effect
        // mapViewInvalidate();
    }

    /**
     * Disables "follow" functionality.
     */
    public void disableFollowLocation() {
        mFollow = false;
    }

    public boolean isFollowLocationEnabled() {
        return mFollow;
    }

    // @Override
    public boolean runOnFirstFix(final Runnable runnable) {
        if (mLocationListener != null && mLocationListener.isFixLocation()) {
            if (runOnFirstFixExecutor != null) {
                runOnFirstFixExecutor.execute(runnable);
            } else {
                Log.w(TAG, "runOnFirstFixExecutor is null : Could not run runOnFirstFix()");
            }
            return true;
        } else {
            runOnFirstFix.addLast(runnable);
            return false;
        }
    }

    // private int getDisplayRotation2() {
    // int AXIS_X, AXIS_Y;
    // int x = AXIS_X;
    // int y = AXIS_Y;
    // switch (mDisplay.getRotation()) { // .getOrientation()
    // case Surface.ROTATION_0: break;
    // case Surface.ROTATION_90: x = AXIS_Y; y= AXIS_MINUS_X;break;
    // case Surface.ROTATION_180: y = AXIS_MINUS_Y;break;
    // case Surface.ROTATION_270: x =AXIS_MINUS_Y; y=AXIS_X;break;
    // default: break;
    // };
    // SensorManager.remapCoordinateSystem(inR, X, Y, outR)
    // return ang;
    // }

    public boolean isCompassEnabled() {
        return mDrawCompassEnabled;
    }

    public boolean isGpsLocationProviderIsEnable() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    public GeoPoint getLastKnownLocationAsGeoPoint() {
        return mLocationListener.getLastKnownLocationAsGeoPoint();
    }
    // ===========================================================
    // Drawing Location On Map
    // ===========================================================

    public GeoPoint getLastFixAsGeoPoint() {
        return mLocationListener.getLastFixAsGeoPoint();
    }

    private int getDisplayRotation() {
        int ang = 0;
        switch (mDisplay.getRotation()) { // .getOrientation()
            case Surface.ROTATION_0:
                break;
            case Surface.ROTATION_90:
                ang = 90;
                break;
            case Surface.ROTATION_180:
                ang = 180;
                break;
            case Surface.ROTATION_270:
                ang = 270;
                break;
            default:
                break;
        }
        return ang;
    }

    // ===========================================================
    // Drawing Compass
    // ===========================================================

    public int getAzimuth() {
        return mOrientationListener.getAzimuth();
    }

    // ===========================================================
    // Motion Event Management
    // ===========================================================

    public int getDisplayAzimuth() {
        return getDisplayAzimuth(getAzimuth());
    }

    public int getDisplayAzimuth(int brutAzimth) {
        return brutAzimth + getDisplayRotation();
    }

    @Override
    protected void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow) {
            return;
        }
        Location location = mLocationListener.getLastFix();
        if (mDrawMyLocationEnabled.get() && location != null) {
            drawMyLocation(canvas, mapView, location, mLocationListener.getLastFixAsGeoPoint());
        }
        //
        if (mDrawCompassEnabled) {
            int azimuth = getDisplayAzimuth();
            drawCompass(canvas, mapView, azimuth);
        }
    }

    protected void drawMyLocation(final Canvas canvas, final MapView mapView, final Location lastFix, final GeoPoint myLocation) {

        final Projection pj = mapView.getProjection();
        pj.toMapPixels(myLocation, mMapCoords);

        // Draw Accuracy
        if (mDrawAccuracyEnabled) {
            final float groundResolutionInM = (float) TileSystem.GroundResolution(lastFix.getLatitude(), mapView.getZoomLevel());
            final float radius = lastFix.getAccuracy() / groundResolutionInM;
            canvas.drawCircle(mMapCoords.x, mMapCoords.y, radius, mCirclePaint);
            canvas.drawCircle(mMapCoords.x, mMapCoords.y, radius, mCirclePaintBorder);
            // Record dirty Box
            int radiusAsInt = ((int) radius) + 1;
            dirtyRectForLocationAccuracy.set(mMapCoords.x - radiusAsInt, mMapCoords.y - radiusAsInt, mMapCoords.x + radiusAsInt, mMapCoords.y + radiusAsInt);
        }

        if (DEBUGMODE) {
            final Matrix mMatrix = new Matrix();
            final float[] mMatrixValues = new float[9];
            canvas.getMatrix(mMatrix);
            mMatrix.getValues(mMatrixValues);
            final float tx = (-mMatrixValues[Matrix.MTRANS_X] + 20) / mMatrixValues[Matrix.MSCALE_X];
            final float ty = (-mMatrixValues[Matrix.MTRANS_Y] + 90) / mMatrixValues[Matrix.MSCALE_Y];
            int hPos = 15;
            canvas.drawText("Lat: " + lastFix.getLatitude(), tx, ty + 5 + hPos, mPaint);
            canvas.drawText("Lon: " + lastFix.getLongitude(), tx, ty + 20 + hPos, mPaint);
            canvas.drawText("Alt: " + lastFix.getAltitude(), tx, ty + 35 + hPos, mPaint);
            canvas.drawText("Acc: " + lastFix.getAccuracy(), tx, ty + 50 + hPos, mPaint);
            canvas.drawText("Pro: " + lastFix.getProvider(), tx, ty + 65 + hPos, mPaint);
            canvas.drawText("Azi: " + mOrientationListener.getAzimuth(), tx, ty + 80 + hPos, mPaint);
        }

        // Draw marker
        int azimuth = getDisplayAzimuth();
        // azimuth = (azimuth + 180) % 359;
        directionRotater.setRotate(azimuth, DIRECTION_ARROW_CENTER_X, DIRECTION_ARROW_CENTER_Y);
        directionRotater.postTranslate(-DIRECTION_ARROW_CENTER_X, -DIRECTION_ARROW_CENTER_Y);
        directionRotater.postTranslate(mMapCoords.x, mMapCoords.y);
        canvas.drawBitmap(DIRECTION_ARROW_SELECTED, directionRotater, mPaint);
        // Copute Dirty Rex
        dirtyRectForLocationDirection.offsetTo(mMapCoords.x - DIRECTION_ARROW_CENTER_X - DIRECTION_ARROW_CENTER_X, mMapCoords.y - DIRECTION_ARROW_CENTER_Y - DIRECTION_ARROW_CENTER_Y);

        //TODO    blinkDrawable.draw(canvas);adb

        // Debug
        if (DEBUGMODE) {
            canvas.drawCircle(mMapCoords.x, mMapCoords.y, 5, mPaint);
            Rect hitTestRecr = new Rect();
            hitTestRecr.set(-DIRECTION_ARROW_CENTER_X, -DIRECTION_ARROW_CENTER_Y, DIRECTION_ARROW_CENTER_X, DIRECTION_ARROW_CENTER_Y);
            hitTestRecr.offset(mMapCoords.x, mMapCoords.y);
            canvas.drawRect(hitTestRecr, mPaint);
            // Dirty Box
            canvas.drawRect(dirtyRectForLocationAccuracy, mPaint);
            canvas.drawRect(dirtyRectForLocationDirection, mPaint);

        }

    }

    protected void drawCompass(final Canvas canvas, final MapView mapView, final float bearing) {
        // Screen Limit
        final Projection projection = mapView.getProjection();
        Rect screenRect = projection.getScreenRect();

        // Position of COmpass
        final int centerX = screenRect.left + mCompassCenterX;
        final int centerY = screenRect.top + mCompassCenterY;

        // Rotate Bitmap
        mCompassMatrix.setRotate(-bearing, COMPASS_ROSE_CENTER_X, COMPASS_ROSE_CENTER_Y);
        mCompassMatrix.postTranslate(-COMPASS_ROSE_CENTER_X, -COMPASS_ROSE_CENTER_Y);
        mCompassMatrix.postTranslate(centerX, centerY);

        canvas.drawBitmap(mCompassRose, mCompassMatrix, mPaint);


        // Debug
        if (DEBUGMODE) {
            Rect hitTestRecr = new Rect();
            hitTestRecr.set(-COMPASS_ROSE_CENTER_X, -COMPASS_ROSE_CENTER_Y, COMPASS_ROSE_CENTER_X, COMPASS_ROSE_CENTER_Y);
            hitTestRecr.offset(centerX, centerY);
            canvas.drawRect(hitTestRecr, mPaint);
        }
    }

    // ===========================================================
    // Motion Event Management
    // ===========================================================
    // https://github.com/cyrilmottier/Polaris/blob/master/library/src/com/cyrilmottier/polaris/PolarisMapView.java

    @Override
    public boolean onTouchEvent(final MotionEvent event, final MapView mapView) {
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            disableFollowLocation();
        }

        return super.onTouchEvent(event, mapView);
    }

    @Override
    public boolean onSingleTapUp(final MotionEvent event, final MapView mapView) {
        final Location lastFix = mLocationListener.getLastFix();
        if (lastFix != null) {
            GeoPoint lastFixAsGeoPoint = mLocationListener.getLastKnownLocationAsGeoPoint();
            if (isTapOnFixLocation(event, mapView, lastFixAsGeoPoint)) {
                Log.d(TAG, "onSingleTapUp on myLocation Point");

                if (balloonView == null) {
                    Log.d(TAG, "onSingleTapUp : Create new MyLocationBubble");
                    balloonView = new MyLocationBubble(mapView.getContext());
                    boolean isDisplayGeoLoc = sharedPreferences.getBoolean(AppConstants.PREFS_KEY_MYLOCATION_DISPLAY_GEOLOC, false);
                    balloonView.setDisplayGeoLoc(isDisplayGeoLoc);
                    balloonView.setVisibility(View.GONE);
                    // Todo add click listener
                }
//              TODO   showCallout();
                boolean balloonViewNotVisible = (View.VISIBLE != balloonView.getVisibility());
                Log.d(TAG, "onSingleTapUp my location balloon is NOT visible : " + balloonViewNotVisible + " (" + balloonView.getVisibility() + ") on geoPoint " + lastFixAsGeoPoint);
                if (balloonViewNotVisible) {

                    // Position Layout
                    boolean isRecycled = false;// balloonViewLayoutParams != null;
                    balloonViewLayoutParams = createBubbleLayoutParams( lastFixAsGeoPoint );
                    if (isRecycled) {
                        balloonView.setLayoutParams(balloonViewLayoutParams);
                        Log.d(TAG, "onSingleTapUp : setLayoutParams " + balloonViewLayoutParams);

                    } else {
                        mapView.addView(balloonView, balloonViewLayoutParams);
                        balloonView.setLayoutParams(balloonViewLayoutParams);
                        Log.d(TAG, "onSingleTapUp : addView " + balloonViewLayoutParams);
                    }
                    balloonView.setVisibility(View.VISIBLE);
                    mapView.bringChildToFront(balloonView);

                    // balloonView.setData(lastFix);
                    setBubbleData(lastFix);

                    printMapViewBuuble(  mapView);

                    return true;
                } else {
                    return hideBubble(mapView);
                }
            } else {
                hideBubble(mapView);
            }
        }
        return false;
    }


    private boolean hideBubble(MapView mapView) {
        boolean isHide = false;
        //if (balloonView != null && View.GONE != balloonView.getVisibility()) {
        if (balloonView != null ) {
            balloonView.setVisibility(View.GONE);
            //Remove from stack
            mapView.removeView(balloonView);
            balloonView = null;
            balloonViewLayoutParams = null;
            isHide = true;
            printMapViewBuuble(  mapView);

        }
        return isHide;
    }


    private void printMapViewBuuble(MapView mapView) {
        int chidlCount = mapView.getChildCount();
        Log.d(TAG, "mapView Child "  + chidlCount + " / isvisible : " + (balloonView==null ? "null" : ""+ (balloonView.getVisibility() == View.VISIBLE) ) );
        for (int i=0; i< chidlCount ; i++) {
            Log.d(TAG, "mapView Child " + (i+1) + "/"+chidlCount + " : " + mapView.getChildAt(i));
        }
    }


    private MapView.LayoutParams createBubbleLayoutParams(  GeoPoint lastFixAsGeoPoint) {
        // Compute Offset
        int offsetX = 0; // 150
        int offsetY = -20; // -20
        // Position Layout
        MapView.LayoutParams result = balloonViewLayoutParams;
        if (balloonViewLayoutParams==null) {
              result =  new MapView.LayoutParams(MapView.LayoutParams.WRAP_CONTENT, MapView.LayoutParams.WRAP_CONTENT,
                lastFixAsGeoPoint, MapView.LayoutParams.BOTTOM_CENTER,
                offsetX, offsetY);
        } else {
            balloonViewLayoutParams.geoPoint = lastFixAsGeoPoint;
        }

        return result;
    }


    private void setBubbleDataWithStickyBubble(final Location lastFix) {
        setBubbleData(lastFix, false);
        // Move Bubble
        GeoPoint lastFixAsGeoPoint = mLocationListener.getLastKnownLocationAsGeoPoint();
        // Check Bubble position
        MapView.LayoutParams balloonViewLayoutParams = this.balloonViewLayoutParams;
        MyLocationBubble balloonView = this.balloonView;
        if (lastFixAsGeoPoint != null && balloonViewLayoutParams != null && balloonView != null) {
           balloonViewLayoutParams = createBubbleLayoutParams( lastFixAsGeoPoint );
            balloonView.setLayoutParams(balloonViewLayoutParams);
        }
    }

    private void setBubbleData(final Location lastFix) {
        setBubbleData(lastFix, true);
    }
    private void setBubbleData(final Location lastFix, boolean doGeocoding) {
        if (balloonView != null && View.VISIBLE == balloonView.getVisibility()) {
            balloonView.setData(lastFix);
            if (doGeocoding) {
                setBubbleDataGeocodingData(lastFix);
            }
        }
    }

    private void setBubbleDataGeocodingData(final Location lastFix) {
        Runnable geocoderTask = new Runnable() {

            @Override
            public void run() {
                try {
                    if (geocoder != null) {
                        List<Address> addresses = geocoder.getFromLocation(lastFix.getLatitude(), lastFix.getLongitude(), 1);
                        if (addresses != null && !addresses.isEmpty()) {
                            final Address addr = addresses.get(0);
                            Message msg = uiHandler.obtainMessage(UI_MSG_SET_ADDRESS, addr);
                            uiHandler.sendMessage(msg);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "MyLocation Geocoder Error : " + e.getMessage());
                }

            }
        };

        if (runOnFirstFixExecutor != null) {
            runOnFirstFixExecutor.execute(geocoderTask);
        } else {
            Log.w(TAG, "runOnFirstFixExecutor is null : Could not run setBubbleData()");
        }
    }


//    private OnAnnotationSelectionChangedListener mOnAnnotationSelectionChangedListener;
//    
//    private final OnClickListener mOnClickListener = new OnClickListener() {
//        @Override
//        public void onClick(View v) {
//            if (mOnAnnotationSelectionChangedListener != null) {
//                final int annotationPosition = getSelectedAnnotationPosition();
//                final Annotation annotation = getSelectedAnnotation();
//                if (annotation != null && annotationPosition != INVALID_POSITION) {
//                    // @formatter:off
//                    mOnAnnotationSelectionChangedListener.onAnnotationClicked(context, (MapCalloutView) v, annotationPosition, annotation);
//                    // @formatter:on
//                }
//            }
//        }
//    };

    private boolean isTapOnFixLocation(final MotionEvent event, final MapView mapView, final GeoPoint lastFixAsGeoPoint) {
        if (lastFixAsGeoPoint != null) {
            // Test for hit point in MyLocation
            Projection pj = mapView.getProjection();
            // LastFix Location to Screen Coords
            pj.toMapPixels(lastFixAsGeoPoint, tapPointScreenCoords);
            // Tested Box for LastFix
            tapPointHitTestRect.set(-DIRECTION_ARROW_CENTER_X, -DIRECTION_ARROW_CENTER_Y, DIRECTION_ARROW_CENTER_X, DIRECTION_ARROW_CENTER_Y);
            tapPointHitTestRect.offset(tapPointScreenCoords.x, tapPointScreenCoords.y);
            // Tap Point
            IGeoPoint tapPoint = pj.fromPixels(event.getX(), event.getY());
            pj.toMapPixels(tapPoint, tapPointScreenCoords);
            // Test If On Containt the other
            return tapPointHitTestRect.contains(tapPointScreenCoords.x, tapPointScreenCoords.y);
        }
        return false;
    }

    private MapCalloutView getMapCallout(int index) {
        if (mMapCallouts[index] == null) {
            mMapCallouts[index] = new MapCalloutView(context);
            mMapCallouts[index].setVisibility(View.GONE);
//            mMapCallouts[index].setOnClickListener(mOnClickListener);
            mMapCallouts[index].setOnDoubleTapListener(mOnDoubleTapListener);
        }
        return mMapCallouts[index];
    }

    private MapCalloutView getCurrentMapCallout() {
        return getMapCallout(mMapCalloutIndex);
    }

    private MapCalloutView getNextMapCallout() {
        if (mMapCalloutIndex == INDEX_FIRST) {
            mMapCalloutIndex = INDEX_SECOND;
        } else {
            mMapCalloutIndex = INDEX_FIRST;
        }
        return getMapCallout(mMapCalloutIndex);
    }

    public void showCallout() {
        final Location lastFix = mLocationListener.getLastFix();
        if (lastFix != null) {
            GeoPoint lastFixAsGeoPoint = mLocationListener.getLastKnownLocationAsGeoPoint();
            final MapCalloutView mapCalloutView = getNextMapCallout();
            int markerHeight = 20; // marker.getBounds().height();
            mapCalloutView.setMarkerHeight(markerHeight);
            // TODO Set Data
//            mapCalloutView.setData(lastFix);
            mapCalloutView.setTitle(getLoacationAsDataTitle(lastFix));
            mapCalloutView.setSubtitle(getLoacationAsDataSubtitle(lastFix));
            if (mapCalloutView.hasDisplayableContent()) {
                mapCalloutView.show(mMapView, lastFixAsGeoPoint, true);
            }

        }
    }

    private String getLoacationAsDataTitle(Location lastFix) {
        return lastFix.getProvider();
    }

    private String getLoacationAsDataSubtitle(Location location) {
        StringBuilder sb = new StringBuilder(128);
        // Location
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        String coordString = String.format(Locale.US, "(%.6f, %.6f)", lat, lng);
        sb.append(coordString);
        sb.append("\n");
        // Accuracy
        String accuracy = String.format("%s m", (int) location.getAccuracy());
        sb.append(accuracy);
        return sb.toString();
    }
}
