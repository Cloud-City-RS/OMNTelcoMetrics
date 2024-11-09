package cloudcity.networking.models;

import com.google.gson.annotations.SerializedName;

public class MobileSignalNetworkDataModel extends NetworkDataModel {

    private String category;
    @SerializedName("lat")
    private double latitude;
    @SerializedName("lon")
    private double longitude;
    private double accuracy;
    private double speed;
    @SerializedName("cell_info")
    private CellInfoModel cellData;
    private MeasurementsModel values;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
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

    public MeasurementsModel getValues() {
        return values;
    }

    public void setValues(MeasurementsModel values) {
        this.values = values;
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
