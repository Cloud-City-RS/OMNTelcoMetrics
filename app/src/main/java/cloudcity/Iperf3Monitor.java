package cloudcity;

import android.content.Context;
import android.location.Location;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.common.util.concurrent.ListenableFuture;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import cloudcity.dataholders.Iperf3MetricsPOJO;
import cloudcity.dataholders.Iperf3RunnerData;
import cloudcity.dataholders.PingMetricsPOJO;
import cloudcity.util.CloudCityLogger;
import cloudcity.util.CloudCityUtil;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.Iperf3Fragment;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.Iperf3Parser;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.Iperf3ParserNoFileToReadException;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.Iperf3ResultsDataBase;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.Iperf3RunResult;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.Iperf3RunResultDao;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.Iperf3ToLineProtocolWorker;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.Iperf3UploadWorker;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.Iperf3Worker;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.Error;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.Interval.Interval;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.Interval.Sum.SUM_TYPE;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.Interval.Sum.Sum;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.Interval.Sum.UDP.UDP_DL_SUM;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Metric.METRIC_TYPE;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Metric.Metric;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SPType;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SharedPreferencesGrouper;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.R;

/**
 * The new single-source for Iperf3 tests, which can be used to programatically read and parse Iperf3
 * results and start Iperf3 tests.
 * <p>
 * It has two moving parts - one for listening for test results and one for starting tests -
 * the internal {@link #parsingRunnable} and a bunch of synchronization necessary for all of that to
 * work together.
 *
 * @see #startListeningForIperf3Updates(Iperf3MonitorCompletionListener)
 * @see #startDefaultAutomatedTest(Location)
 */
public class Iperf3Monitor {
    /**
     * Threshold for how much time needs to pass since the last test started, in seconds.
     * Defaults to 5 minutes.
     */
    private static volatile long THROTTLING_THRESHOLD_IN_SECONDS = 5 * 60;

    /**
     * Threshold for how much distance we need to move since the last test, in meters.
     * Defaults to 0 meters
     */
    private static volatile float THROTTLING_THRESHOLD_IN_METERS = 0;

    private static final String TAG = "Iperf3Monitor";
    private static final String TAG_RESET = "Iperf3Monitor-ResetLatch";

    private final static long PARSING_DELAY_IN_MS = 10L; //0.01sec, was 1000L

    /**
     * If things go south or somehow break, the ResetLatch {@link #resetLatch} will mark the iperf3 test
     * as completed after this many seconds from a terminal state that was received in one place
     * but not the other that is actually in charge of *doing* important things.
     */
    private static final int RESET_LATCH_DURATION_IN_SECONDS = 100;

    private Handler handler;

    private HandlerThread handlerThread;

    private static volatile Iperf3Monitor instance;
    private Context appContext;

    private volatile Metric defaultThroughput;
    private volatile Metric defaultReverseThroughput;
    private volatile Metric defaultJITTER;
    private volatile Metric PACKET_LOSS;

    private final AtomicBoolean shouldStop = new AtomicBoolean(false);

    private volatile Iperf3Parser iperf3Parser;

    private final AtomicBoolean iperf3TestRunning = new AtomicBoolean(false);

    private volatile MainThreadExecutor mainThreadExecutor;

    private volatile Iperf3ResultsDataBase iperf3ResultsDatabase;

    /**
     * This is kinda terrible but will most likely get the job done.
     */
    private volatile long testStartTimestamp;
    private volatile long testEndTimestamp;

    private volatile @NonNull Location lastTestRunLocation;

    private volatile @NonNull PingMetricsPOJO lastPingTestMetrics;

    private volatile @Nullable CountDownTimer resetLatch;

    private volatile long lastEmissionFromDatabaseTimestampMillis;

    private volatile String lastEmissionFromDatabaseWorkUID;

    /**
     * Runnable used for parsing Iperf3 tests by constantly calling {@link #iperf3Parser}'s
     * {@link Iperf3Parser#parse()} method every {@link #PARSING_DELAY_IN_MS} until it reaches the end
     * or ordered to stop by toggling {@link #shouldStop} flag
     */
    private final Runnable parsingRunnable = new Runnable() {
        @Override
        public void run() {
            CloudCityLogger.v(TAG, "startParsingThread()::parsingCycle!\tshouldStop: " + shouldStop.get());

            // And finally, parse a bit of the file.
            iperf3Parser.parse();
            if (shouldStop.get()) {
                // well, do nothing but return
                return;
            } else {
                handler.postDelayed(this, PARSING_DELAY_IN_MS);
            }
        }
    };

    private Iperf3Monitor() {
        // private constructor to prevent instantiation
    }

    /**
     * Initializes the Iperf3Monitor, sets up the internal {@link Handler} and
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
                    instance = new Iperf3Monitor();
                }
            }
        }

        instance.handlerThread = new HandlerThread("Iperf3MonitorThread");
        instance.handlerThread.start();
        instance.handler = new Handler(instance.handlerThread.getLooper());
        instance.appContext = appContext;

        instance.iperf3ResultsDatabase = Iperf3ResultsDataBase.getDatabase(appContext);
    }

    /**
     * Check whether the Iperf3Monitor is initialized
     *
     * @return whether it's initialized or not, returns <i>true</i> if initialized or <i>false</i> otherwise
     */
    public static synchronized boolean isInitialized() {
        return instance != null;
    }

