package cloudcity;

import static cloudcity.CloudCityConstants.CLOUD_CITY_CC_LOGGING;
import static cloudcity.CloudCityConstants.CLOUD_CITY_GENERAL_LOGGING;
import static cloudcity.CloudCityConstants.CLOUD_CITY_SERVER_URL;
import static cloudcity.CloudCityConstants.CLOUD_CITY_TOKEN;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.DataProvider;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SPType;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SharedPreferencesGrouper;

public class MainActivityExtensions {

    /**
     * We will be using the 'logging' shared pref since that's the only one that is displayed in the logging settings fragment...
     * @param TAG
     * @param spg
     */
    public static void performMainActivityThing(String TAG, SharedPreferencesGrouper spg, DataProvider dp) {
        // First, set a listener that will trigger when the actual CC logging changes
        spg.setListener((sharedPreferences, key) -> {
            if (key.equalsIgnoreCase(CLOUD_CITY_CC_LOGGING)) {
                dp.refreshAll();
            }
        }, SPType.logging_sp);
        // Now perform the other stuff, and among other things, change the CC logging
        performMainActivityThing2(TAG, spg.getSharedPreference(SPType.logging_sp));

        // Finally, get rid of the listener we put up there
        spg.removeListener(SPType.logging_sp);
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

        // Finally, turn on CloudCity logging by default
        sp.edit()
                .putBoolean(CLOUD_CITY_GENERAL_LOGGING, true)
                .putBoolean(CLOUD_CITY_CC_LOGGING, true)
                .commit();
    }
}
