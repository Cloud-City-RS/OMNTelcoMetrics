package cloudcity;

import android.app.Application;

import cloudcity.dataholders.Iperf3MetricsPOJO;
import cloudcity.util.CellUtil;
import cloudcity.util.CloudCityLogger;
import cloudcity.util.CloudCityUtil;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.DataProvider;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.GlobalVars;
import timber.log.Timber;

public class CloudCityOMNTApplication extends Application {
    public static final String TAG = "CloudCityOMNTApplication";

    private Iperf3Monitor iperf3Monitor;

    @Override
    public void onCreate() {
        super.onCreate();
        CloudCityParamsRepository.initialize(getApplicationContext());

        Timber.plant(new Timber.DebugTree());

        Iperf3Monitor.initialize(getApplicationContext());
        iperf3Monitor = Iperf3Monitor.getInstance();

        iperf3Monitor.startListeningForIperf3Updates(new Iperf3Monitor.Iperf3MonitorCompletionListener() {
            @Override
            public void onIperf3TestCompleted(Iperf3MetricsPOJO metrics) {
                CloudCityLogger.d(TAG, "One iperf3 cycle is completed! received metrics: " + metrics);
                DataProvider dataProvider = GlobalVars.getInstance().get_dp();
                boolean iperf3SendingResult = CloudCityUtil.sendIperf3Data(
                        metrics,
                        GPSMonitor.getLastLocation(),
                        CellUtil.getRegisteredCellInformationUpdatedBySignalStrengthInformation(dataProvider)
                );
                CloudCityLogger.d(TAG, "sending iperf3 metrics result: "+iperf3SendingResult);
            }
        });

        PingMonitor.initialize(getApplicationContext());
    }
}
