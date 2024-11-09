package cloudcity;

import android.app.Application;

import cloudcity.dataholders.MetricsPOJO;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.DataProvider;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.GlobalVars;

public class CloudCityOMNTApplication extends Application {
    public static final String TAG = CloudCityOMNTApplication.class.getSimpleName();

    private Iperf3Monitor iperf3Monitor;

    @Override
    public void onCreate() {
        super.onCreate();
        CloudCityParamsRepository.initialize(getApplicationContext());

        Iperf3Monitor.initialize(getApplicationContext());
    }
}
