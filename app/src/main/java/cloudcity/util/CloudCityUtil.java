package cloudcity.util;

import android.location.Location;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.NonNull;

import cloudcity.CloudCityParamsRepository;
import cloudcity.dataholders.Iperf3MetricsPOJO;
import cloudcity.networking.CloudCityHelpers;
import cloudcity.networking.models.CellInfoModel;
import cloudcity.networking.models.Iperf3NetworkDataModel;
import cloudcity.networking.models.MeasurementsModel;
import cloudcity.networking.models.NetworkDataModel;
import cloudcity.networking.models.NetworkDataModelRequest;

public class CloudCityUtil {

    /**
     * This method is necessary, since {@link String#isBlank()} is introduced in API34
     *
     * @param param non-null string to check
     * @return whether passed-in string is blank (all whitespace or empty) or not. Nulls are considered blank.
     */
    public static boolean isBlank(@NonNull String param) {
        if (param == null) return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return param.isBlank();
        } else {
            return isBlank_pre34(param);
        }
    }

    private static boolean isBlank_pre34(@NonNull String param) {
        return param.trim().isEmpty();
    }

    /**
     * Sends iperf3 network performance data to the server
     *
     * @param metricsPOJO          The metrics containing upload and download data
     * @param location             The location where the test was performed
     * @param cellInfoMeasurements The cellular network measurements during the test
     * @return true if the data was sent successfully, false otherwise
     */
    public static boolean sendIperf3Data(
            @NonNull Iperf3MetricsPOJO metricsPOJO,
            @NonNull Location location,
            @NonNull Pair<MeasurementsModel, CellInfoModel> cellInfoMeasurements) {
        Iperf3MetricsPOJO.MetricsPair metricsPair = metricsPOJO.toMetricsPair();
        Iperf3MetricsPOJO.UploadMetrics uploadMetrics = metricsPair.getUploadMetrics();
        Iperf3MetricsPOJO.DownloadMetrics downloadMetrics = metricsPair.getDownloadMetrics();

        Iperf3NetworkDataModel iperf3Data = new Iperf3NetworkDataModel(
                uploadMetrics,
                downloadMetrics,
                location,
                cellInfoMeasurements.first,
                cellInfoMeasurements.second
        );
        return CloudCityUtil.sendIperf3Data(iperf3Data);
    }

    /**
     * Internal method for sending iperf3 data that grabs the server url/token from the repo
     * and calls into {@link CloudCityHelpers#sendData(String, String, NetworkDataModelRequest)}
     *
     * @param data the {@link Iperf3NetworkDataModel} to send
     * @return true if sending went successfuly, false otherwise
     */
    private static boolean sendIperf3Data(NetworkDataModel data) {
        String address = CloudCityParamsRepository.getInstance().getServerUrl();
        String token = CloudCityParamsRepository.getInstance().getServerToken();
        NetworkDataModelRequest requestData = new NetworkDataModelRequest();
        requestData.add(data);
        return CloudCityHelpers.sendData(address, token, requestData);
    }

    /**
     * Method that rounds a double {@code numberToRound} to {@code numberOfDecimals} number of decimals and returns the
     * corrected number
     *
     * @param numberToRound    the number to round to a fixed number of decimals
     * @param numberOfDecimals the number of decimals to allow after the decimal point
     * @return the fixed number
     */
    public static double roundToNumberOfDecimals(double numberToRound, int numberOfDecimals) {
        // multiply by 10^numberOfDecimals, round, floor, then return divided
        double multiplier = Math.pow(10, numberOfDecimals);
        double basis = numberToRound * multiplier;
        basis = Math.round(basis);
        double retVal = basis / multiplier;
        return retVal;
    }
}
