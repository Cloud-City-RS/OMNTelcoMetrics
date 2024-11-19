package cloudcity.networking.models;

import android.location.Location;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import cloudcity.dataholders.MetricsPOJO;
import cloudcity.util.CloudCityUtil;

public class Iperf3NetworkDataModel extends NetworkDataModel {
    private static final int NUMBER_OF_DECIMALS_FOR_ACCURACY = 4;

    @SerializedName("category")
    final private String category = "Iperf3";

    @SerializedName("accuracy")
    private double accuracy;
    @SerializedName("speed")
    private double speed;

    public Iperf3NetworkDataModel(
            @NonNull MetricsPOJO.UploadMetrics upload,
            @NonNull MetricsPOJO.DownloadMetrics download,
            @NonNull Location location,
            @NonNull MeasurementsModel measurementsModel
    ) {
        super(location.getLatitude(), location.getLongitude(), new Iperf3ValuesModel(upload, download, measurementsModel));
        this.accuracy = location.getAccuracy();
        this.speed = location.getSpeed();
    }
}

class Iperf3ValuesModel extends NetworkDataModel.NetworkDataModelValues {
    private static final int NUMBER_OF_DECIMALS_FOR_METRICS = 2;

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

    @SerializedName("cell_type")
    private final int cellType;

    // LTE
    private final Integer rsrp;
    private final Integer rsrq;
    private final Integer rssnr;

    // 5G
    private final Integer csirsrp;
    private final Integer csirsrq;
    private final Integer csisinr;
    private final Integer ssrsrp;
    private final Integer ssrsrq;
    private final Integer sssinr;

    public Iperf3ValuesModel(@NonNull MetricsPOJO.UploadMetrics upload,
                             @NonNull MetricsPOJO.DownloadMetrics download,
                             @NonNull MeasurementsModel cellMeasurements) {
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

        rsrp = cellMeasurements.getRsrp();
        rsrq = cellMeasurements.getRsrq();
        rssnr = cellMeasurements.getRssnr();

        csirsrp = cellMeasurements.getCsirsrp();
        csirsrq = cellMeasurements.getCsirsrq();
        csisinr = cellMeasurements.getCsisinr();
        ssrsrp = cellMeasurements.getSsrsrp();
        ssrsrq = cellMeasurements.getSsrsrq();
        sssinr = cellMeasurements.getSssinr();

        cellType = cellMeasurements.getCellType();
    }
}
