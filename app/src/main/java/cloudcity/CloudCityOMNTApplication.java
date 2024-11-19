package cloudcity;

import android.app.Application;

import cloudcity.dataholders.MetricsPOJO;
import cloudcity.util.CellUtil;
import cloudcity.util.CloudCityUtil;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.DataProvider;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.GlobalVars;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.android.timber.SentryTimberTree;
import timber.log.Timber;

public class CloudCityOMNTApplication extends Application {
    public static final String TAG = CloudCityOMNTApplication.class.getSimpleName();

    private Iperf3Monitor iperf3Monitor;

    @Override
    public void onCreate() {
        super.onCreate();
        CloudCityParamsRepository.initialize(getApplicationContext());

        // Because Timber seems to be pure kotlin, and java can't quite interact well with pure kotlin code
        // lets just make a kotlin class to instantiate timber and configure it, and call that method from here
        Timber.plant(new Timber.DebugTree());
        Timber.plant(new SentryTimberTree(
                Sentry.getCurrentHub(),
                SentryLevel.DEBUG,
                SentryLevel.DEBUG
        ));

        Iperf3Monitor.initialize(getApplicationContext());
        iperf3Monitor = Iperf3Monitor.getInstance();

        iperf3Monitor.startListeningForIperf3Updates(new Iperf3Monitor.Iperf3MonitorCompletionListener() {
            @Override
            public void onIperf3TestCompleted(MetricsPOJO metrics) {
                android.util.Log.wtf(TAG, "One iperf3 cycle is completed! received metrics: "+metrics);
                DataProvider dataProvider = GlobalVars.getInstance().get_dp();
                boolean iperf3SendingResult = CloudCityUtil.sendIperf3Data(
                        metrics,
                        GPSMonitor.getLastLocation(),
                        CellUtil.getRegisteredCellInformationUpdatedBySignalStrengthInformation(dataProvider)
                );
                android.util.Log.d(TAG, "sending metrics result: "+iperf3SendingResult);
            }
        });
    }
}
