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
    /**
     * iPerf3 server to use for iperf3 tests; used for Settings screen prefilling as well as CloudCity automated tests
     */
    public static final String CLOUD_CITY_IPERF3_SERVER = "demo.app.cloudcities.co";
    /**
     * iPerf3 server port range to use for iperf3 tests, this one is the smaller of the two ports in the range;
     * used for Settings screen prefilling as well as CloudCity automated tests
     */
    public static final int CLOUD_CITY_IPERF3_VALID_PORT_MIN = 9200;
    /**
     * iPerf3 server port range to use for iperf3 tests, this one is the larger of the two ports in the range;
     * used for Settings screen prefilling as well as CloudCity automated tests
     */
    public static final int CLOUD_CITY_IPERF3_VALID_PORT_MAX = 9240;
    /**
     * iPerf3 test duration; used for Settings screen prefilling
     */
    public static final int CLOUD_CITY_IPERF3_DEFAULT_DURATION = 30;
    /**
     * iPerf3 automated iperf3 test duration; used only for CloudCity automated tests
     */
    public static final int CLOUD_CITY_IPERF3_TEST_DURATION_IN_SECONDS = 10;

    /**
     * The threshold value we need to be under to start the iPerf3 test
     * Since 1m/s = 3.6km/h, and we need to run tests as long as we're under 5km/h, we're looking at something ~1.8m/s
     *
     * Used only for CloudCity automated tests
     */
    public static final float CLOUD_CITY_IPERF3_TEST_SPEED_THRESHOLD_VALUE = 1.8f;
    /**
     * How long do we need to be under threshold to fire a callback that starts the automated iPerf3 test
     *
     * Used only for CloudCity automated tests
     */
    public static final int CLOUD_CITY_IPERF3_TEST_SPEED_THRESHOLD_DURATION_IN_MILLIS = 5000;

    // Iperf3 time-based throttling shared pref key
    public static final String CLOUD_CITY_IPERF3_TEST_TIME_THROTTLING_THRESHOLD = "cloud_city_iperf3_throttling_interval";
    // Iperf3 distance-based throttling shared pref key
    public static final String CLOUD_CITY_IPERF3_TEST_DISTANCE_THROTTLING_THRESHOLD = "cloud_city_iperf3_throttling_distance";
}
