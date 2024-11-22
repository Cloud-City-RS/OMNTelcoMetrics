package de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider;

import org.json.JSONObject;

import cloudcity.util.CloudCityLogger;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.BuildConfig;

public class BuildInformation extends Information {
    private final String TAG = "BuildInformation";

    public BuildInformation() {
        super();
    }

    public BuildInformation(long timeStamp) {
        super(timeStamp);
    }

    public String getBuildType() {
        return BuildConfig.BUILD_TYPE;
    }

    public int getVersionCode() {
        return BuildConfig.VERSION_CODE;
    }

    public String getVersionName() {
        return BuildConfig.VERSION_NAME;
    }

    public String getApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    public boolean isDebug() {
        return BuildConfig.DEBUG;
    }

    public JSONObject toJSON(){

        JSONObject json = new JSONObject();
        try {
            json.put("BuildType", getBuildType());
            json.put("VersionCode", getVersionCode());
            json.put("VersionName", getVersionName());
            json.put("ApplicationId", getApplicationId());
            json.put("Debug", isDebug());
        } catch (Exception e) {
            CloudCityLogger.d(TAG,e.toString(), e);
        }
        return json;
    }

}
