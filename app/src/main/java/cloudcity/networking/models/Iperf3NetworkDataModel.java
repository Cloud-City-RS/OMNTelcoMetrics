package cloudcity.networking.models;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import cloudcity.dataholders.MetricsPOJO;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.LocationInformation;

public class Iperf3NetworkDataModel extends NetworkDataModel {

    @SerializedName("category")
    final private String category = "Iperf3";

    @SerializedName("lat")
    private double latitude;
    @SerializedName("lon")
    private double longitude;

    @SerializedName("accuracy")
    private double accuracy;
    @SerializedName("speed")
    private double speed;

    @SerializedName("values")
    final private Iperf3ValuesModel values;


    public Iperf3NetworkDataModel(
            MetricsPOJO.UploadMetrics upload,
            MetricsPOJO.DownloadMetrics download,
            LocationInformation location
    ) {
        this.values = new Iperf3ValuesModel(upload, download);
        this.latitude = location.getLatitude();
        this.longitude = location.getLongitude();
        this.accuracy = location.getAccuracy();
        this.speed = location.getSpeed();
    }
}

class Iperf3ValuesModel {
    @SerializedName("upload_min")
    private final double ULmin;
    @SerializedName("upload_median")
    private final double ULmedian;
    @SerializedName("upload_mean")
    private final double ULmean;
    @SerializedName("upload_max")
    private final double ULmax;
    @SerializedName("upload_last")
    private final double ULlast;

    @SerializedName("download_min")
    private final double DLmin;
    @SerializedName("download_median")
    private final double DLmedian;
    @SerializedName("download_mean")
    private final double DLmean;
    @SerializedName("download_max")
    private final double DLmax;
    @SerializedName("download_last")
    private final double DLlast;

    public Iperf3ValuesModel(@NonNull MetricsPOJO.UploadMetrics upload,
                             @NonNull MetricsPOJO.DownloadMetrics download) {
        ULmin = upload.getULmin();
        ULmedian = upload.getULmedian();
        ULmean = upload.getULmean();
        ULmax = upload.getULmax();
        ULlast = upload.getULlast();

        DLmin = download.getDLmin();
        DLmedian = download.getDLmedian();
        DLmean = download.getDLmean();
        DLmax = download.getDLmax();
        DLlast = download.getDLlast();
    }
}
