package cloudcity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.List;

public class GPSMonitor {
    private static final String TAG = GPSMonitor.class.getSimpleName();
    private static final long GPS_POLLING_SPEED_IN_MS = 1000L;//10L; //was 1000L
    private static final long GPS_POLLING_MIN_DIST_IN_M = 0L; //1L; //was 0

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Context context;

    public GPSMonitor(Context context) {
        this.context = context;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                // Handle location update
                Log.d(TAG, "Latitude: " + location.getLatitude() + ", Longitude: " + location.getLongitude());
                if (location.hasSpeed()) {
                    Log.d(TAG, "Location has speed! speed: "+location.getSpeed());
                    if (location.hasSpeedAccuracy()) {
                        Log.d(TAG, "Location has speed accuracy! speed: "+location.getSpeedAccuracyMetersPerSecond());
                    }
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };

        Log.d(TAG, "GPSMonitor initialized!");
    }

    public void startMonitoring() {
        Log.d(TAG, "--> startMonitoring()");
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request permissions if not granted
            Log.e(TAG, "GPS Permissions not granted! fix this");
            return;
        }
        List<String> allGpsProviders = locationManager.getAllProviders();
        Log.d(TAG, "all GPS providers: "+allGpsProviders);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_POLLING_SPEED_IN_MS, GPS_POLLING_MIN_DIST_IN_M, locationListener);
        Log.d(TAG, "<-- startMonitoring()");
    }

    public void stopMonitoring() {
        locationManager.removeUpdates(locationListener);
    }
}
