package cloudcity;

import android.app.Application;

public class CloudCityOMNTApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CloudCityParamsRepository.initialize(getApplicationContext());
    }
}
