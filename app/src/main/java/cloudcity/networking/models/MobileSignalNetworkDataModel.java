package cloudcity.networking.models;

import android.location.Location;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

public class MobileSignalNetworkDataModel extends NetworkDataModel {

    private String category;
    private double accuracy;
    private double speed;
    @SerializedName("cell_info")
    private CellInfoModel cellData;

    public MobileSignalNetworkDataModel(@NonNull Location locationInformation, @NonNull MeasurementsModel values) {
        super(locationInformation.getLatitude(), locationInformation.getLongitude(), values);
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public CellInfoModel getCellData() {
        return cellData;
    }

    public void setCellData(CellInfoModel cellData) {
        this.cellData = cellData;
    }


    @Override
    public String toString() {
        return "NetworkDataModel{" +
                "category='" + category + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", accuracy=" + accuracy +
                ", speed=" + speed +
                ", cellData=" + cellData +
                ", values=" + values +
                '}';
    }
}
