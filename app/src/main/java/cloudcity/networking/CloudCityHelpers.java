package cloudcity.networking;

import java.io.IOException;
import java.util.Locale;

import cloudcity.networking.models.NetworkDataModel;
import cloudcity.networking.models.NetworkDataModelRequest;
import cloudcity.util.CloudCityLogger;
import retrofit2.Response;
import retrofit2.Retrofit;

public class CloudCityHelpers {
    public static final String TAG = "CloudCityHelpers";

    public static boolean sendData(String address, String token, NetworkDataModelRequest data) {
        CloudCityLogger.d(TAG, "--> sendData()\taddress=" + address + ", token=" + token + ", data=" + data);
        Retrofit retrofit = NetworkClient.getRetrofitClient(address);
        if (retrofit == null) {
            CloudCityLogger.e(TAG, "sendData: Retrofit not set, exiting.");
            return false;
        }
        ServerAPI api = retrofit.create(ServerAPI.class);
        Response<Void> response = null;

        try {
            // Check if data has valid Lat,Lng pair, do not send if they are (0,0)
            boolean continueSending = validateData(data);
            if (continueSending) {
                CloudCityLogger.d(TAG, String.format(Locale.US, "sendData: Executing send data request: address %s", address));
                response = api.sendData("Bearer " + token, data).execute();
                CloudCityLogger.d(TAG, String.format(Locale.US, "Received sendData response. %s", response));

                if (response.isSuccessful()) {
                    return true;
                } else {
                    CloudCityLogger.d(TAG, "sendData: Send data request failed.");
                }
                return false;
            } else {
                CloudCityLogger.e(TAG, "sendData: data's location information was (0,0) - skipping sending!");
                return false;
            }
        } catch (IOException e) {
            CloudCityLogger.e(TAG, "sendData: Failure to receive response.", e);
            return false;
        }
    }

    private static boolean validateData(NetworkDataModelRequest data) {
        // Assume data is valid
        boolean isValid = true;

        if (data != null) {
            // We have valid data if lattitude and longitude are non-zero
            for (NetworkDataModel model : data.getData()) {
                isValid = isValid && model.hasNonZeroLatitudeAndLongitude();
            }
        } else {
            isValid = false;
        }

        return isValid;
    }
}
