package cloudcity;

import android.app.Application;

import cloudcity.dataholders.MetricsPOJO;

public class CloudCityOMNTApplication extends Application {
    public static final String TAG = CloudCityOMNTApplication.class.getSimpleName();

    private Iperf3Monitor iperf3Monitor;

    @Override
    public void onCreate() {
        super.onCreate();
        CloudCityParamsRepository.initialize(getApplicationContext());

        Iperf3Monitor.initialize();
        iperf3Monitor = Iperf3Monitor.getInstance();

        iperf3Monitor.startListeningForIperf3Updates(getApplicationContext(), new Iperf3Monitor.Iperf3MonitorCompletionListener() {
            @Override
            public void onIperf3TestCompleted(MetricsPOJO metrics) {
                android.util.Log.wtf(TAG, "One iperf3 cycle is completed! received metrics: "+metrics);
            }
        });
    }
}