    /**
     * Returns the Iperf3Monitor instance, after calling {@link #initialize(Context)}
     *
     * @return the Iperf3Monitor instance
     */
    public static synchronized Iperf3Monitor getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Iperf3Monitor is not initialized. Must call initialize() first!");
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

    public void startListeningForIperf3Updates(Iperf3MonitorCompletionListener completionListener) {
        // Start listening for iPerf3 results...
        iperf3ResultsDatabase.iperf3RunResultDao().getLatestResult().observeForever(latestIperf3RunResult -> {
            shouldStop.set(false);
            // Actually, since these come from the DB, the latest Iperf3 result will be the *last executed Iperf3 test*
            // So if no Iperf3 tests were ran ever, the first result will naturally be 'null' because there's nothing
            // in the DB.
            CloudCityLogger.d(TAG, "Latest iPerf3 emission: " + latestIperf3RunResult + "\tshouldStop: " + shouldStop.get());
            // The first emmision is always a null so...
            if (latestIperf3RunResult != null) {
                CloudCityLogger.v(TAG, "Latest iPerf3 result is: " + latestIperf3RunResult.result);
                lastEmissionFromDatabaseTimestampMillis = System.currentTimeMillis();
                lastEmissionFromDatabaseWorkUID = latestIperf3RunResult.uid;

                // Check the reset latch
                if (resetLatch != null) {
                    resetLatch.cancel();
                    resetLatch = null;
                    CloudCityLogger.d(TAG, "ResetLatch cancel()-ed and nulled out!");
                    CloudCityLogger.d(TAG_RESET, "ResetLatch cancel()-ed and nulled out!");
                }

                // 0 is the magic number for success
                if (latestIperf3RunResult.result == 0) {
                    CloudCityLogger.v(TAG, "Result was fine, commencing further...");
                    if (latestIperf3RunResult.input != null) {
                        CloudCityLogger.v(TAG, "Latest result's rawIperf3file is: " + latestIperf3RunResult.input.iperf3rawIperf3file);
                    } else {
                        CloudCityLogger.v(TAG, "Latest result's input was: NULL");
                    }

                    // Copy the file, for just in case
                    // This is useful for when we run the test and open it up in the default Iperf3 log viewer
                    // since the Iperf3Parser might be destructively parsing the file - from what I noticed only
                    // one of them reaches the END
                    String originalFilePath = latestIperf3RunResult.input.iperf3rawIperf3file;
                    String targetFilePath = originalFilePath + ".tmp";

                    try {
                        copyFile(originalFilePath, targetFilePath);
                    } catch (IOException e) {
                        CloudCityLogger.e(TAG, "Exception " + e + " happened during iperf3 raw file copy!", e);
                        throw new RuntimeException(e);
                    }

                    CloudCityLogger.v(TAG, "File copying should be done, instantiating new parser with rawIperf3File: " + targetFilePath);

                    Iperf3Parser iperf3Parser = null;
                    try {
                        iperf3Parser = Iperf3Parser.instantiate(targetFilePath);
                    } catch (Iperf3ParserNoFileToReadException e) {
                        CloudCityLogger.e(TAG, "Exception " + e + " happened during iperf3 parser instantiation!", e);
                        throw new RuntimeException(e);
                    }
                    defaultThroughput = new Metric(METRIC_TYPE.THROUGHPUT, appContext);
                    defaultReverseThroughput = new Metric(METRIC_TYPE.THROUGHPUT, appContext);
                    defaultJITTER = new Metric(METRIC_TYPE.JITTER, appContext);
                    PACKET_LOSS = new Metric(METRIC_TYPE.PACKET_LOSS, appContext);
                    // Obviously, these things need to be added or nothing will work... ffs...
                    defaultThroughput.createMainLL("Throughput");
                    defaultReverseThroughput.createMainLL("Throughput");
                    defaultJITTER.createMainLL("Jitter ms");
                    PACKET_LOSS.createMainLL("Packet Loss %");
                    // With this retarded problem out of the way... we can move on.

                    CloudCityLogger.v(TAG, "Adding property change listener");
                    iperf3Parser.addPropertyChangeListener(new PropertyChangeListener() {
                        private void parseSum(Sum sum, Metric throughput) {
                            SUM_TYPE sumType = sum.getSumType();
                            throughput.update(sum.getBits_per_second());
                            switch (sumType) {
                                case UDP_DL:
                                    defaultJITTER.update(((UDP_DL_SUM) sum).getJitter_ms());
                                    PACKET_LOSS.update(((UDP_DL_SUM) sum).getLost_percent());
                                case TCP_DL:
                                    if (throughput.getDirectionName().getText().equals("Throughput")) {
                                        throughput.getDirectionName().setText("Downlink Mbit/s");
                                    }
                                    break;
                                case UDP_UL:
                                case TCP_UL:
                                    if (throughput.getDirectionName().getText().equals("Throughput")) {
                                        throughput.getDirectionName().setText("Uplink Mbit/s");
                                    }
                                    break;
                            }
                        }

                        public void propertyChange(PropertyChangeEvent evt) {
                            CloudCityLogger.d(TAG, "--> onPropertyChange()\tchanged event: " + evt);

                            switch (evt.getPropertyName()) {
                                case "interval": {
                                    Interval interval = (Interval) evt.getNewValue();
                                    parseSum(interval.getSum(), defaultThroughput);
                                    if (interval.getSumBidirReverse() != null) {
                                        parseSum(interval.getSumBidirReverse(), defaultReverseThroughput);
                                    }

                                    CloudCityLogger.v(TAG, "INTERVAL\tdefaultThroughput size: " + defaultThroughput.getMeanList().size() + ", defaultReverseThroughput size: " + defaultReverseThroughput.getMeanList().size());
                                }
                                break;

                                case "start": {
                                    CloudCityLogger.v(TAG, "START\tdefaultThroughput: " + defaultThroughput + ", defaultReverseThroughput: " + defaultReverseThroughput);
                                }
                                break;

                                case "end": {
                                    CloudCityLogger.v(TAG, "END\tdefaultThroughput size: " + defaultThroughput.getMeanList().size() + ", defaultReverseThroughput size: " + defaultReverseThroughput.getMeanList().size());
                                    CloudCityLogger.v(TAG, "END\tend value is: " + evt.getNewValue() + "\t\tequals END_MARKER ? " + evt.getNewValue().equals(Iperf3Parser.END_MARKER));

                                    //NOTE: while this is exactly the same as calculateAndLogMetrics(), we need these values here
                                    // so we can pass them to the Iperf3MonitorCompletionListener
                                    //
                                    // Throughput values need to be normalized by dividing by 1e6 so => realValue = value/1e+6
                                    double DLmin = normalize(defaultReverseThroughput.calcMin());
                                    double DLmedian = normalize(defaultReverseThroughput.calcMedian());
                                    double DLmax = normalize(defaultReverseThroughput.calcMax());
                                    double DLmean = normalize(defaultReverseThroughput.calcMean());
                                    double DLlast = normalize(CloudCityUtil.getLastElementFromListOfDoubles(defaultReverseThroughput.getMeanList()));

                                    double ULmin = normalize(defaultThroughput.calcMin());
                                    double ULmedian = normalize(defaultThroughput.calcMedian());
                                    double ULmax = normalize(defaultThroughput.calcMax());
                                    double ULmean = normalize(defaultThroughput.calcMean());
                                    double ULlast = normalize(CloudCityUtil.getLastElementFromListOfDoubles(defaultThroughput.getMeanList()));

                                    CloudCityLogger.d(TAG, "download speeds: MIN=" + DLmin + ", MED=" + DLmedian + ", MAX=" + DLmax + ", MEAN=" + DLmean + ", LAST=" + DLlast);
                                    CloudCityLogger.d(TAG, "upload speeds: MIN=" + ULmin + ", MED=" + ULmedian + ", MAX=" + ULmax + ", MEAN=" + ULmean + ", LAST=" + ULlast);

                                    // Stop the thread to avoid spinning it needlessly forever...
                                    stopParsingThread();
                                    shouldStop.compareAndSet(false, true);

                                    testEndTimestamp = System.currentTimeMillis();
                                    PingMetricsPOJO.MetricsPair pingMetricsPair = lastPingTestMetrics.toMetricsPair();
                                    // Instantiate the POJO stuff holder
                                    Iperf3MetricsPOJO values = new Iperf3MetricsPOJO(
                                            new Iperf3MetricsPOJO.DownloadMetrics(DLmin, DLmedian, DLmean, DLmax, DLlast),
                                            new Iperf3MetricsPOJO.UploadMetrics(ULmin, ULmedian, ULmean, ULmax, ULlast),
                                            pingMetricsPair.getRTTMetrics(),
                                            pingMetricsPair.getPackageLossMetrics(),
                                            testStartTimestamp,
                                            testEndTimestamp
                                    );

                                    finalizeTestExecution(completionListener, values);

                                    CloudCityLogger.v(TAG, "END\tcleaned up everything! shouldStop: " + shouldStop.get() + ", iperf3TestRunning: " + iperf3TestRunning.get());
                                }
                                break;

                                case "error": {
                                    Error error = (Error) evt.getNewValue();
                                    TextView errorView = new TextView(appContext);
                                    errorView.setText(error.getError());
                                    errorView.setTextColor(appContext.getColor(R.color.crimson));
                                    errorView.setPadding(10, 10, 10, 10);
                                    errorView.setTextSize(20);
//                                metricLL.addView(errorView);
                                }
                                break;
                            }
                        }
                    });
                    CloudCityLogger.v(TAG, "Adding completion listener");
                    iperf3Parser.setCompletionListener(new Iperf3Parser.Iperf3ParserCompletionListener() {
                        @Override
                        public void onParseCompleted() {
                            CloudCityLogger.v(TAG, "---> onParseCompleted");

                            calculateAndLogMetrics();
                            stopParsingThread();

                            // And finally delete the temp copy of the file
                            deleteFile(targetFilePath);
                        }
                    });


                    CloudCityLogger.d(TAG, "Starting parsing...");
                    startParsingThread(iperf3Parser);
                } else {
                    // This could be either a 1, or a -100; first being a failure, the second one being a 'in progress' value.
                    CloudCityLogger.d(TAG, "latestIperf3RunResult.result was: " + latestIperf3RunResult.result);
                    // We want to reset the test running marker on any non -100 result as well,
                    // since -100 means it's still running, and apparently anything non-zero means completion
                    // 1 for failure, -1 for cancellation and who knows what else
                    if (latestIperf3RunResult.result != -100) {
                        CloudCityLogger.d(TAG, "latestIperf3RunResult.result was actually terminal, finishing iperf3 test run");
                        iperf3TestRunning.compareAndSet(true, false);
                    }
                }
            }
        });
    }

