package cloudcity;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import cloudcity.dataholders.PingMetricsPOJO;
import cloudcity.util.CloudCityLogger;
import cloudcity.util.CloudCityUtil;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Metric.METRIC_TYPE;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Metric.Metric;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Ping.PingInformations.PacketLossLine;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Ping.PingInformations.PingInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Ping.PingInformations.RTTLine;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Ping.PingParser;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Ping.PingWorker;

/**
 * The new single-source for programatically starting ping tests
 */
public class PingMonitor {
    private static final String TAG = "PingMonitor";

    private final static long PARSING_DELAY_IN_MS = 10L; //0.01sec, was 1000L

    private final static long PING_DELAY_IN_MS = 200L;

    private final static String DEFAULT_PING_ADDRESS = "8.8.8.8";

    private final static int DEFAULT_PING_PACKET_COUNT = 5;

    private Handler handler;

    private HandlerThread handlerThread;

    private static volatile PingMonitor instance;

    private Context appContext;

    private Metric rttMetric;

    private Metric packetLossMetric;

    private final AtomicBoolean shouldStop = new AtomicBoolean(false);

    private volatile PingParser pingParser;

    private final Set<OneTimeWorkRequest> pingWRs;

    private final AtomicBoolean pingTestRunning = new AtomicBoolean(false);

    private volatile @Nullable PingMonitorCompletionListener completionListener;

    /**
     * This is kinda terrible but will most likely get the job done.
     */
    private volatile long testStartTimestamp;
    private volatile long testEndTimestamp;

    /**
     * Runnable used for parsing ping tests by constantly calling {@link #pingParser}'s
     * {@link PingParser#parse()} method every {@link #PARSING_DELAY_IN_MS} until it reaches the end
     * or ordered to stop by toggling {@link #shouldStop} flag
     */
    private final Runnable parsingRunnable = new Runnable() {   //TODO get rid of this, we're not even using it
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
        pingWRs = Collections.synchronizedSet(new HashSet<>());
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
            synchronized (PingMonitor.class) {
                if (instance == null) {
                    instance = new PingMonitor();
                }
            }
        }

        instance.handlerThread = new HandlerThread("PingMonitorThread");
        instance.handlerThread.start();
        instance.handler = new Handler(instance.handlerThread.getLooper());
        instance.appContext = appContext;
        instance.pingWRs.clear();

        //TODO reconsider this bit, as it will collide with regular pinger
        // And clear up all occurances of work
//        wm.cancelAllWorkByTag("Ping");
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

    /**
     * Start a Ping test towards
     * @param completionListener
     */
    public void startPingTest(PingMonitorCompletionListener completionListener) {
        CloudCityLogger.d(TAG, "--> startPingTest()\tlistener: "+completionListener+"\tshouldStop: " + shouldStop.get());
        shouldStop.set(false);

        // Instantiate the metrics
        rttMetric = new Metric(METRIC_TYPE.PING_RTT, appContext);
        packetLossMetric = new Metric(METRIC_TYPE.PING_PACKET_LOSS, appContext);
        // I'm not sure we actually need these, but... why not.
        rttMetric.createMainLL("RTT [ms]");
        packetLossMetric.createMainLL("Packet Loss [%]");

        pingParser = PingParser.getInstance(null);
        CloudCityLogger.v(TAG, "Adding property change listener");
        pingParser.addPropertyChangeListener(evt -> {
            PingInformation pi = (PingInformation) evt.getNewValue();
//            CloudCityLogger.v(TAG, "propertyChange: "+pi+"\tline type: "+pi.getLineType());
            switch (pi.getLineType()) {
                case RTT:
                    rttMetric.update(((RTTLine) pi).getRtt());
                    break;
                case PACKET_LOSS:
                    packetLossMetric.update(((PacketLossLine) pi).getPacketLoss());
                    break;
            }
        });

        testStartTimestamp = System.currentTimeMillis();
        startPingThread(completionListener);
//        startParsingThread(pingParser);
        CloudCityLogger.d(TAG, "<-- startPingTest()\tlistener: " + completionListener + "\tshouldStop: " + shouldStop.get() + "\tisRunning: " + pingTestRunning.get());
    }

    private void startPingThread(PingMonitorCompletionListener completionListener) {
        CloudCityLogger.d(TAG, "--> startPingThread()");
        if (pingTestRunning.compareAndSet(false, true)) {
            this.completionListener = completionListener;
            handler.post(pingUpdate);
        } else {
            boolean currentRunningValue = pingTestRunning.get();
            CloudCityLogger.e(TAG, "wanted to try ping test but it's currentRunningValue was: "+currentRunningValue);
        }
        CloudCityLogger.d(TAG, "<-- startPingThread()");
    }

