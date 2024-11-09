package cloudcity;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
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

import cloudcity.dataholders.Iperf3RunnerData;
import cloudcity.dataholders.MetricsPOJO;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.Iperf3Fragment;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.Iperf3Parser;
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
 * @see #startDefault15secTest()
 */
public class Iperf3Monitor {
    private static final String TAG = Iperf3Monitor.class.getSimpleName();

    private final static long PARSING_DELAY_IN_MS = 10L; //0.01sec, was 1000L

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
     * Runnable used for parsing Iperf3 tests by constantly calling {@link #iperf3Parser}'s
     * {@link Iperf3Parser#parse()} method every {@link #PARSING_DELAY_IN_MS} until it reaches the end
     * or ordered to stop by toggling {@link #shouldStop} flag
     */
    private final Runnable parsingRunnable = new Runnable() {
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

    public void startListeningForIperf3Updates(Iperf3MonitorCompletionListener completionListener) {
        // Start listening for iPerf3 results...
        iperf3ResultsDatabase.iperf3RunResultDao().getLatestResult().observeForever(latestIperf3RunResult -> {
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
                    if (latestIperf3RunResult.input != null) {
                        Log.v(TAG, "Latest result's rawIperf3file is: " + latestIperf3RunResult.input.iperf3rawIperf3file);
                    } else {
                        Log.v(TAG, "Latest result's input was: NULL");
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
                        Log.e(TAG, "Exception " + e + " happened during iperf3 raw file copy!", e);
                        throw new RuntimeException(e);
                    }

                    Log.v(TAG, "File copying should be done, instantiating new parser with rawIperf3File: " + targetFilePath);

                    Iperf3Parser iperf3Parser = Iperf3Parser.instantiate(targetFilePath);
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

                                    // Instantiate the POJO stuff holder
                                    MetricsPOJO values = new MetricsPOJO(
                                            new MetricsPOJO.DownloadMetrics(DLmin, DLmedian, DLmean, DLmax, DLlast),
                                            new MetricsPOJO.UploadMetrics(ULmin, ULmedian, ULmean, ULmax, ULlast)
                                    );

                                    // Notify the completion listener
                                    if (completionListener != null) {
                                        completionListener.onIperf3TestCompleted(values);
                                    }

                                    // And set the new marker as 'no longer running'
                                    iperf3TestRunning.compareAndSet(true, false);

                                    Log.v(TAG, "END\tcleaned up everything! shouldStop: " + shouldStop.get() + ", iperf3TestRunning: " + iperf3TestRunning.get());
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

                            // And finally delete the temp copy of the file
                            deleteFile(targetFilePath);
                        }
                    });


                    Log.d(TAG, "Starting parsing...");
                    startParsingThread(iperf3Parser);
                } else {
                    // This could be either a 1, or a -100; first being a failure, the second one being a 'in progress' value.
                    Log.d(TAG, "latestIperf3RunResult.result was: " + latestIperf3RunResult.result + "\t\tignoring...");
                    // We want to reset the test running marker on any non -100 result as well,
                    // since -100 means it's still running, and apparently anything non-zero means completion
                    // 1 for failure, -1 for cancellation and who knows what else
                    if (latestIperf3RunResult.result != -100) {
                        iperf3TestRunning.compareAndSet(true, false);
                    }
                }
            }
        });
    }

    private void startParsingThread(Iperf3Parser newIperf3Parser) {
        Log.d(TAG, "--> startParsingThread()");
        iperf3Parser = newIperf3Parser;
        handler.post(parsingRunnable);
        Log.d(TAG, "<-- startParsingThread()");
    }

    private void stopParsingThread() {
        handler.removeCallbacks(parsingRunnable);
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
        void onIperf3TestCompleted(MetricsPOJO metrics);
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
     *
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
     *
     * @param value the value to normalize
     * @return the normalized value, divided by 1e+6 (1000000)
     */
    private double normalize(double value) {
        return value / 1e+6;
    }

    public void startDefault15secTest() {
        // Sanity check
        if (iperf3TestRunning.get()) {
            Log.e(TAG, "Iperf3 test is still running! aborting...");
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
        String duration = "15";
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

        Log.d(TAG, "Joined command " + joined);

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

        startIperf3Test(appContext, testData);
    }

    private void startIperf3Test(Context applicationContext, Iperf3RunnerData data) {
        // Init the database
        Iperf3RunResultDao iperf3RunResultDao = iperf3ResultsDatabase.iperf3RunResultDao();
        // Do everything else needed for the test
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath() + "/omnt/iperf3LP/";

        try {
            Files.createDirectories(Paths.get(path));
        } catch (IOException e) {
            Log.e(TAG, "Could not create Dir files!");
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
                        .build();
        OneTimeWorkRequest iperf3UP =
                new OneTimeWorkRequest
                        .Builder(Iperf3UploadWorker.class)
                        .setInputData(iperf3Data.build())
                        .addTag("iperf3")
                        .build();

        iperf3RunResultDao.insert(
                new Iperf3RunResult(iperf3WorkerID, -100, false, data.getCityInput(), data.getTimestamp()));

        boolean iperf3Json = Boolean.TRUE.equals(data.getBooleanDataMap().get("iperf3Json"));

        // Sanity check...
        if (iperf3TestRunning.get()) {
            // IF test is running, log error, and return
            Log.e(TAG, "Iperf3 test was already running! Returning...");
            return;
        }

        SharedPreferencesGrouper spg = SharedPreferencesGrouper.getInstance(applicationContext);
        if (iperf3TestRunning.compareAndSet(false, true)) {

            // Check if we have a main thread executor
            if (mainThreadExecutor == null) {
                synchronized (this) {
                    if (mainThreadExecutor == null) {
                        mainThreadExecutor = new MainThreadExecutor();
                    }
                }
            }

            mainThreadExecutor.execute(() -> {
                if (spg.getSharedPreference(SPType.logging_sp).getBoolean("enable_influx", false) && iperf3Json) {
                    iperf3WM.beginWith(iperf3WR).then(iperf3LP).then(iperf3UP).enqueue();
                } else if (iperf3Json) {
                    iperf3WM.beginWith(iperf3WR).then(iperf3LP).enqueue();
                } else {
                    iperf3WM.beginWith(iperf3WR).enqueue();
                }

                iperf3WM.getWorkInfoByIdLiveData(iperf3WR.getId()).observeForever(workInfo -> {
                    int iperf3_result;
                    iperf3_result = workInfo.getOutputData().getInt("iperf3_result", -100);
                    if (workInfo.getState().equals(WorkInfo.State.CANCELLED)) {
                        iperf3_result = -1;
                    }
                    iperf3RunResultDao.updateResult(iperf3WorkerID, iperf3_result);
                    Log.d(TAG, "onChanged: iperf3_result: " + iperf3_result);
                });
                iperf3WM.getWorkInfoByIdLiveData(iperf3UP.getId()).observeForever(workInfo -> {
                    boolean iperf3_upload;
                    iperf3_upload = workInfo.getOutputData().getBoolean("iperf3_upload", false);
                    Log.d(TAG, "onChanged: iperf3_upload: " + iperf3_upload);
                    iperf3RunResultDao.updateUpload(iperf3WorkerID, iperf3_upload);
                });
            });
        }
    }

    private WorkManager getWorkManager() {
        return WorkManager.getInstance(appContext);
    }
}
