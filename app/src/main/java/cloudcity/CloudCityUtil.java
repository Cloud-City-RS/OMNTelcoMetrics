package cloudcity;

import android.os.Build;

import androidx.annotation.NonNull;

import cloudcity.dataholders.MetricsPOJO;
import cloudcity.networking.CloudCityHelpers;
import cloudcity.networking.models.Iperf3NetworkDataModel;
import cloudcity.networking.models.NetworkDataModel;
import cloudcity.networking.models.NetworkDataModelRequest;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.LocationInformation;

public class CloudCityUtil {

    /**
     * This method is necessary, since {@link String#isBlank()} is introduced in API34
     * @param param non-null string to check
     * @return whether passed-in string is blank (all whitespace or empty) or not. Nulls are considered blank.
     */
    public static boolean isBlank(@NonNull String param) {
        if (param == null) return true;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return param.isBlank();
        } else {
            return isBlank_pre34(param);
        }
    }

    private static boolean isBlank_pre34(@NonNull String param) {
        return param.trim().isEmpty();
    }

    public static boolean sendIperf3Data(MetricsPOJO metricsPOJO, LocationInformation location) {
        MetricsPOJO.MetricsPair metricsPair = metricsPOJO.toMetricsPair();
        MetricsPOJO.UploadMetrics uploadMetrics = metricsPair.getUploadMetrics();
        MetricsPOJO.DownloadMetrics downloadMetrics = metricsPair.getDownloadMetrics();

        Iperf3NetworkDataModel iperf3Data = new Iperf3NetworkDataModel(uploadMetrics, downloadMetrics, location);
        return CloudCityUtil.sendIperf3Data(iperf3Data);
    }

    private static boolean sendIperf3Data(NetworkDataModel data) {
        String address = CloudCityParamsRepository.getInstance().getServerUrl();
        String token = CloudCityParamsRepository.getInstance().getServerToken();
        NetworkDataModelRequest requestData = new NetworkDataModelRequest();
        requestData.add(data);
        return CloudCityHelpers.sendData(address, token, requestData);
    }
}
