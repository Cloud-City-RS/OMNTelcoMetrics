package cloudcity.networking;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import cloudcity.util.CloudCityLogger;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

public class CustomClient {

    public static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            X509TrustManager trustManager = new CustomTrustManager();

            // Create an SSL context with the custom trust manager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor(new CloudCityLogger.CloudCityLoggerOKHttpLogger());
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            // Create an OkHttpClient with the custom SSL context
            return new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                    .hostnameVerifier((hostname, session) -> true)
                    .addInterceptor(logging)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
