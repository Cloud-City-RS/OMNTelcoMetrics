package cloudcity.dataholders;

import androidx.annotation.NonNull;

/**
 * Plain Old Java Object (POJO) for holding Ping network performance metrics.
 * Contains statistical data for both round-trip-time (RTT) and packet loss (PL) measurements.
 *
 * Used for sending data to CC server.
 */
public class PingMetricsPOJO {

    private final double RTTmin;
    private final double RTTmedian;
    private final double RTTmean;
    private final double RTTmax;
    private final double RTTlast;

    private final double PLmin;
    private final double PLmedian;
    private final double PLmean;
    private final double PLmax;
    private final double PLlast;

    private final long startTimestamp;
    private final long endTimestamp;

    private final boolean wasSuccess;

    /**
     * The all arg constructor. Please prefer using the other {@link #PingMetricsPOJO(RTTMetrics, PackageLossMetrics, long, long, boolean)}
     * over this one, as this one might be error-prone
     *
     * @param RTTmin rtt min
     * @param RTTmedian rtt median (average) of the whole sampling list
     * @param RTTmean rtt mean (middle element of sampling list)
     * @param RTTmax rtt max
     * @param RTTlast last rtt sample
     * @param PLmin package loss min
     * @param PLmedian package loss median (average) of the whole sampling list
     * @param PLmean package loss mean (middle element of the sampling list)
     * @param PLmax package loss max
     * @param PLlast last package loss sample
     * @param startTimestamp ping test start timestamp
     * @param endTimestamp ping test end timestamp
     * @param success whether the ping test completed successfully or not
     */
    public PingMetricsPOJO(double RTTmin, double RTTmedian, double RTTmean, double RTTmax, double RTTlast,
                           double PLmin, double PLmedian, double PLmean, double PLmax, double PLlast,
                           long startTimestamp, long endTimestamp, boolean success) {
        this.RTTmin = RTTmin;
        this.RTTmedian = RTTmedian;
        this.RTTmean = RTTmean;
        this.RTTmax = RTTmax;
        this.RTTlast = RTTlast;
        this.PLmin = PLmin;
        this.PLmedian = PLmedian;
        this.PLmean = PLmean;
        this.PLmax = PLmax;
        this.PLlast = PLlast;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.wasSuccess = success;
    }

    /**
     * The preferred and safer constructor to use
     * @param rtt the RTT metrics
     * @param packageLoss the package loss metrics
     * @param startTimestamp test start timestamp
     * @param endTimestamp test end timestamp
     * @param success was test a success
     */
    public PingMetricsPOJO(RTTMetrics rtt, PackageLossMetrics packageLoss, long startTimestamp, long endTimestamp, boolean success) {
        this(
                rtt.getMin(),
                rtt.getMedian(),
                rtt.getMean(),
                rtt.getMax(),
                rtt.getLast(),
                packageLoss.getMin(),
                packageLoss.getMedian(),
                packageLoss.getMean(),
                packageLoss.getMax(),
                packageLoss.getLast(),
                startTimestamp,
                endTimestamp,
                success
        );
    }

    public MetricsPair toMetricsPair() {
        return new MetricsPair(
                new RTTMetrics(RTTmin, RTTmedian, RTTmean, RTTmax, RTTlast),
                new PackageLossMetrics(PLmin, PLmedian, PLmean, PLmax, PLlast)
        );
    }

    public TestDurationPair toTestDurationPair() {
        return new TestDurationPair(startTimestamp, endTimestamp);
    }

    /**
     * Returns whether the test was successful; this is determined by the terminal state of {@link de.fraunhofer.fokus.OpenMobileNetworkToolkit.Ping.PingWorker}
     * @return the value of {@link #wasSuccess} - whether test has finished successfully or not
     */
    public boolean wasSuccess() { return wasSuccess; }

    public boolean isDestinationReachable() { return RTTmedian > 0 && PLmedian == 0; }

    public static class MetricsPair {
        @NonNull
        final RTTMetrics rttMetrics;
        @NonNull
        final PackageLossMetrics packageLossMetrics;

        MetricsPair(@NonNull RTTMetrics rtt, @NonNull PackageLossMetrics packageLoss) {
            this.rttMetrics = rtt;
            this.packageLossMetrics = packageLoss;
        }

        public @NonNull RTTMetrics getRTTMetrics() { return rttMetrics; }
        public @NonNull PackageLossMetrics getPackageLossMetrics() { return packageLossMetrics; }
    }

    public static class TestDurationPair {
        final long startTimestamp;
        final long endTimestamp;

        TestDurationPair(long startTs, long endTs) {
            this.startTimestamp = startTs;
            this.endTimestamp = endTs;
        }

        public long getTestStartTimestamp() { return startTimestamp; }
        public long getTestEndTimestamp() { return endTimestamp; }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        return sb
                .append("PingMetricsPOJO{ ")
                .append("RTT:[")
                .append("min=").append(RTTmin)
                .append(", median=").append(RTTmedian)
                .append(", mean=").append(RTTmean)
                .append(", max=").append(RTTmax)
                .append(", last=").append(RTTlast)
                .append("], PackageLoss:[")
                .append("min=").append(PLmin)
                .append(", median=").append(PLmedian)
                .append(", mean=").append(PLmean)
                .append(", max=").append(PLmax)
                .append(", last=").append(PLlast)
                .append("] }")
                .toString();
    }

    public static class RTTMetrics extends BaseMetrics{

        public RTTMetrics(double min, double median, double mean, double max, double last) {
            super(min, median, mean, max, last);
        }

        public double getRTTmin() {
            return getMin();
        }

        public double getRTTmedian() {
            return getMedian();
        }

        public double getRTTmean() {
            return getMean();
        }

        public double getRTTmax() {
            return getMax();
        }

        public double getRTTlast() {
            return getLast();
        }
    }

    public static class PackageLossMetrics extends BaseMetrics {
        public PackageLossMetrics(double min, double median, double mean, double max, double last) {
            super(min,median,mean,max,last);
        }

        public double getPLmin() {
            return getMin();
        }

        public double getPLmedian() {
            return getMedian();
        }

        public double getPLmean() {
            return getMean();
        }

        public double getPLmax() {
            return getMax();
        }

        public double getPLlast() {
            return getLast();
        }
    }
}
