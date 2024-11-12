package cloudcity.networking.models;

import com.google.gson.annotations.SerializedName;

/**
 * Base class for our network data
 */
public class NetworkDataModel {
    @SerializedName("lat")
    protected final double latitude;
    @SerializedName("lon")
    protected final double longitude;
    @SerializedName("values")
    protected final NetworkDataModelValues values;

    public NetworkDataModel(double latitude, double longitude, NetworkDataModelValues values) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.values = values;
    }

    static class NetworkDataModelValues { }

    public boolean hasNonZeroLatitudeAndLongitude() {
        return (latitude != 0f && longitude != 0f);
    }
}
