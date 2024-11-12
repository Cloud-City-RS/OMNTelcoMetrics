package cloudcity.networking.models;

import com.google.gson.annotations.SerializedName;

/**
 * Base class for our network data
 */
public class NetworkDataModel {
    private static final double EPSILON = 0.00001;

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
        return isNonZero(latitude) && isNonZero(longitude);
    }

    private boolean isNonZero(double value) {
        // if we have a zero, or some very small number e.g. 0.00000000234, subtracting EPSILON
        // from it and performing an abs() on the result will make it a number less than EPSILON
        // since effectivelly it'd be as if we performed (EPSILON - small number)
        return value != 0f && value != 0.0 && Math.abs(value - EPSILON) <= EPSILON;
    }
}
