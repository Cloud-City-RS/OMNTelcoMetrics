package cloudcity;

import static cloudcity.CloudCityConstants.CLOUD_CITY_CC_LOGGING;
import static cloudcity.CloudCityConstants.CLOUD_CITY_GENERAL_LOGGING;
import static cloudcity.CloudCityConstants.CLOUD_CITY_SERVER_URL;
import static cloudcity.CloudCityConstants.CLOUD_CITY_TOKEN;

import android.content.SharedPreferences;
import android.util.Log;

import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.DataProvider;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SPType;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SharedPreferencesGrouper;

public class MainActivityExtensions {

    /**
     * We will be using the 'logging' shared pref since that's the only one that is displayed in the logging settings fragment...
     *
     * @param TAG the log tag used for logging
     * @param spg the {@link SharedPreferencesGrouper} on which we'll set the listener and from which we'll grab the logging shared pref
     * @param dp the {@link DataProvider} which will be used to refresh all 'data' necessary for the CC server updates
     */
    public static void performMainActivityThing(String TAG, SharedPreferencesGrouper spg, DataProvider dp) {
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
        spg
                .getSharedPreference(SPType.logging_sp)
                .edit()
                .putBoolean(CLOUD_CITY_GENERAL_LOGGING, true)
                .putBoolean(CLOUD_CITY_CC_LOGGING, true)
                .commit();
    }
}
