package cloudcity;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.concurrent.atomic.AtomicBoolean;

import cloudcity.dataholders.MetricsPOJO;
import cloudcity.util.CloudCityLogger;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.Iperf3Parser;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Ping.PingParser;

/**
 * The new single-source for programatically starting ping tests
 */
public class PingMonitor {
    private static final String TAG = "PingMonitor";

    private final static long PARSING_DELAY_IN_MS = 10L; //0.01sec, was 1000L

    private Handler handler;

    private HandlerThread handlerThread;

    private static volatile PingMonitor instance;

    private Context appContext;

    private final AtomicBoolean shouldStop = new AtomicBoolean(false);

    private volatile PingParser pingParser;

    private final AtomicBoolean pingTestRunning = new AtomicBoolean(false);

    private volatile MainThreadExecutor mainThreadExecutor;

    /**
     * Runnable used for parsing Iperf3 tests by constantly calling {@link #pingParser}'s
     * {@link PingParser#parse()} method every {@link #PARSING_DELAY_IN_MS} until it reaches the end
     * or ordered to stop by toggling {@link #shouldStop} flag
     */
    private final Runnable parsingRunnable = new Runnable() {
        @Override
        public void run() {
            CloudCityLogger.v(TAG, "startParsingThread()::parsingCycle!\tshouldStop: " + shouldStop.get());

            // And finally, parse a bit of the file.
            pingParser.parse();
            if (shouldStop.get()) {
                // well, do nothing but return
                return;
            } else {
                handler.postDelayed(this, PARSING_DELAY_IN_MS);
            }
        }
    };

    private PingMonitor() {
        // private constructor to prevent instantiation
    }

    /**
     * Initializes the PingMonitor, sets up the internal {@link Handler} and
     * starts it's background {@link HandlerThread}
     * <br>
     * <b>Can be called only once!</b> Must not be called again unless {@link #shutdown()} is called before.
     *
     * @param appContext the application context
     */
    public static synchronized void initialize(Context appContext) {
        if (instance != null) {
            throw new IllegalStateException("Already initialized!");
        } else {
            // Since instance is null, this branch is already the first part of
            // the double-locked synchronized singleton creation
            synchronized (Iperf3Monitor.class) {
                if (instance == null) {
                    instance = new PingMonitor();
                }
            }
        }

        instance.handlerThread = new HandlerThread("Iperf3MonitorThread");
        instance.handlerThread.start();
        instance.handler = new Handler(instance.handlerThread.getLooper());
        instance.appContext = appContext;
    }

    /**
     * Check whether the PingMonitor is initialized
     *
     * @return whether it's initialized or not, returns <i>true</i> if initialized or <i>false</i> otherwise
     */
    public static synchronized boolean isInitialized() {
        return instance != null;
    }

    /**
     * Returns the PingMonitor instance, after calling {@link #initialize(Context)}
     *
     * @return the PingMonitor instance
     */
    public static synchronized PingMonitor getInstance() {
        if (instance == null) {
            throw new IllegalStateException("PingMonitor is not initialized. Must call initialize() first!");
        }
        return instance;
    }

    /**
     * Shuts down the Iperf3Monitor, clears it's internal Handler and stops it's internal HandlerThread
     */
    public static synchronized void shutdown() {
        if (!isInitialized()) {
            throw new IllegalStateException("Iperf3Monitor was not initialized. Cannot call shutdown() before calling initialize()");
        }

        if (instance.handler != null) {
            try {
                // Clean up all callbacks from the handler
                instance.handler.removeCallbacksAndMessages(null);
            } catch (Exception e) {
                CloudCityLogger.e(TAG, "Exception " + e + " happened during shutdown() while cleaning up handler !!!", e);
            }
        }

        if (instance.handlerThread != null) {
            instance.handlerThread.quitSafely();
            try {
                instance.handlerThread.join();
            } catch (InterruptedException e) {
                CloudCityLogger.e(TAG, "Exception " + e + " happened during shutdown() while joining the handlerThread !!! ", e);
            }
        }
    }

    public void startPingTest() {}

    private void startParsingThread(Iperf3Parser newIperf3Parser) {
        CloudCityLogger.d(TAG, "--> startParsingThread()");
        iperf3Parser = newIperf3Parser;
        handler.post(parsingRunnable);
        CloudCityLogger.d(TAG, "<-- startParsingThread()");
    }

    private void stopParsingThread() {
        handler.removeCallbacks(parsingRunnable);
    }

    private void calculateAndLogMetrics() {
        double DLmin = normalize(defaultReverseThroughput.calcMin());
        double DLmedian = normalize(defaultReverseThroughput.calcMedian());
        double DLmax = normalize(defaultReverseThroughput.calcMax());
        double DLmean = normalize(defaultReverseThroughput.calcMean());
        double DLlast = normalize(getLast(defaultReverseThroughput.getMeanList()));

        double ULmin = normalize(defaultThroughput.calcMin());
        double ULmedian = normalize(defaultThroughput.calcMedian());
        double ULmax = normalize(defaultThroughput.calcMax());
        double ULmean = normalize(defaultThroughput.calcMean());
        double ULlast = normalize(getLast(defaultThroughput.getMeanList()));

        CloudCityLogger.d(TAG, "download speeds: MIN=" + DLmin + ", MED=" + DLmedian + ", MAX=" + DLmax + ", MEAN=" + DLmean + ", LAST=" + DLlast);
        CloudCityLogger.d(TAG, "upload speeds: MIN=" + ULmin + ", MED=" + ULmedian + ", MAX=" + ULmax + ", MEAN=" + ULmean + ", LAST=" + ULlast);
    }

    /**
     * Callback for when PingMonitor does a whole ping test cycle, reporting with the gathered metrics
     */
    public interface PingMonitorCompletionListener {
        /**
         * Called when the ping test was successfully completed
         *
         * @param metrics the gathered metrics during the test
         */
        void onPingTestCompleted(MetricsPOJO metrics);
    }
}
