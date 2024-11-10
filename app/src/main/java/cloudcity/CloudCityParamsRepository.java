package cloudcity;

import static cloudcity.CloudCityConstants.CLOUD_CITY_SERVER_URL;
import static cloudcity.CloudCityConstants.CLOUD_CITY_TOKEN;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import de.fraunhofer.fokus.OpenMobileNetworkToolkit.BuildConfig;


public class CloudCityParamsRepository {
    private static final String TAG = "CloudCityParamsRepository";

    private static final String SHARED_PREFS_NAME = "cloud_city_prefs";

    private static final String STAGING_SERVER_URL = "staging.app.cloudcities.co";
    private static final String PRODUCTION_SERVER_URL = "app.cloudcities.co";
    private static final String DEMO_SERVER_URL = "demo.app.cloudcities.co";

    private Context context;
    private static CloudCityParamsRepository instance;

    private SharedPreferences sharedPrefs;

    private volatile String serverUrl;
    private volatile String serverToken;


    private CloudCityParamsRepository(Context context) {
        this.context = context;
        this.sharedPrefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Initializes the repository, loads saved data and prefills it with data if no data was found
     *
     * @param ctx the context to use
     */
    public static void initialize(Context ctx) {
        if (instance != null) {
            throw new IllegalStateException("Already initialized!");
        } else {
            // Since instance is null, this branch is already the first part of
            // the double-locked synchronized singleton creation
            synchronized (CloudCityParamsRepository.class) {
                if (instance == null) {
                    instance = new CloudCityParamsRepository(ctx);
                }
            }
        }
        instance.loadData();
        if (!instance.isParamPresent(instance.getServerUrl()) || !instance.isParamPresent(instance.getServerToken())) {
            instance.prefillWithData();
        }
    }

    public static CloudCityParamsRepository getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Must call initialize() first!");
        }

        return instance;
    }

    private void loadData() {
        serverUrl = sharedPrefs.getString(CLOUD_CITY_SERVER_URL, "");
        serverToken = sharedPrefs.getString(CLOUD_CITY_TOKEN, "");
    }

    private void prefillWithData() {
        serverUrl = getServerURLBasedOnBuildVariant();
        serverToken = getMacAddress();

        sharedPrefs
                .edit()
                .putString(CLOUD_CITY_SERVER_URL, serverUrl)
                .putString(CLOUD_CITY_TOKEN, serverToken)
                .commit();
    }

    public boolean isParamPresent(@Nullable String param) {
        return param != null && !param.isEmpty() && !CloudCityUtil.isBlank(param);
    }

    /**
     * Return cached {@link #serverUrl} if non-empty, or fetches it from the shared prefs
     * @return the cached serverURL or the one from shared prefs; if both of these are empty/nonexistant then an empty string is returned
     */
    public synchronized String getServerUrl() {
        String retVal;
        if (isParamPresent(serverUrl)) {
            retVal = serverUrl;
        } else {
            serverUrl = sharedPrefs.getString(CLOUD_CITY_SERVER_URL, "");
            retVal = serverUrl;
        }
        return retVal;
    }

    /**
     * Return cached {@link #serverToken} if non-empty, or fetches it from the shared prefs
     * @return the cached serverToken or the one from shared prefs; if both of these are empty/nonexistant then an empty string is returned
     */
    public synchronized String getServerToken() {
        String retVal;
        if (isParamPresent(serverToken)) {
            retVal = serverToken;
        } else {
            serverToken = sharedPrefs.getString(CLOUD_CITY_TOKEN, "");
            retVal = serverToken;
        }
        return retVal;
    }

    /**
     * Convenience method for putting the server URL to internal shared prefs
     * @param newUrl the new server URL to put in
     * @see #putStringKey(String, String)
     */
    public synchronized void setServerUrl(String newUrl) {
        putStringKey(CLOUD_CITY_SERVER_URL, newUrl);
        serverUrl = newUrl;
    }

    /**
     * Convenience method for putting the server token to internal shared prefs
     * @param newToken the new server token to put in
     * @see #putStringKey(String, String)
     */
    public synchronized void setServerToken(String newToken) {
        putStringKey(CLOUD_CITY_TOKEN, newToken);
        serverToken = newToken;
    }

    /**
     * Returns the <i>key</i> from internal {@link SharedPreferences} storage, or "" if no key is found
     *
     * @param key the key to retrieve
     * @return value of the key, or empty string
     */
    public String getStringKey(String key) {
        return sharedPrefs.getString(key, "");
    }

    /**
     * Puts a String value behind the key-value pair in the internal {@link SharedPreferences}
     *
     * @param key   the key to use
     * @param value the key to put in
     */
    public void putStringKey(String key, String value) {
        sharedPrefs
                .edit()
                .putString(key, value)
                .commit();
    }

    private String getServerURLBasedOnBuildVariant() {
        String serverUrl = null;
        if(BuildConfig.IS_DEMO) {
            serverUrl = DEMO_SERVER_URL;
        } else if (BuildConfig.IS_STAGING) {
            serverUrl = STAGING_SERVER_URL;
        } else if (BuildConfig.IS_PRODUCTION) {
            serverUrl = PRODUCTION_SERVER_URL;
        } else {
            throw new IllegalStateException("Which server environment are we trying to use? Something has went wrong here.");
        }

        return serverUrl;
    }

    /**
     * MAC address sniffer
     */

    public String getMacAddress() {
        // Even though our minSdk is 31 (and M is 23) better to be thorough
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            return getMacAddressPreM();
        } else {
            return getMacAddressPostM();
        }
    }

    public String getMacAddressPreM() {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        return wifiInfo.getMacAddress();
    }

    public static String getMacAddressPostM() {
        final String WLAN_INTERFACE = "wlan0";

        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase(WLAN_INTERFACE)) continue;
                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(String.format("%02X:", b));
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }
        } catch (Exception ex) {
            Log.e(TAG, "Exception happened while trying to lookup MAC address! ex: "+ex, ex);
        }
        return "02:00:00:00:00:00";
    }
}
