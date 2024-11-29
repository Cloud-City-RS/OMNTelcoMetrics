package cloudcity.networking.models;

import android.location.Location;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import cloudcity.dataholders.Iperf3MetricsPOJO;
import cloudcity.dataholders.PingMetricsPOJO;

public class Iperf3NetworkDataModel extends NetworkDataModel {
    @SerializedName("category")
    final private String category = "Iperf3";
    @SerializedName("accuracy")
    private double accuracy;
    @SerializedName("speed")
    private double speed;
    @SerializedName("cell_info")
    private final ExtendedCellInfoModel cellData;

    public Iperf3NetworkDataModel(
            @NonNull Iperf3MetricsPOJO.UploadMetrics upload,
            @NonNull Iperf3MetricsPOJO.DownloadMetrics download,
            @NonNull PingMetricsPOJO.RTTMetrics rttMetrics,
            @NonNull PingMetricsPOJO.PackageLossMetrics packageLossMetrics,
            @NonNull Location location,
            @NonNull MeasurementsModel measurementsModel,
            @NonNull CellInfoModel cellInfoModel
    ) {
        super(
                location.getLatitude(),
                location.getLongitude(),
                new Iperf3ValuesModel(upload, download, rttMetrics, packageLossMetrics)
        );
        this.accuracy = location.getAccuracy();
        this.speed = location.getSpeed();
        // Create the ExtendedCellInfo by merging the cellInfoModel with measurementsModel
        // we do this because we don't want actual signal measurements in the iperf3 values
        // but could use them in the cell_info
        this.cellData = new ExtendedCellInfoModel(measurementsModel, cellInfoModel);
    }
}

class Iperf3ValuesModel extends NetworkDataModel.NetworkDataModelValues {
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

    // ping details
    @SerializedName("ping_RTT_min")
    private final double RTTmin;
    @SerializedName("ping_RTT_median")
    private final double RTTmedian;
    @SerializedName("ping_RTT_mean")
    private final double RTTmean;
    @SerializedName("ping_RTT_max")
    private final double RTTmax;
    @SerializedName("ping_RTT_last")
    private final double RTTlast;

    @SerializedName("ping_package_loss_min")
    private final double PLmin;
    @SerializedName("ping_package_loss_median")
    private final double PLmedian;
    @SerializedName("ping_package_loss_mean")
    private final double PLmean;
    @SerializedName("ping_package_loss_max")
    private final double PLmax;
    @SerializedName("ping_package_loss_last")
    private final double PLlast;

    public Iperf3ValuesModel(@NonNull Iperf3MetricsPOJO.UploadMetrics upload,
                             @NonNull Iperf3MetricsPOJO.DownloadMetrics download,
                             @NonNull PingMetricsPOJO.RTTMetrics rttMetrics,
                             @NonNull PingMetricsPOJO.PackageLossMetrics packageLossMetrics) {
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

        RTTmin = rttMetrics.getRTTmin();
        RTTmedian = rttMetrics.getRTTmedian();
        RTTmean = rttMetrics.getRTTmean();
        RTTmax = rttMetrics.getRTTmax();
        RTTlast = rttMetrics.getRTTlast();

        PLmin = packageLossMetrics.getPLmin();
        PLmedian = packageLossMetrics.getPLmedian();
        PLmean = packageLossMetrics.getPLmean();
        PLmax = packageLossMetrics.getPLmax();
        PLlast = packageLossMetrics.getPLlast();
    }
}
