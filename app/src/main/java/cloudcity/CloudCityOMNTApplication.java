package cloudcity;

import android.app.Application;

public class CloudCityOMNTApplication extends Application {
    public static final String TAG = CloudCityOMNTApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        CloudCityParamsRepository.initialize(getApplicationContext());

        CloudCityUtil.startListeningForIperf3Updates(TAG, getApplicationContext());
    }
}
