package cloudcity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.List;
import java.util.Optional;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import cloudcity.dataholders.TimerWrapper;

/**
 * Class for monitoring GPS location and speed via {@link LocationManager}, monitoring if the speed
 * is under threshold {@link #THRESHOLD_VALUE} for minimum threshold millis {@link #THRESHOLD_DURATION},
 * firing callbacks via {@link ValueMonitorCallback} when that condition has been met.
 */
public class GPSMonitor {
    private static final String TAG = GPSMonitor.class.getSimpleName();
    private static final long GPS_POLLING_SPEED_IN_MS = 1000L;
    private static final long GPS_POLLING_MIN_DIST_IN_M = 0L;

    /**
     * The threshold value we need to be under to start the Iperf3 test
     */
    private static final int THRESHOLD_VALUE = 5;
    /**
     * How long do we need to be under threshold to fire a callback
     */
    private static final int THRESHOLD_DURATION = 5000;
    /**
     * How often to poll the value, in milliseconds<p>
     * Try to keep it faster then {@link #GPS_POLLING_SPEED_IN_MS}
     */
    private static final int VALUE_MONITOR_INTERVAL = 500;


    private LocationManager locationManager;
    private LocationListener locationListener;
    private Context context;
    private ValueMonitor valueMonitor;

    private static volatile float lastSpeed;
    /**
     * This is the 'last' current location received from {@link LocationManager}
     */
    private static volatile Location lastLocation;

    /**
     * This is the previous location received from the {@link LocationManager}. It's the {@link #lastLocation}'s previous
     * value which is used to check how much (far) we've moved recently
     */
    private static volatile Location previousLocation;
    private volatile AtomicLong timeUnderThreshold = new AtomicLong(0);

    private static volatile GPSMonitor instance;

    public static synchronized void initialize(Context context) {
        if (instance == null) {
            synchronized (GPSMonitor.class) {
                if (instance == null) {
                    instance = new GPSMonitor();
                    instance.context = context;
                    instance.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                    instance.locationListener = new LocationListener() {
                        @Override
                        public void onLocationChanged(Location location) {
                            // Handle location update
                            Log.d(TAG, "Latitude: " + location.getLatitude() + ", Longitude: " + location.getLongitude());
                            setLastLocation(location);
                            if (location.hasSpeed()) {
                                Log.d(TAG, "Location has speed! speed: " + location.getSpeed());
                                if (location.hasSpeedAccuracy()) {
                                    Log.d(TAG, "Location has speed accuracy! speed: " + location.getSpeedAccuracyMetersPerSecond());
                                }
                                lastSpeed = location.getSpeed();
                            }
                        }

                        @Override
                        public void onStatusChanged(String provider, int status, Bundle extras) {
                        }

                        @Override
                        public void onProviderEnabled(String provider) {
                        }

                        @Override
                        public void onProviderDisabled(String provider) {
                        }
                    };

                    // Initialize lastLocation to something bogus
                    // We won't use setLastLocation() because we don't want this value to end up
                    // in the 'previousLocation'
                    lastLocation = new Location("null");
                    lastLocation.reset();

                    instance.requestCurrentLocation();

                    Log.d(TAG, "GPSMonitor initialized!");
                }
            }
        } else {
            throw new IllegalStateException("GPSMonitor was already initialized! Must not call before calling shutdown()");
        }
    }

    public static GPSMonitor getInstance() {
        if (instance != null) {
            return instance;
        } else {
            throw new IllegalStateException("GPSMonitor not initialized! Must call initialize() first!");
        }
    }

    public static void shutdown() {
        if (instance != null) {
            instance.stopMonitoring();
            instance = null;
        } else {
            Log.w(TAG, "GPSMonitor instance is already null during shutdown.");
        }
    }

    /**
     * Returns last location observed during monitoring
     *
     * @return last location or rather {@link #lastLocation}
     */
    public static Location getLastLocation() {
        return lastLocation;
    }

    /**
     * Utility function for {@link Location#distanceTo(Location)} but wrapped in an {@link Optional}
     * If any of the two locations are null, the {@code Location.distanceTo} requires both of them
     * to be non-null, and an empty Optional is returned. Otherwise, an Optional containing the
     * distance is returned.
     *
     * @param location1 location to measure from
     * @param location2 location to measure to
     *
     * @return {@link Optional#empty()} if any of the two locations is null or {@link Optional#of(Object)} their distance ({@code location1.distanceTo(location2)})
     */
    public static Optional<Float> calculateDistance(@NonNull Location location1, @NonNull Location location2) {
        if (location1 != null && location2 != null) {
            return Optional.of(location1.distanceTo(location2));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Calculates distance between {@link #previousLocation} and {@link #lastLocation} and returns an {@link Optional}
     *
     * @return an empty Optional if either of the two are null, or an Optional containing an actual value
     */
    public static Optional<Float> calculateDistanceBetweenLastTwoLocations() {
        return calculateDistance(lastLocation, previousLocation);
    }

    /**
     * Start monitoring for GPS location changes, and turn on the internal {@link ValueMonitor}
     * and assign it a {@link ValueMonitorCallback} to call {@link Iperf3Monitor#startDefault15secTest()}
     */
    public void startMonitoring() {
        Log.d(TAG, "--> startMonitoring()");
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request permissions if not granted
            Log.e(TAG, "GPS Permissions not granted! fix this");
            return;
        }
        List<String> allGpsProviders = locationManager.getAllProviders();
        Log.d(TAG, "all GPS providers: " + allGpsProviders);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_POLLING_SPEED_IN_MS, GPS_POLLING_MIN_DIST_IN_M, locationListener);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, GPS_POLLING_SPEED_IN_MS, GPS_POLLING_MIN_DIST_IN_M, locationListener);
        locationManager.requestLocationUpdates(LocationManager.FUSED_PROVIDER, GPS_POLLING_SPEED_IN_MS, GPS_POLLING_MIN_DIST_IN_M, locationListener);

        Log.d(TAG, "Starting speed polling task...");

        valueMonitor = new ValueMonitor();
        valueMonitor.setCallback(() -> {
            Iperf3Monitor.getInstance().startDefault15secTest(lastLocation);
        });
        valueMonitor.startMonitoring();

        Log.d(TAG, "<-- startMonitoring()");
    }

