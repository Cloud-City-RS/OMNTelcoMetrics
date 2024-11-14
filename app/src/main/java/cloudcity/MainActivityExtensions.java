package cloudcity;

import static cloudcity.CloudCityConstants.CLOUD_CITY_CC_LOGGING;
import static cloudcity.CloudCityConstants.CLOUD_CITY_GENERAL_LOGGING;
import static cloudcity.CloudCityConstants.CLOUD_CITY_SERVER_URL;
import static cloudcity.CloudCityConstants.CLOUD_CITY_TOKEN;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import cloudcity.util.CloudCityUtil;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.DataProvider;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SPType;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SharedPreferencesGrouper;

public class MainActivityExtensions {
    private static final String TAG = "MainActivityExtensions";
    private static GPSMonitor gpsMonitor;

    /**
     * We will be using the 'logging' shared pref since that's the only one that is displayed in the logging settings fragment...
     *
     * @param activityContext the Activity that will be used as host for requesting permissions
     * @param spg the {@link SharedPreferencesGrouper} on which we'll set the listener and from which we'll grab the logging shared pref
     * @param dp the {@link DataProvider} which will be used to refresh all 'data' necessary for the CC server updates
     */
    public static void performMainActivityThing(Activity activityContext, SharedPreferencesGrouper spg, DataProvider dp) {
        // First, set a listener that will trigger when the actual CC logging changes
        spg.setListener((sharedPreferences, key) -> {
            if (key != null && !key.isEmpty() && !CloudCityUtil.isBlank(key)) {
                switch (key) {
                    case CLOUD_CITY_CC_LOGGING: {
                        // This might cause a double refresh with the thing in LoggingServiceExtensions but... oh well.
                        dp.refreshAll();
                    }
                    break;

                    case CLOUD_CITY_SERVER_URL: {
                        String newSharedPrefValue = sharedPreferences.getString(key, "");
                        CloudCityParamsRepository.getInstance().setServerUrl(newSharedPrefValue);
                    }
                    break;

                    case CLOUD_CITY_TOKEN: {
                        String newSharedPrefValue = sharedPreferences.getString(key, "");
                        CloudCityParamsRepository.getInstance().setServerToken(newSharedPrefValue);
                    }
                    break;
                }
            }
        }, SPType.logging_sp);
        // Now perform the other stuff, and among other things, change the CC logging
        performMainActivityThing2(TAG, spg.getSharedPreference(SPType.logging_sp));

        // We can't get rid of the listener anymore, because the URL/token part of it ensures
        // there will never be a discrepancy between what's stored in the SharedPrefs and what's
        // in the actual repository.

        // Finally, check the permission; and turn on GPS monitoring if we have the permissions
        if (ActivityCompat.checkSelfPermission(activityContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Requesting ACCESS_BACKGROUND_LOCATION Permission");
            ActivityCompat.requestPermissions(activityContext, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 3);
        } else {
            // We have the BACKGROUND_LOCATION permission, can start GPS logging
            startGPSMonitoring(activityContext.getApplicationContext());
        }
    }

    public static void performMainActivityThing2(String TAG, SharedPreferences sp) {
        /* Handle default values for Cloud City URL and token */
        String address = sp.getString(CLOUD_CITY_SERVER_URL, "");
        String token = sp.getString(CLOUD_CITY_TOKEN, "");

        if (address.isEmpty()) {
            Log.d(TAG, "onCreate: Cloud city address not set, setting default");
            sp.edit().putString(CLOUD_CITY_SERVER_URL, CloudCityParamsRepository.getInstance().getServerUrl()).commit();
        }

        if (token.isEmpty()) {
            Log.d(TAG, "onCreate: Cloud city token not set, setting default");
            sp.edit().putString(CLOUD_CITY_TOKEN, CloudCityParamsRepository.getInstance().getServerToken()).commit();
        }
    }

    public static void turnOnCloudCityLoggingAfterPermissionsGranted(SharedPreferencesGrouper spg) {
        // Finally, turn on CloudCity logging by default
        Log.d(TAG, "Turning ON cloudcity logging");
        spg
                .getSharedPreference(SPType.logging_sp)
                .edit()
                .putBoolean(CLOUD_CITY_GENERAL_LOGGING, true)
                .putBoolean(CLOUD_CITY_CC_LOGGING, true)
                .commit();
    }

    public static void startGPSMonitoring(Context applicationContext) {
        // GPSMonitor
        Log.d(TAG, "Initializing GPSMonitor");
        GPSMonitor.initialize(applicationContext);
        gpsMonitor = GPSMonitor.getInstance();
        gpsMonitor.startMonitoring();
    }

    /**
     * TODO SHARK Figure out how to make this one work
     */
    /*
    public static void startListeningToIperf3ResultsAndUploadOnSuccess() {
        Log.d(TAG, "--> startListeningToIperf3ResultsAndUploadOnSuccess()");
        iperf3Monitor = Iperf3Monitor.getInstance();

        iperf3Monitor.startListeningForIperf3Updates(new Iperf3Monitor.Iperf3MonitorCompletionListener() {
            @Override
            public void onIperf3TestCompleted(MetricsPOJO metrics) {
                Log.wtf(TAG, "One iperf3 cycle is completed! received metrics: "+metrics);
                DataProvider dp = GlobalVars.getInstance().get_dp();
                boolean iperf3SendingResult = CloudCityUtil.sendIperf3Data(metrics, dp.getLocation());
                Log.wtf(TAG, "sending iperf3 metrics result: "+iperf3SendingResult);
            }
        });
        Log.d(TAG, "<-- startListeningToIperf3ResultsAndUploadOnSuccess()");
    }
     */
}
