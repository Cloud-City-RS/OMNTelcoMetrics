package cloudcity;

import static cloudcity.CloudCityConstants.CLOUD_CITY_SERVER_URL;
import static cloudcity.CloudCityConstants.CLOUD_CITY_TOKEN;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

public class CloudCityParamsRepository {
    private static final String SHARED_PREFS_NAME = "cloud_city_prefs";

    private Context context;
    private static CloudCityParamsRepository instance;

    private SharedPreferences sharedPrefs;

    private String serverUrl;
    private String serverToken;


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
        String sharkToken = "252|GI2CTemW2EX3TmQaDmCzYg1xrs3VEDslnGrfCwp245a84b22";
        String chungaToken = "68|5LGMoNAd0mck4bmMaGdj2GqjqqYUB1NyqtbSrpFB82303173";

        serverUrl = "staging.app.cloudcities.co";
        serverToken = sharkToken;

        sharedPrefs
                .edit()
                .putString(CLOUD_CITY_SERVER_URL, serverUrl)
                .putString(CLOUD_CITY_TOKEN, serverToken)
                .commit();
    }

    public boolean isParamPresent(@Nullable String param) {
        return param != null && !param.isEmpty() && !param.isBlank();
    }

    /**
     * Return cached {@link #serverUrl} if non-empty, or fetches it from the shared prefs
     * @return the cached serverURL or the one from shared prefs; if both of these are empty/nonexistant then an empty string is returned
     */
    public String getServerUrl() {
        String retVal;
        if (isParamPresent(serverUrl)) {
            retVal = serverUrl;
        } else {
            retVal = sharedPrefs.getString(CLOUD_CITY_SERVER_URL, "");
        }
        return retVal;
    }

    /**
     * Return cached {@link #serverToken} if non-empty, or fetches it from the shared prefs
     * @return the cached serverToken or the one from shared prefs; if both of these are empty/nonexistant then an empty string is returned
     */
    public String getServerToken() {
        String retVal;
        if (isParamPresent(serverToken)) {
            retVal = serverToken;
        } else {
            retVal = sharedPrefs.getString(CLOUD_CITY_TOKEN, "");
        }
        return retVal;
    }

    /**
     * Convenience method for putting the server URL to internal shared prefs
     * @param newUrl the new server URL to put in
     * @see #putStringKey(String, String)
     */
    public void setServerUrl(String newUrl) {
        putStringKey(CLOUD_CITY_SERVER_URL, newUrl);
        serverUrl = newUrl;
    }

    /**
     * Convenience method for putting the server token to internal shared prefs
     * @param newToken the new server token to put in
     * @see #putStringKey(String, String)
     */
    public void setServerToken(String newToken) {
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
}
