package cloudcity.networking;

import cloudcity.networking.models.NetworkDataModelRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface ServerAPI {

    @Headers({
            "Accept: application/json",
            "Content-Type: application/json"
    })
    @POST("/api/sensor-events")
    Call<Void> sendData(@Header("Authorization") String token, @Body NetworkDataModelRequest request);

}