    private void finalizeTestExecution(Iperf3MonitorCompletionListener completionListener, Iperf3MetricsPOJO values) {
        CloudCityLogger.v(TAG, "--> finalizeTestExecution(completionListener=" + completionListener + ", values=" + values + ")");
        // Stop the thread to avoid spinning it needlessly forever...
        stopParsingThread();
        shouldStop.compareAndSet(false, true);

        // Notify the completion listener
        if (completionListener != null && values != null) {
            completionListener.onIperf3TestCompleted(values);
        }

        // And set the new marker as 'no longer running'
        iperf3TestRunning.compareAndSet(true, false);
        lastPingTestMetrics = null;
        CloudCityLogger.v(TAG, "<-- finalizeTestExecution()");
    }

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
        double DLlast = normalize(CloudCityUtil.getLastElementFromListOfDoubles(defaultReverseThroughput.getMeanList()));

        double ULmin = normalize(defaultThroughput.calcMin());
        double ULmedian = normalize(defaultThroughput.calcMedian());
        double ULmax = normalize(defaultThroughput.calcMax());
        double ULmean = normalize(defaultThroughput.calcMean());
        double ULlast = normalize(CloudCityUtil.getLastElementFromListOfDoubles(defaultThroughput.getMeanList()));

        CloudCityLogger.d(TAG, "download speeds: MIN=" + DLmin + ", MED=" + DLmedian + ", MAX=" + DLmax + ", MEAN=" + DLmean + ", LAST=" + DLlast);
        CloudCityLogger.d(TAG, "upload speeds: MIN=" + ULmin + ", MED=" + ULmedian + ", MAX=" + ULmax + ", MEAN=" + ULmean + ", LAST=" + ULlast);
    }

    /**
     * Callback for when Iperf3Monitor does a whole Iperf3 test cycle, reporting with the gathered metrics
     */
    public interface Iperf3MonitorCompletionListener {
        /**
         * Called when the Iperf3 test was successfully completed
         *
         * @param metrics the gathered metrics during the test
         */
        void onIperf3TestCompleted(Iperf3MetricsPOJO metrics);
    }

    /**
     * Copies a file designated by <i>sourceFileName</i> file path to the <i>destFileName</i> path
     * <br>
     * Throws IOException if something goes wrong.
     *
     * @param sourceFileName path to the source file
     * @param destFileName   path to the destination file, where the file should be copied to (including the new name)
     * @throws IOException
     */
    public static void copyFile(String sourceFileName, String destFileName) throws IOException {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(sourceFileName);
            fos = new FileOutputStream(destFileName);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        } finally {
            if (fis != null) {
                fis.close();
            }
            if (fos != null) {
                fos.close();
            }
        }
    }

    /**
     * Deletes file designated by <i>filePath</i>
     *
     * @param filePath the file to delete
     * @return whether deletion was successful or not
     */
    public static boolean deleteFile(String filePath) {
        File file = new File(filePath);
        boolean retVal;
        if (file.exists()) {
            if (file.delete()) {
                CloudCityLogger.d(TAG, "File deleted successfully! filepath: " + filePath);
                retVal = true;
            } else {
                CloudCityLogger.d(TAG, "Failed to delete the file at filepath: " + filePath);
                retVal = false;
            }
        } else {
            CloudCityLogger.w(TAG, "File at path " + filePath + " does not exist!");
            retVal = false;
        }

        return retVal;
    }

    /**
     * Normalizes the value by dividing by 1e+6
     *
     * @param value the value to normalize
     * @return the normalized value, divided by 1e+6 (1000000)
     */
    private double normalize(double value) {
        return value / 1e+6;
    }

    /**
     * Setter for the {@link #THROTTLING_THRESHOLD_IN_SECONDS} throttling constant,
     * and also returns the new value of that parameter
     * @param newThrottlingValueInSeconds the new value to use for {@link #THROTTLING_THRESHOLD_IN_SECONDS}
     * @return the new (current) value of that parameter
     */
    public long setTimeThrottlingThreshold(int newThrottlingValueInSeconds) {
        CloudCityLogger.d(TAG, "Setting new THROTTLING_THRESHOLD_IN_SECONDS to "+newThrottlingValueInSeconds);
        THROTTLING_THRESHOLD_IN_SECONDS = newThrottlingValueInSeconds;
        return THROTTLING_THRESHOLD_IN_SECONDS;
    }

    /**
     * Setter for the {@link #THROTTLING_THRESHOLD_IN_METERS} throttling constant,
     * and also returns the new value of that parameter
     * @param newThrottlingValueInMeters the new value to use for {@link #THROTTLING_THRESHOLD_IN_METERS}
     * @return the new (current) value of that parameter
     */
    public float setDistanceThrottlingThreshold(float newThrottlingValueInMeters) {
        CloudCityLogger.d(TAG, "Setting new THROTTLING_THRESHOLD_IN_METERS to "+newThrottlingValueInMeters);
        THROTTLING_THRESHOLD_IN_METERS = newThrottlingValueInMeters;
        return THROTTLING_THRESHOLD_IN_METERS;
    }

    /**
     * Runs the default CloudCity automated iperf3 test when the movement speed is under the threshold
     * @param testRunLocation
     */
    public void startDefaultAutomatedTest(Location testRunLocation) {
        // Sanity check
        if (iperf3TestRunning.get()) {
            CloudCityLogger.e(TAG, "Iperf3 test is still running! aborting attempt and returning...");

            // Try another failsafe here for just in case, because the current one isn't sure-proof
            long currentTimeMillis = System.currentTimeMillis();
            long diffInSeconds = (currentTimeMillis - lastEmissionFromDatabaseTimestampMillis) / 1000L;
            if (diffInSeconds > 2 * RESET_LATCH_DURATION_IN_SECONDS) {
                CloudCityLogger.e(TAG, "Last emission from database is more than twice the RESET_LATCH_DURATION seconds ago and test is still running! Aborting test and everything on WorkManager!");
                // Nuke the last running test
                CloudCityLogger.v(TAG, "Cancelling last work UID "+lastEmissionFromDatabaseWorkUID+" from WorkManager");
                getWorkManager().cancelAllWorkByTag(lastEmissionFromDatabaseWorkUID);
                // Cancel all of the UIDs actually
                List<String> workerUIDs = iperf3ResultsDatabase.iperf3RunResultDao().getIDs();
                CloudCityLogger.v(TAG, "Cancelling list of UIDs: "+workerUIDs+" from WorkManager");
                for(String workerUID : workerUIDs) {
                    // While we *could* use cancelAllWorkByUUID(), we won't because we are adding their UUIDs
                    // as Worker tags via addTag() when enqueue()-ing them onto the WorkManager
                    getWorkManager().cancelAllWorkByTag(workerUID);
                }
                // Nuke everything else iperf3-related from the WorkManager - would be great to just use cancelAllWork() but that will kill more than just iperf3
                // JUST MAKE SURE THESE ARE THE SAME AS IN Iperf3Fragment for enqueue() methods calls in executeIperfCommand()!!!
                // actually, we enqueue() them in the Monitor, so... try to keep the two identical, if possible
                CloudCityLogger.v(TAG, "Cancelling ALL Workers with the three tags from WorkManager");
                getWorkManager().cancelAllWorkByTag("iperf3Run");
                getWorkManager().cancelAllWorkByTag("iperf3LineProtocol");
                getWorkManager().cancelAllWorkByTag("iperf3");
                // And finally, 'finish' the test (altho these WM cancellations will likely do so as well, so perhaps this messes things up)
                CloudCityLogger.v(TAG, "Calling finalizeTestExecution() to unset the lock flag");
                finalizeTestExecution(null, null);
            }
            return;
        }

        LinkedList<String> cmdList = new LinkedList<>();
        // Add server
        cmdList.add("-c");
        String iperf3ServerIP = CloudCityConstants.CLOUD_CITY_IPERF3_SERVER;
        cmdList.add(iperf3ServerIP);
        cmdList.add("-p");
        // Calculate random port
        int validPortMin = CloudCityConstants.CLOUD_CITY_IPERF3_VALID_PORT_MIN;
        int validPortMax = CloudCityConstants.CLOUD_CITY_IPERF3_VALID_PORT_MAX;
        Random rnd = new Random();
        int randomPort = rnd.nextInt((validPortMax - validPortMin) + 1) + validPortMin;
        String randomPortStr = String.valueOf(randomPort);
        // Add port
        cmdList.add(randomPortStr);
        // Add duration
        cmdList.add("-t");
        String duration = String.valueOf(CloudCityConstants.CLOUD_CITY_IPERF3_TEST_DURATION_IN_SECONDS);
        cmdList.add(duration);
        // Protocol
        String protocol = "TCP";
        // Generate file
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String timestampStr = timestamp.toString();
        String iperf3TS = timestampStr.replace(" ", "_").replace(":", "_");

        String uuid = UUID.randomUUID().toString();

        String logFileName = String.format("iperf3_%s_%s.json", iperf3TS, uuid);

        String logFileDir =
                Environment
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                        .getAbsolutePath() + "/omnt/iperf3RawLogs/";

        String rawIperf3file = logFileDir + logFileName;

        cmdList.add("--logfile");
        cmdList.add(rawIperf3file);

        // Turn on bidirectional test (test download and upload speed)
        cmdList.add("--bidir");

        // Add the rest of the command
        cmdList.add("--json-stream");

        cmdList.add("--connect-timeout");
        cmdList.add("500");

        String joined = String.join(" ", cmdList);

        CloudCityLogger.d(TAG, "Joined command " + joined);

        // Generate a key-value hashmap to hold the rest of the necessary duplicated crap
        HashMap<String, String> stringMap = new HashMap<>();

        stringMap.put("cport", null);
        stringMap.put("bandwidth", null);
        stringMap.put("rawIperf3file", rawIperf3file);
        stringMap.put("iperf3WorkerID", uuid);
        stringMap.put("ip", iperf3ServerIP);
        stringMap.put("measurementName", "Iperf3");
        stringMap.put("duration", duration);
        stringMap.put("protocol", protocol);
        stringMap.put("port", randomPortStr);
        stringMap.put("bytes", null);
        stringMap.put("client", "Client");
        stringMap.put("interval", null);
        // The following values cannot be put into a <String,String> map and will need to be handled
        // differently; however they are still here for completeness sake and to show that we still
        // have everything mentioned in {@link Iperf3Fragment#executeIperfCommand(View)} method
//        map.put("commands", cmdList); //This String[] is passed separately
//        map.put("rev", false)     //Will be passed in the other map
//        map.put("oneOff", false); //Will be passed in the other map
//        map.put("biDir", true);   //Will be passed in the other map
        stringMap.put("timestamp", timestampStr);

        // Utility elements
        stringMap.put("uuid", uuid);
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath() + "/omnt/iperf3LP/";
        stringMap.put("path", path);
        String iperf3LineProtocolFile = path + uuid + ".txt";
        stringMap.put("iperf3LineProtocolFile", iperf3LineProtocolFile);

        // Boolean elements in the new boolean map
        HashMap<String, Boolean> boolMap = new HashMap<>();
        boolMap.put("rev", false);
        boolMap.put("oneOff", false);
        boolMap.put("biDir", true);
        boolMap.put("iperf3Json", true);

        // All of this would have been great... but this Iperf3Fragment.InputData is fucking hardcoded
        // into the database schema, and by *not* using it we're just literally shooting ourselves
        // in the foot. So we have to use that as well.... sheesh.
        Iperf3Fragment.Iperf3Input input = new Iperf3Fragment.Iperf3Input();
        input.iperf3IdxProtocol = protocol.equalsIgnoreCase("TCP") ? 0 : 1;
        input.timestamp = timestamp;
        input.uuid = uuid;
        input.measurementName = "Iperf3";
        input.iperf3LogFileName = logFileName;
        input.iperf3rawIperf3file = rawIperf3file;
        input.iperf3BiDir = true;
        input.iperf3Reverse = false;
        input.iperf3OneOff = false;
        input.iperf3Json = true;
        input.iperf3BiDir = true;
        input.iperf3Command = joined;
        input.iperf3LineProtocolFile = iperf3LineProtocolFile;

        // This array here is used just to provide a type, we could provide a new array that is of
        // the same size as the LinkedList, so that the toArray() method would return elements in
        // that particular array, but there's negligable gain from doing that over this approach
        Iperf3RunnerData testData = new Iperf3RunnerData(
                joined,
                cmdList.toArray(new String[0]),
                stringMap,
                boolMap,
                timestamp,
                input
        );

        startIperf3Test(appContext, testData, testRunLocation);
    }

    /**
     * Method that will start the iPerf3 test; will also check if the test should be "throttled"
     * meaning, if there has been less than <i>user-configurable time</i> seconds before the last test
     * was started, the test will <b>not be started</b>
     *
     * @param applicationContext the application {@link Context}
     * @param data               the data required to run the test
     * @param testRunLocation    the {@link Location} where the test is being ran
     */
    private void startIperf3Test(Context applicationContext, Iperf3RunnerData data, Location testRunLocation) {
        // Check for throttling
        if (lastTestWasStartedLessThanThrottlingDistanceAway(testRunLocation)) {
            // If we haven't moved more than throttling distance away, log and return
            if (testRunLocation != null && lastTestRunLocation != null) {
                // We can calculate distances only if both are non-null
                float distance = testRunLocation.distanceTo(lastTestRunLocation);
                CloudCityLogger.w(TAG, "Last test was started only " + distance + " meters away, which is shorter than THROTTLING_THRESHOLD_IN_METERS of " + THROTTLING_THRESHOLD_IN_METERS + " meters! Cancelling test run!");
            } else {
                // This can't ever happen, but is here just for completeness sake.
                // If any of these two locations are null, the lastTestWasStartedLessThanThrottlingDistanceAway()
                // returns 'false' and we will not be here
                CloudCityLogger.wtf(TAG, "Well, what do you know. The impossible has happened.");
            }
            return;
        }

        if (lastTestWasStartedLessThanThrottlingTimeAgo()) {
            // Log and return
            long now = System.currentTimeMillis();
            long diffToLastTestInMillis = now - testStartTimestamp;
            long diffInSeconds = diffToLastTestInMillis / 1000L;
            CloudCityLogger.w(TAG, "Last test was started only " + diffInSeconds + " seconds ago, which is shorter than THROTTLING_THRESHOLD_IN_SECONDS of " + THROTTLING_THRESHOLD_IN_SECONDS + " seconds ago! Cancelling test run!");
            return;
        }
        // Init the database
        Iperf3RunResultDao iperf3RunResultDao = iperf3ResultsDatabase.iperf3RunResultDao();
        // Do everything else needed for the test
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath() + "/omnt/iperf3LP/";

        try {
            Files.createDirectories(Paths.get(path));
        } catch (IOException e) {
            CloudCityLogger.e(TAG, "Could not create Dir files!");
        }

        // Fill up the WorkManager's worker data
        Data.Builder iperf3Data = new Data.Builder();
        iperf3Data.putStringArray("commands", data.getCommandList());
        // Iterate the map, and fill the data as-is
        for (String mapKey : data.getStringDataMap().keySet()) {
            String mapValue = data.getStringDataMap().get(mapKey);
            iperf3Data.putString(mapKey, mapValue);
        }
        // Finally, put the booleans in there too
        for (String mapKey : data.getBooleanDataMap().keySet()) {
            Boolean mapValue = data.getBooleanDataMap().get(mapKey);
            // We do this weird equals check to avoid potential unboxing problems
            // because TRUE.equals(null) is still false
            iperf3Data.putBoolean(mapKey, Boolean.TRUE.equals(mapValue));
        }

        WorkManager iperf3WM = getWorkManager();
        ListenableFuture<List<WorkInfo>> status = iperf3WM.getWorkInfosByTag("iperf3Run");

        String iperf3WorkerID = data.getStringDataMap().get("uuid");

        OneTimeWorkRequest iperf3WR =
                new OneTimeWorkRequest
                        .Builder(Iperf3Worker.class)
                        .setInputData(iperf3Data.build())
                        .addTag("iperf3Run")
                        .addTag(iperf3WorkerID)
                        .build();
        OneTimeWorkRequest iperf3LP =
                new OneTimeWorkRequest
                        .Builder(Iperf3ToLineProtocolWorker.class)
                        .setInputData(iperf3Data.build())
                        .addTag("iperf3LineProtocol")
                        .addTag(iperf3WorkerID)
                        .build();
        OneTimeWorkRequest iperf3UP =
                new OneTimeWorkRequest
                        .Builder(Iperf3UploadWorker.class)
                        .setInputData(iperf3Data.build())
                        .addTag("iperf3")
                        .addTag(iperf3WorkerID)
                        .build();

        iperf3RunResultDao.insert(
                new Iperf3RunResult(iperf3WorkerID, -100, false, data.getCityInput(), data.getTimestamp()));

        boolean iperf3Json = Boolean.TRUE.equals(data.getBooleanDataMap().get("iperf3Json"));

        // Sanity check...
        if (iperf3TestRunning.get()) {
            // IF test is running, log error, and return
            CloudCityLogger.e(TAG, "Iperf3 test was already running! Returning...");
            return;
        }

        SharedPreferencesGrouper spg = SharedPreferencesGrouper.getInstance(applicationContext);


        // Check if we have a main thread executor
        if (mainThreadExecutor == null) {
            mainThreadExecutor = MainThreadExecutor.getInstance();
        }

        PingMonitor.getInstance().startPingTest(metrics -> {
            boolean wasSuccess = metrics.wasSuccess();
            boolean destinationReachable = metrics.isDestinationReachable();
            // FIXME sometimes this 'wasSuccess' is false when the 'destinationReachable' is true
            // and this is because the PingWorker fails for some unknown reason while it manages
            // to calculate everything correctly.
            //
            // While we could use an OR here, I strongly believe that a ping test would finish
            // 'successfully' even if all packets are lost and the destination isn't reachable
            // So lets enforce both, which *may* lead us to "drop" iperf3 test executions
            // but a subsequent ping a few seconds later (when re-queried by GPSListener) should
            // manage to get here just fine, since we save last start timestamp only immediatelly
            // before starting the iperf3 test - that is, after a successful ping test.
            if (wasSuccess && destinationReachable) {
                CloudCityLogger.d(TAG, "Ping test was succesful, starting iperf3 test");

                // Start the iperf3 test
                if (iperf3TestRunning.compareAndSet(false, true)) {
                    // Set the startTimestamp
                    testStartTimestamp = System.currentTimeMillis();
                    lastTestRunLocation = testRunLocation;
                    lastPingTestMetrics = metrics;

                    // Enqueue tasks onto the WorkManager
                    if (spg.getSharedPreference(SPType.logging_sp).getBoolean("enable_influx", false) && iperf3Json) {
                        iperf3WM.beginWith(iperf3WR).then(iperf3LP).then(iperf3UP).enqueue();
                    } else if (iperf3Json) {
                        iperf3WM.beginWith(iperf3WR).then(iperf3LP).enqueue();
                    } else {
                        iperf3WM.beginWith(iperf3WR).enqueue();
                    }
                } else {
                    CloudCityLogger.w(TAG, "Ping test was unsuccesful or destination was not reachable, skipping iperf3 test!\twasSuccess: " + wasSuccess + ", destinationReachable: " + destinationReachable);
                    //TODO fire an Sentry event to track this erroneous state, because when things fuck up - we see the change here but not in the expected place
                    //TODO come up with some fallback plan on how to 'unset' that the iperf3 test is still running because it most certainly isn't.
                }
            }
        });

        mainThreadExecutor.execute(() -> {
            getWorkManager().getWorkInfoByIdLiveData(iperf3WR.getId()).observeForever(workInfo -> {
                int iperf3_result;
                iperf3_result = workInfo.getOutputData().getInt("iperf3_result", -100);
                if (workInfo.getState().equals(WorkInfo.State.CANCELLED)) {
                    iperf3_result = -1;
                }
                iperf3RunResultDao.updateResult(iperf3WorkerID, iperf3_result);
                CloudCityLogger.d(TAG, "onChanged: iperf3_result: " + iperf3_result);
                // This little thing here is when we hear that the test is over (and update the database DAO)
                // but since we're actually doing everything important to the iperf3 test based on the database
                // emissions, sometimes these emissions just... don't happen.
                //
                // While we 'hear' that the test has ended here, we just don't hear it in the other place
                // and the Iperf3Monitor remains stuck as if it's still performing an iperf3 test
                // while in reality nothing is actually working.
                //
                // This little dirty hack will just "force finish" the test if it's been in terminal state
                // for RESET_LATCH_DURATION (100) seconds, and hopefully we'll be able to resume iperf3
                // tests on the next GPSMonitor's call.
                if (isTerminalState(iperf3_result)) {
                    // Give it 100 seconds to unset that test is running. If it doesn't manage to do
                    // that - because we will clear this latch in the next emission of anything - proceed
                    // to finalize the test with no data so that the system (iperf3 monitor) resets
                    // itself back into a working state
                    CloudCityLogger.d(TAG, "Initializing ResetLatch to unblock the Iperf3Monitor in " + RESET_LATCH_DURATION_IN_SECONDS + " seconds\tresetLatch = " + resetLatch);
                    CloudCityLogger.d(TAG_RESET, "Initializing ResetLatch to unblock the Iperf3Monitor in " + RESET_LATCH_DURATION_IN_SECONDS + " seconds\tresetLatch = " + resetLatch);
                    // Avoid having more than one resetLatch
                    if (resetLatch == null) {
                        resetLatch = new CountDownTimer(RESET_LATCH_DURATION_IN_SECONDS * 1000L, (RESET_LATCH_DURATION_IN_SECONDS / 10) * 1000L) {

                            @Override
                            public void onTick(long millisUntilFinished) { /* nothing here, we don't care about this*/}

                            @Override
                            public void onFinish() {
                                CloudCityLogger.e(TAG, "ResetLatch finalizing test after 100 seconds of being broken, something went wrong or was broken!!!");
                                finalizeTestExecution(null, null);
                            }
                        }.start();
                    }
                }
            });
            getWorkManager().getWorkInfoByIdLiveData(iperf3UP.getId()).observeForever(workInfo -> {
                boolean iperf3_upload;
                iperf3_upload = workInfo.getOutputData().getBoolean("iperf3_upload", false);
                CloudCityLogger.d(TAG, "onChanged: iperf3_upload: " + iperf3_upload);
                iperf3RunResultDao.updateUpload(iperf3WorkerID, iperf3_upload);
            });
        });
    }

    /**
     * Method that checks whether last test was started at least {@link #THROTTLING_THRESHOLD_IN_SECONDS} seconds ago
     *
     * @return false if it was started more than threshold seconds ago, true if it was started less than threshold seconds ago and should be filtered
     */
    private boolean lastTestWasStartedLessThanThrottlingTimeAgo() {
        long now = System.currentTimeMillis();
        long diffToLastTestInMillis = now - testStartTimestamp;
        long diffInSeconds = diffToLastTestInMillis / 1000L;
        return diffInSeconds < THROTTLING_THRESHOLD_IN_SECONDS;
    }

    /**
     * Method that checks whether last test was started at least {@link #THROTTLING_THRESHOLD_IN_METERS} meters away.
     * It really just checks whether the distance between {@code thisRunLocation} and {@link #lastTestRunLocation} is
     * less than threshold and returns if the test should be filtered (throttled) or not.
     *
     * @param thisRunLocation the {@link Location} where this test should be started
     * @return false if it was started more than threshold meters away, true if it was started less than threshold meters away and should be filtered
     */
    private boolean lastTestWasStartedLessThanThrottlingDistanceAway(@NonNull Location thisRunLocation) {
        // Don't be mistaken by this 'lastTestRunLocation == null is always false' warning, it'll be null
        // until it's initially set, and on first check of this method it will most surely still be null
        //
        // Same thing goes for 'thisRunLocation' as well, because the annotation cannot *enforce* them being
        // non-null at runtime, it can only force compile-time non-nullability.
        if (lastTestRunLocation == null || thisRunLocation == null) {
            // If any of the two locations are null, don't filter
            return false;
        } else {
            return thisRunLocation.distanceTo(lastTestRunLocation) < THROTTLING_THRESHOLD_IN_METERS;
        }
    }

    /**
     * Method that checks whether the iperf3 execution state is a terminal state.
     * Due to various conditions, all non -100 results are terminal, where only 0 is a
     * successful termination, 1 is failure, -1 is cancellation and who knows how many others there are
     *
     * @param state the state to evaluate
     * @return whether the state was the terminal (last) emission or not
     */
    private boolean isTerminalState(int state) {
        return state != -100;
    }

    private WorkManager getWorkManager() {
        return WorkManager.getInstance(appContext);
    }
}
