package cloudcity.networking;

import android.util.Log;

import java.io.IOException;
import java.util.Locale;

import cloudcity.networking.models.NetworkDataModelRequest;
import retrofit2.Response;
import retrofit2.Retrofit;

public class CloudCityHelpers {
    public static final String TAG = CloudCityHelpers.class.getSimpleName();

    private static final String X_MAC_ADDRESS_HEADER = "X-MAC-Address ";

    public static boolean sendData(String address, String token, NetworkDataModelRequest data) {
        Log.d(TAG, "--> sendData()\taddress=" + address + ", token=" + token + ", data=" + data);
        Retrofit retrofit = NetworkClient.getRetrofitClient(address);
        if (retrofit == null) {
            Log.e(TAG, "sendData: Retrofit not set, exiting.");
            return false;
        }
        ServerAPI api = retrofit.create(ServerAPI.class);
        Response<Void> response = null;

        try {
            Log.d(TAG, String.format(Locale.US, "sendData: Executing send data request: address %s", address));
            response = api.sendData(X_MAC_ADDRESS_HEADER + token, data).execute();
            Log.d(TAG, String.format(Locale.US, "Received sendData response. %s", response));

            if (response.isSuccessful()) {
                return true;
            } else {
                Log.d(TAG, "sendData: Send data request failed.");
            }
            return false;

        } catch (IOException e) {
            Log.e(TAG, "sendData: Failure to receive response.", e);
            return false;
        }
    }
}