    private void startParsingThread(PingParser newPingParser) {
        CloudCityLogger.d(TAG, "--> startParsingThread()");
        pingParser = newPingParser;
        handler.post(parsingRunnable);
        CloudCityLogger.d(TAG, "<-- startParsingThread()");
    }

    private void stopParsingThread() {
        handler.removeCallbacks(parsingRunnable);
    }

    private final Runnable pingUpdate = new Runnable() {
        @Override
        public void run() {
            Data data = new Data.Builder()
                    .putString("input", DEFAULT_PING_ADDRESS)
                    .putString("count", String.valueOf(DEFAULT_PING_PACKET_COUNT))
                    .build();
            OneTimeWorkRequest pingWR =
                    new OneTimeWorkRequest.Builder(PingWorker.class)
                            .setInputData(data)
                            .addTag("Ping").build();
            pingWRs.add(pingWR);

            getWorkManager().beginWith(pingWR).enqueue();
            Observer observer = new Observer() {
                @Override
                public void onChanged(Object o) {
                    WorkInfo workInfo = (WorkInfo) o;
                    WorkInfo.State state = workInfo.getState();
                    CloudCityLogger.d(TAG, "Ping WorkRequest state changed! new state: "+state);
                    switch (state){
                        case RUNNING:
                        case ENQUEUED:
                            return;
                        case CANCELLED:
                        case SUCCEEDED:
                        case FAILED:
                            CloudCityLogger.d(TAG, "Work reached terminal state! state: "+state);
                            // both of these are terminal states, so we should fire a callback
                            testEndTimestamp = System.currentTimeMillis();
                            PingMetricsPOJO metrics = calculateAndLogMetrics(state);

                            // We should make up some MetricsPOJO object and fire it at the callback listener
                            if (completionListener != null) {
                                completionListener.onPingTestCompleted(metrics);
                            }
                            // Get rid of the updating thread which should have finished it's purpose already
                            handler.removeCallbacks(pingUpdate);
                            // and clear out the completionListener and the 'isRunning' flag
                            completionListener = null;
                            pingTestRunning.compareAndSet(true, false);
                    }

                    getWorkManager().getWorkInfoByIdLiveData(pingWR.getId()).removeObserver(this);
                    pingWRs.remove(pingWR);
                    //TODO consider if we should keep pinging or not, ideally some AtomicInt that lets us control
                    // how many times we actually want to ping
//                    handler.postDelayed(pingUpdate, PING_DELAY_IN_MS);
                }
            };
            // We cannot observe on a background thread, so lets do the same hack as in Iperf3Monitor
            MainThreadExecutor mainThreadExecutor = MainThreadExecutor.getInstance();
            mainThreadExecutor.execute(() -> getWorkManager().getWorkInfoByIdLiveData(pingWR.getId()).observeForever(observer));
        }
    };

    private WorkManager getWorkManager() {
        return WorkManager.getInstance(appContext);
    }

    private PingMetricsPOJO calculateAndLogMetrics(WorkInfo.State state) {
        double RTTmin = rttMetric.calcMin();
        double RTTmedian = rttMetric.calcMedian();
        double RTTmax = rttMetric.calcMax();
        double RTTmean = rttMetric.calcMean();
        double RTTlast = CloudCityUtil.getLastElementFromListOfDoubles(rttMetric.getMeanList());

        double PLmin = packetLossMetric.calcMin();
        double PLmedian = packetLossMetric.calcMedian();
        double PLmax = packetLossMetric.calcMax();
        double PLmean = packetLossMetric.calcMean();
        double PLlast = CloudCityUtil.getLastElementFromListOfDoubles(packetLossMetric.getMeanList());
        CloudCityLogger.d(TAG, "RTT speeds: MIN=" + RTTmin + ", MED=" + RTTmedian + ", MAX=" + RTTmax + ", MEAN=" + RTTmean + ", LAST=" + RTTlast);
        CloudCityLogger.d(TAG, "PL speeds: MIN=" + PLmin + ", MED=" + PLmedian + ", MAX=" + PLmax + ", MEAN=" + PLmean + ", LAST=" + PLlast);

        return new PingMetricsPOJO(
                new PingMetricsPOJO.RTTMetrics(RTTmin, RTTmedian, RTTmax, RTTmean, RTTlast),
                new PingMetricsPOJO.PackageLossMetrics(PLmin, PLmedian, PLmax, PLmean, PLlast),
                testStartTimestamp,
                testEndTimestamp,
                state == WorkInfo.State.SUCCEEDED
        );
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
        void onPingTestCompleted(PingMetricsPOJO metrics);
    }
}
