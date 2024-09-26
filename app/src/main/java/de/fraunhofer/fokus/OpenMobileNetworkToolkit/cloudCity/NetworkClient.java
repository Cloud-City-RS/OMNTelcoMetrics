package de.fraunhofer.fokus.OpenMobileNetworkToolkit.cloudCity;

import java.util.Locale;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class NetworkClient {

    private static Retrofit retrofit;

    /**
     * Get retrofit client to be used for the communication. Creates singleton.
     * @return Instance of retrofit client to be used.
     */
    public static Retrofit getRetrofitClient(String url) {
        if (retrofit == null) {
            if (url == null || url.isEmpty()){
                return null;
            }

            String baseUrl = String.format(Locale.US, "https://%s/", url);

            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(CustomClient.getUnsafeOkHttpClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        return retrofit;
    }
}
