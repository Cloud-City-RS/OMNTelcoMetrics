package cloudcity;

/**
 * The "string holder" class for anything CloudCity related
 */
public class CloudCityConstants {
    /**
     * Name of the cloud city server url preference
     */
    public static final String CLOUD_CITY_SERVER_URL = "cloud_city_url";
    /**
     * Name of the cloud city token preference
     */
    public static final String CLOUD_CITY_TOKEN = "cloud_city_token";

    public static final String CLOUD_CITY_GENERAL_LOGGING = "enable_logging";
    public static final String CLOUD_CITY_CC_LOGGING = "enable_cloud_city";


    // Iperf3Fragment constants
    public static final String CLOUD_CITY_IPERF3_SERVER = "demo.app.cloudcities.co";
    public static final int CLOUD_CITY_IPERF3_VALID_PORT_MIN = 9200;
    public static final int CLOUD_CITY_IPERF3_VALID_PORT_MAX = 9240;
    public static final int CLOUD_CITY_IPERF3_DEFAULT_DURATION = 30;

    // Iperf3 time-based throttling shared pref key
    public static final String CLOUD_CITY_IPERF3_TEST_TIME_THROTTLING_THRESHOLD = "cloud_city_iperf3_throttling_interval";
    // Iperf3 distance-based throttling shared pref key
    public static final String CLOUD_CITY_IPERF3_TEST_DISTANCE_THROTTLING_THRESHOLD = "cloud_city_iperf3_throttling_distance";
}
