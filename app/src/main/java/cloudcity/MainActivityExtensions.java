package cloudcity;

import android.content.SharedPreferences;
import android.util.Log;

import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SPType;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SharedPreferencesGrouper;

public class MainActivityExtensions {

    public static void performMainActivityThing(String TAG, SharedPreferencesGrouper spg) {
        performMainActivityThing2(TAG, spg.getSharedPreference(SPType.default_sp));
    }

    public static void performMainActivityThing2(String TAG, SharedPreferences sp) {
        /* Handle default values for Cloud City URL and token */
        String address = sp.getString("cloud_city_url", "");
        String token = sp.getString("cloud_city_token", "");

        if (address.isEmpty()) {
            Log.d(TAG, "onCreate: Cloud city address not set, setting default");
            sp.edit().putString("cloud_city_url", "staging.app.cloudcities.co").commit();
        }

        if (token.isEmpty()) {
            Log.d(TAG, "onCreate: Cloud city token not set, setting default");
            sp.edit().putString("cloud_city_token", "68|5LGMoNAd0mck4bmMaGdj2GqjqqYUB1NyqtbSrpFB82303173").commit();
        }
    }
}
