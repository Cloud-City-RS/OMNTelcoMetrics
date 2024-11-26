package cloudcity.dataholders;

import androidx.annotation.NonNull;

/**
 * Plain Old Java Object (POJO) for holding iPerf3 network performance metrics.
 * Contains statistical data for both download (DL) and upload (UL) measurements.
 *
 * Used for sending data to CC server.
 */
public class Iperf3MetricsPOJO {

    private final double DLmin;
    private final double DLmedian;
    private final double DLmean;
    private final double DLmax;
    private final double DLlast;

    private final double ULmin;
    private final double ULmedian;
    private final double ULmean;
    private final double ULmax;
    private final double ULlast;

    private final long startTimestamp;
    private final long endTimestamp;

    /**
     * The all arg constructor. Please prefer using the other {@link #Iperf3MetricsPOJO(DownloadMetrics, UploadMetrics, long, long)}
     * over this one, as this one might be error-prone
     *
     * @param DLmin download min
     * @param DLmedian download median (average) of the whole sampling list
     * @param DLmean download mean (middle element of sampling list)
     * @param DLmax download max
     * @param DLlast last download sample
     * @param ULmin upload min
     * @param ULmedian upload median (average) of the whole sampling list
     * @param ULmean upload mean (middle element of the sampling list)
     * @param ULmax upload max
     * @param ULlast last upload sample
     * @param startTimestamp iperf3 test start timestamp
     * @param endTimestamp iperf3 test end timestamp
     */
    public Iperf3MetricsPOJO(double DLmin, double DLmedian, double DLmean, double DLmax, double DLlast,
                             double ULmin, double ULmedian, double ULmean, double ULmax, double ULlast,
                             long startTimestamp, long endTimestamp) {
        this.DLmin = DLmin;
        this.DLmedian = DLmedian;
        this.DLmean = DLmean;
        this.DLmax = DLmax;
        this.DLlast = DLlast;
        this.ULmin = ULmin;
        this.ULmedian = ULmedian;
        this.ULmean = ULmean;
        this.ULmax = ULmax;
        this.ULlast = ULlast;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
    }

    /**
     * The preferred and safer constructor to use
     * @param download the download metrics
     * @param upload the upload metrics
     */
    public Iperf3MetricsPOJO(DownloadMetrics download, UploadMetrics upload, long startTimestamp, long endTimestamp) {
        this(
                download.getDLmin(),
                download.getDLmedian(),
                download.getDLmean(),
                download.getDLmax(),
                download.getDLlast(),
                upload.getULmin(),
                upload.getMedian(),
                upload.getMean(),
                upload.getMax(),
                upload.getLast(),
                startTimestamp,
                endTimestamp
        );
    }

    public MetricsPair toMetricsPair() {
        return new MetricsPair(
                new UploadMetrics(ULmin, ULmedian, ULmean, ULmax, ULlast),
                new DownloadMetrics(DLmin, DLmedian, DLmean, DLmax, DLlast)
        );
    }

    public TestDurationPair toTestDurationPair() {
        return new TestDurationPair(startTimestamp, endTimestamp);
    }

    public static class MetricsPair {
        @NonNull
        final UploadMetrics uploadMetrics;
        @NonNull
        final DownloadMetrics downloadMetrics;

        MetricsPair(@NonNull UploadMetrics upload, @NonNull DownloadMetrics download) {
            this.uploadMetrics = upload;
            this.downloadMetrics = download;
        }

        public @NonNull UploadMetrics getUploadMetrics() { return uploadMetrics; }
        public @NonNull DownloadMetrics getDownloadMetrics() { return downloadMetrics; }
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
                .append("Iperf3MetricsPOJO{ ")
                .append("DOWNLOAD:[")
                .append("min=").append(DLmin)
                .append(", median=").append(DLmedian)
                .append(", mean=").append(DLmean)
                .append(", max=").append(DLmax)
                .append(", last=").append(DLlast)
                .append("], UPLOAD:[")
                .append("min=").append(ULmin)
                .append(", median=").append(ULmedian)
                .append(", mean=").append(ULmean)
                .append(", max=").append(ULmax)
                .append(", last=").append(ULlast)
                .append("] }")
                .toString();
    }

    public static class DownloadMetrics extends BaseMetrics {
        public DownloadMetrics(double DLmin, double DLmedian, double DLmean, double DLmax, double DLlast) {
            super(DLmin, DLmedian, DLmean, DLmax, DLlast);
        }

        public double getDLmin() {
            return getMin();
        }

        public double getDLmedian() {
            return getMedian();
        }

        public double getDLmean() {
            return getMean();
        }

        public double getDLmax() {
            return getMax();
        }

        public double getDLlast() {
            return getLast();
        }
    }

    public static class UploadMetrics extends BaseMetrics {

        public UploadMetrics(double ULmin, double ULmedian, double ULmean, double ULmax, double ULlast) {
            super(ULmin, ULmedian, ULmean, ULmax, ULlast);
        }

        public double getULmin() {
            return getMin();
        }

        public double getULmedian() {
            return getMedian();
        }

        public double getULmean() {
            return getMean();
        }

        public double getULmax() {
            return getMax();
        }

        public double getULlast() {
            return getLast();
        }
    }
}
