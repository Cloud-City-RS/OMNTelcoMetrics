package de.fraunhofer.fokus.OpenMobileNetworkToolkit.cloudCity;

import android.util.Log;

import java.util.Locale;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class NetworkClient {
    private static final String TAG = "NetworkClient";

    private static Retrofit retrofit;
    private static String currentClientBaseUrl;

    /**
     * Get retrofit client to be used for the communication. Creates and memoizes the client which will
     * be reused as long as the URL stays the same - when the URL changes and differs from what the
     * memoized ("singleton") client was initialized with - a new client gets initialized with the new base URL.
     *
     * @return Instance of retrofit client to be used.
     */
    public static Retrofit getRetrofitClient(String url) {
        String baseUrl = String.format(Locale.US, "https://%s/", url);
        // If we don't have a retrofit client, or the URL we're targetting is different
        // than what the previous client was initialized with - build a new client;
        // otherwise return the last initialized one
        if (retrofit == null || !baseUrl.equalsIgnoreCase(currentClientBaseUrl)) {
            if (url == null || url.isEmpty()){
                return null;
            }

            if (!url.equalsIgnoreCase(currentClientBaseUrl)) {
                Log.w(TAG, "URL has changed from previous Retrofit client's base URL, instantiating new client...");
            }

            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(CustomClient.getUnsafeOkHttpClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            currentClientBaseUrl = baseUrl;
        }

        return retrofit;
    }
}
