package cloudcity;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

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

public class CloudCityUtil {

    /**
     * This method is necessary, since {@link String#isBlank()} is introduced in API34
     * @param param non-null string to check
     * @return whether passed-in string is blank (all whitespace or empty) or not. Nulls are considered blank.
     */
    public static boolean isBlank(@NonNull String param) {
        if (param == null) return true;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return param.isBlank();
        } else {
            return isBlank_pre34(param);
        }
    }

    private static boolean isBlank_pre34(@NonNull String param) {
        return param.trim().isEmpty();
    }


    public static void startListeningForIperf3Updates(String TAG, Context applicationContext) {
        // Start listening for iPerf3 results...
        Iperf3ResultsDataBase database = Iperf3ResultsDataBase.getDatabase(applicationContext);
        database.iperf3RunResultDao().getLatestResult().observeForever(latestIperf3RunResult -> {
            // Actually, since these come from the DB, the latest Iperf3 result will be the *last executed Iperf3 test*
            // So if no Iperf3 tests were ran ever, the first result will naturally be 'null' because there's nothing
            // in the DB.
            android.util.Log.wtf(TAG, "Latest iPerf3 emission: " + latestIperf3RunResult);
            // The first emmision is always a null so...
            if (latestIperf3RunResult != null) {
                android.util.Log.wtf(TAG, "Latest iPerf3 result is: " + latestIperf3RunResult.result);
                // 0 is the magic number for success
                if (latestIperf3RunResult.result == 0) {
                    android.util.Log.wtf(TAG, "Result was fine, commencing further...");
                    android.util.Log.wtf(TAG, "Latest result's rawIperf3file is: "+latestIperf3RunResult.input.iperf3rawIperf3file);
                    Iperf3Parser iperf3Parser = Iperf3Parser.instantiate(latestIperf3RunResult.input.iperf3rawIperf3file);
                    Metric defaultThroughput = new Metric(METRIC_TYPE.THROUGHPUT, applicationContext);
                    Metric defaultReverseThroughput = new Metric(METRIC_TYPE.THROUGHPUT, applicationContext);
                    Metric defaultJITTER = new Metric(METRIC_TYPE.JITTER, applicationContext);
                    Metric PACKET_LOSS = new Metric(METRIC_TYPE.PACKET_LOSS, applicationContext);

                    android.util.Log.wtf(TAG, "Adding property change listener");
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
                            Log.wtf(TAG, "--> onPropertyChange()\tchanged event: " + evt);

                            switch (evt.getPropertyName()) {
                                case "interval":
                                    Interval interval = (Interval) evt.getNewValue();
                                    parseSum(interval.getSum(), defaultThroughput);
                                    if (interval.getSumBidirReverse() != null)
                                        parseSum(interval.getSumBidirReverse(),
                                                defaultReverseThroughput);

                                    Log.wtf(TAG, "INTERVAL\ttest updated?");
                                    Log.wtf(TAG, "INTERVAL\tdefaultThroughput: " + defaultThroughput + ", defaultReverseThroughput: " + defaultReverseThroughput);
                                    break;
                                case "start":
                                    Log.wtf(TAG, "START\ttest started?");
                                    Log.wtf(TAG, "START\tdefaultThroughput: " + defaultThroughput + ", defaultReverseThroughput: " + defaultReverseThroughput);
                                    break;
                                case "end":
                                    Log.wtf(TAG, "END\ttest ended?");
                                    Log.wtf(TAG, "END\tdefaultThroughput: " + defaultThroughput + ", defaultReverseThroughput: " + defaultReverseThroughput);
                                    //TODO when this happens, the thread needs to be killed!

                                    double DLmin = defaultThroughput.calcMin();
                                    double DLmedian = defaultThroughput.calcMedian();
                                    double DLmax = defaultThroughput.calcMax();
                                    double DLmean = defaultThroughput.calcMean();

                                    Log.wtf(TAG, "download speeds: MIN=" + DLmin + ", MED=" + DLmedian + ", MAX=" + DLmax + ", MEAN=" + DLmean);
                                    break;
                                case "error":
                                    de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.Error error = (Error) evt.getNewValue();
                                    TextView errorView = new TextView(applicationContext);
                                    errorView.setText(error.getError());
                                    errorView.setTextColor(applicationContext.getColor(R.color.crimson));
                                    errorView.setPadding(10, 10, 10, 10);
                                    errorView.setTextSize(20);
//                                metricLL.addView(errorView);
                                    break;
                            }
                        }
                    });
                    android.util.Log.wtf(TAG, "Adding completion listener");
                    iperf3Parser.setCompletionListener(() -> {
                        Log.wtf(TAG, "parsing completed");

                        double DLmin = defaultThroughput.calcMin();
                        double DLmedian = defaultThroughput.calcMedian();
                        double DLmax = defaultThroughput.calcMax();
                        double DLmean = defaultThroughput.calcMean();

                        Log.wtf(TAG, "download speeds: MIN=" + DLmin + ", MED=" + DLmedian + ", MAX=" + DLmax + ", MEAN=" + DLmean);
                    });

                    //TODO this thing needs to be in a thread...
                    android.util.Log.wtf(TAG, "Starting parsing...");
                    iperf3Parser.parse();
                    android.util.Log.wtf(TAG, "Finished parsing!");
                    // TEST THIS OUT
                }
            }
        });
    }
}