    /**
     * Stop monitoring GPS and speed updates,
     * remove {@link #locationListener} from the {@link LocationManager} and call
     * {@link ValueMonitor#stopMonitoring()}
     */
    public void stopMonitoring() {
        locationManager.removeUpdates(locationListener);
        if (valueMonitor != null) {
            valueMonitor.stopMonitoring();
        }
    }

    public void requestCurrentLocation() {
        boolean lacksFineLocationPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
        boolean lacksCoarseLocationPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;
        if (lacksFineLocationPermission || lacksCoarseLocationPermission) {
            Log.wtf(TAG, "We lack ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION permissions!");
            return;
        }

        Executor executor = Executors.newSingleThreadExecutor();
        Consumer<Location> locationConsumer = new Consumer<Location>() {
            @Override
            public void accept(Location location) {
                Log.d(TAG, "Current location is: " + location);
                lastLocation = location;
                if (location != null) {
                    Log.d(TAG, "Current location's LAT: " + location.getLatitude() + ", LNG: " + location.getLongitude());
                } else {
                    Log.w(TAG, "Cannot get current location's (LAT,LNG) because location was NULL");
                    // It's better to have a bogus dummy location with (LAT,LNG) as (0,0) than a null location
                    Location bogusLocation = new Location("null");
                    bogusLocation.reset();
                    lastLocation = bogusLocation;
                }
            }
        };

        locationManager.getCurrentLocation(
                LocationManager.NETWORK_PROVIDER,
                null,   //CancellationSignal
                executor,
                locationConsumer
        );
        locationManager.getCurrentLocation(
                LocationManager.GPS_PROVIDER,
                null,   //CancellationSignal
                executor,
                locationConsumer
        );
        locationManager.getCurrentLocation(
                LocationManager.FUSED_PROVIDER,
                null,   //CancellationSignal
                executor,
                locationConsumer
        );
    }

    public static void setLastLocation(Location newLocation) {
        previousLocation = lastLocation;
        lastLocation = newLocation;
    }

    private class ValueMonitor {
        private static final String TAG = "ValueMonitor";

        private final TimerWrapper timer;
        /**
         * Callback that will be invoked when the monitored value has been under {@link #THRESHOLD_VALUE}
         * for at least {@link #THRESHOLD_DURATION} millis
         */
        private @Nullable ValueMonitorCallback callback;

        public ValueMonitor() {
            // Start the timer as a daemon thread
            timer = new TimerWrapper(true);
        }

        /**
         * Set or clear the {@link #callback} parameter
         *
         * @param callback the new callback to set
         */
        public void setCallback(@Nullable ValueMonitorCallback callback) {
            this.callback = callback;
        }

        /**
         * Start a new {@link TimerTask} on the {@link #timer} which will call {@link #monitorValue()}
         * every {@link #VALUE_MONITOR_INTERVAL}
         */
        public void startMonitoring() {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    monitorValue();
                }
            }, 0, VALUE_MONITOR_INTERVAL);
        }

        /**
         * Stops monitoring and calls {@link #timer}'s {@link TimerWrapper#stop()}
         */
        public void stopMonitoring() {
            timer.stop();
        }

        /**
         * Logic to monitor the value and track the time under threshold
         */
        private void monitorValue() {
            if (lastSpeed < THRESHOLD_VALUE) {
                // If value is under threshold, increment the time under threshold
                timeUnderThreshold.addAndGet(VALUE_MONITOR_INTERVAL);

                // Check if the time under threshold exceeds 5 seconds
                if (timeUnderThreshold.get() >= THRESHOLD_DURATION) {
                    Log.d(TAG, "value has been under threshold for " + timeUnderThreshold + "ms, firing callback");
                    // Trigger the callback if the condition is met
                    if (callback != null) {
                        callback.onUnderThresholdValueForAtLeastThresholdDuration();
                    }

                    // Reset the time tracking after triggering callback
                    timeUnderThreshold.set(0);
                }
            } else {
                // If the value is above the threshold, reset the time tracking
                timeUnderThreshold.set(0);
            }
        }
    }

    /**
     * Callback interface for the value monitoring
     */
    interface ValueMonitorCallback {
        /**
         * Callback to be invoked when the speed has been under {@link #THRESHOLD_VALUE}
         * for at least {@link #THRESHOLD_DURATION} millis
         */
        void onUnderThresholdValueForAtLeastThresholdDuration();
    }
}
