package cloudcity;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import cloudcity.dataholders.MetricsPOJO;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.Iperf3Parser;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.Iperf3ResultsDataBase;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.Error;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.Interval.Interval;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.Interval.Sum.SUM_TYPE;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.Interval.Sum.Sum;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.Interval.Sum.UDP.UDP_DL_SUM;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Metric.METRIC_TYPE;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Metric.Metric;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.R;

public class Iperf3Monitor {
    private static final String TAG = Iperf3Monitor.class.getSimpleName();

    private final static long PARSING_DELAY_IN_MS = 10L; //0.01sec, was 1000L

    private Handler handler;

    private HandlerThread handlerThread;

    private static volatile Iperf3Monitor instance;

    private volatile Metric defaultThroughput;
    private volatile Metric defaultReverseThroughput;
    private volatile Metric defaultJITTER;
    private volatile Metric PACKET_LOSS;

    private final AtomicBoolean shouldStop = new AtomicBoolean(false);

    private Iperf3Monitor() {
        // private constructor to prevent instantiation
    }

    /**
     * Initializes the Iperf3Monitor, sets up the internal {@link Handler} and
     * starts it's background {@link HandlerThread}
     * <br>
     * <b>Can be called only once!</b> Must not be called again unless {@link #shutdown()} is called before.
     */
    public static synchronized void initialize() {
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
     * Returns the Iperf3Monitor instance, after calling {@link #initialize()}
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
                Log.e(TAG, "Exception " + e + " happened during shutdown() while cleaning up handler !!!", e);
            }
        }

