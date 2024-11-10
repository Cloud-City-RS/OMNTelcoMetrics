package cloudcity.networking;

import cloudcity.networking.models.NetworkDataModelRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface ServerAPI {
    String X_MAC_ADDRESS_HEADER = "X-MAC-Address";

    @Headers({
            "Accept: application/json",
            "Content-Type: application/json"
    })
    @POST("/api/sensor-events")
    Call<Void> sendData(@Header(X_MAC_ADDRESS_HEADER) String token, @Body NetworkDataModelRequest request);

}