        if (instance.handlerThread != null) {
            instance.handlerThread.quitSafely();
            try {
                instance.handlerThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Exception " + e + " happened during shutdown() while joining the handlerThread !!! ", e);
            }
        }
    }

    public void startListeningForIperf3Updates(Context applicationContext, Iperf3MonitorCompletionListener completionListener) {
        // Start listening for iPerf3 results...
        Iperf3ResultsDataBase database = Iperf3ResultsDataBase.getDatabase(applicationContext);
        database.iperf3RunResultDao().getLatestResult().observeForever(latestIperf3RunResult -> {
            shouldStop.set(false);
            // Actually, since these come from the DB, the latest Iperf3 result will be the *last executed Iperf3 test*
            // So if no Iperf3 tests were ran ever, the first result will naturally be 'null' because there's nothing
            // in the DB.
            Log.d(TAG, "Latest iPerf3 emission: " + latestIperf3RunResult + "\tshouldStop: " + shouldStop.get());
            // The first emmision is always a null so...
            if (latestIperf3RunResult != null) {
                Log.v(TAG, "Latest iPerf3 result is: " + latestIperf3RunResult.result);
                // 0 is the magic number for success
                if (latestIperf3RunResult.result == 0) {
                    Log.v(TAG, "Result was fine, commencing further...");
                    Log.v(TAG, "Latest result's rawIperf3file is: " + latestIperf3RunResult.input.iperf3rawIperf3file);

                    // Copy the file, for just in case
                    // This is useful for when we run the test and open it up in the default Iperf3 log viewer
                    // since the Iperf3Parser might be destructively parsing the file - from what I noticed only
                    // one of them reaches the END
                    String originalFilePath = latestIperf3RunResult.input.iperf3rawIperf3file;
                    String targetFilePath = originalFilePath + ".tmp";

                    try {
                        copyFile(originalFilePath, targetFilePath);
                    } catch (IOException e) {
                        Log.e(TAG, "Exception " + e + " happened during iperf3 raw file copy!", e);
                        throw new RuntimeException(e);
                    }

                    Log.v(TAG, "File copying should be done, instantiating new parser with rawIperf3File: " + targetFilePath);

                    Iperf3Parser iperf3Parser = Iperf3Parser.instantiate(targetFilePath);
                    defaultThroughput = new Metric(METRIC_TYPE.THROUGHPUT, applicationContext);
                    defaultReverseThroughput = new Metric(METRIC_TYPE.THROUGHPUT, applicationContext);
                    defaultJITTER = new Metric(METRIC_TYPE.JITTER, applicationContext);
                    PACKET_LOSS = new Metric(METRIC_TYPE.PACKET_LOSS, applicationContext);
                    // Obviously, these things need to be added or nothing will work... ffs...
                    defaultThroughput.createMainLL("Throughput");
                    defaultReverseThroughput.createMainLL("Throughput");
                    defaultJITTER.createMainLL("Jitter ms");
                    PACKET_LOSS.createMainLL("Packet Loss %");
                    // With this retarded problem out of the way... we can move on.

                    Log.v(TAG, "Adding property change listener");
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
                            Log.d(TAG, "--> onPropertyChange()\tchanged event: " + evt);

                            switch (evt.getPropertyName()) {
                                case "interval": {
                                    Interval interval = (Interval) evt.getNewValue();
                                    parseSum(interval.getSum(), defaultThroughput);
                                    if (interval.getSumBidirReverse() != null) {
                                        parseSum(interval.getSumBidirReverse(), defaultReverseThroughput);
                                    }

                                    Log.v(TAG, "INTERVAL\tdefaultThroughput size: " + defaultThroughput.getMeanList().size() + ", defaultReverseThroughput size: " + defaultReverseThroughput.getMeanList().size());
                                }
                                break;

                                case "start": {
                                    Log.v(TAG, "START\tdefaultThroughput: " + defaultThroughput + ", defaultReverseThroughput: " + defaultReverseThroughput);
                                }
                                break;

                                case "end": {
                                    Log.v(TAG, "END\tdefaultThroughput size: " + defaultThroughput.getMeanList().size() + ", defaultReverseThroughput size: " + defaultReverseThroughput.getMeanList().size());
                                    Log.v(TAG, "END\tend value is: " + evt.getNewValue() + "\t\tequals END_MARKER ? " + evt.getNewValue().equals(Iperf3Parser.END_MARKER));

                                    // Throughput values need to be normalized by dividing by 1e6 so => realValue = value/1e+6
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

                                    Log.d(TAG, "download speeds: MIN=" + DLmin + ", MED=" + DLmedian + ", MAX=" + DLmax + ", MEAN=" + DLmean);
                                    Log.d(TAG, "upload speeds: MIN=" + ULmin + ", MED=" + ULmedian + ", MAX=" + ULmax + ", MEAN=" + ULmean);

                                    // Stop the thread to avoid spinning it needlessly forever...
                                    stopParsingThread();
                                    shouldStop.compareAndSet(false, true);

                                    deleteFile(targetFilePath);
                                    Log.v(TAG, "END\tcleaned up everything! shouldStop: " + shouldStop.get());

                                    // Instantiate the POJO stuff holder
                                    MetricsPOJO values = new MetricsPOJO(
                                            new MetricsPOJO.DownloadMetrics(DLmin, DLmedian, DLmean, DLmax, DLlast),
                                            new MetricsPOJO.UploadMetrics(ULmin, ULmedian, ULmean, ULmax, ULlast)
                                    );

                                    // Notify the completion listener
                                    if (completionListener != null) {
                                        completionListener.onIperf3TestCompleted(values);
                                    }
                                }
                                break;

                                case "error": {
                                    Error error = (Error) evt.getNewValue();
                                    TextView errorView = new TextView(applicationContext);
                                    errorView.setText(error.getError());
                                    errorView.setTextColor(applicationContext.getColor(R.color.crimson));
                                    errorView.setPadding(10, 10, 10, 10);
                                    errorView.setTextSize(20);
//                                metricLL.addView(errorView);
                                }
                                break;
                            }
                        }
                    });
                    Log.v(TAG, "Adding completion listener");
                    iperf3Parser.setCompletionListener(new Iperf3Parser.Iperf3ParserCompletionListener() {
                        @Override
                        public void onParseCompleted() {
                            Log.v(TAG, "---> onParseCompleted");

                            double DLmin = normalize(defaultReverseThroughput.calcMin());
                            double DLmedian = normalize(defaultReverseThroughput.calcMedian());
                            double DLmax = normalize(defaultReverseThroughput.calcMax());
                            double DLmean = normalize(defaultReverseThroughput.calcMean());

                            double ULmin = normalize(defaultThroughput.calcMin());
                            double ULmedian = normalize(defaultThroughput.calcMedian());
                            double ULmax = normalize(defaultThroughput.calcMax());
                            double ULmean = normalize(defaultThroughput.calcMean());

                            Log.d(TAG, "download speeds: MIN=" + DLmin + ", MED=" + DLmedian + ", MAX=" + DLmax + ", MEAN=" + DLmean);
                            Log.d(TAG, "upload speeds: MIN=" + ULmin + ", MED=" + ULmedian + ", MAX=" + ULmax + ", MEAN=" + ULmean);
                            stopParsingThread();
                        }
                    });


                    Log.d(TAG, "Starting parsing...");
                    startParsingThread(iperf3Parser);
                } else {
                    // This could be either a 1, or a -100; first being a failure, the second one being a 'in progress' value.
                    Log.d(TAG, "latestIperf3RunResult.result was: " + latestIperf3RunResult.result + "\t\tignoring...");
                }
            }
        });
    }

    private void startParsingThread(Iperf3Parser iperf3Parser) {
        Log.d(TAG, "--> startParsingThread()");
        handler.post(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "startParsingThread()::parsingCycle!\tshouldStop: " + shouldStop.get());

                // And finally, parse a bit of the file.
                iperf3Parser.parse();
                if (shouldStop.get()) {
                    // well, do nothing but return
                    return;
                } else {
                    handler.postDelayed(this, PARSING_DELAY_IN_MS);
                }
            }
        });
        Log.d(TAG, "<-- startParsingThread()");
    }

    private void stopParsingThread() {
        handler.removeCallbacksAndMessages(null);
    }

    /**
     * Callback for when Iperf3Monitor does a whole Iperf3 test cycle, reporting with the gathered metrics
     */
    public interface Iperf3MonitorCompletionListener {
        /**
         * Called when the Iperf3 test was successfully completed
         * @param metrics the gathered metrics during the test
         */
        void onIperf3TestCompleted(MetricsPOJO metrics);
    }

    /**
     * Copies a file designated by <i>sourceFileName</i> file path to the <i>destFileName</i> path
     * <br>
     * Throws IOException if something goes wrong.
     *
     * @param sourceFileName path to the source file
     * @param destFileName path to the destination file, where the file should be copied to (including the new name)
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
     * @param filePath the file to delete
     * @return whether deletion was successful or not
     */
    public static boolean deleteFile(String filePath) {
        File file = new File(filePath);
        boolean retVal;
        if (file.exists()) {
            if (file.delete()) {
                Log.d(TAG, "File deleted successfully! filepath: " + filePath);
                retVal = true;
            } else {
                Log.d(TAG, "Failed to delete the file at filepath: " + filePath);
                retVal = false;
            }
        } else {
            Log.w(TAG, "File at path " + filePath + " does not exist!");
            retVal = false;
        }

        return retVal;
    }

    /**
     * Gets last element of a list
     * @param list the list of doubles from which to get the last element
     * @return the last element, or 0 if the list is empty
     */
    private double getLast(@NonNull List<Double> list) {
        if (list.size() == 0) {
            return 0;
        } else {
            return list.get(list.size() - 1);
        }
    }

    /**
     * Normalizes the value by dividing by 1e+6
     * @param value the value to normalize
     * @return the normalized value, divided by 1e+6 (1000000)
     */
    private double normalize(double value) {
        return value / 1e+6;
    }
}
