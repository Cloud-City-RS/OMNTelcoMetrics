package cloudcity.dataholders;

public class MetricsPOJO {

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

    public MetricsPOJO(double DLmin, double DLmedian, double DLmean, double DLmax, double DLlast,
                       double ULmin, double ULmedian, double ULmean, double ULmax, double ULlast) {
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
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        return sb
                .append("MetricsPOJO{ ")
                .append("DOWNLOAD:[")
                .append("min=" + DLmin)
                .append(", median=" + DLmedian)
                .append(", mean=" + DLmean)
                .append(", max=" + DLmax)
                .append(", last=" + DLlast)
                .append("], UPLOAD:[")
                .append("min=" + ULmin)
                .append(", median=" + ULmedian)
                .append(", mean=" + ULmean)
                .append(", max=" + ULmax)
                .append(", last=" + ULlast)
                .append("] }")
                .toString();

//        return "MetricsPOJO{" +
//                "DLmin=" + DLmin +
//                ", DLmedian=" + DLmedian +
//                ", DLmean=" + DLmean +
//                ", DLmax=" + DLmax +
//                ", DLlast=" + DLlast +
//                ", ULmin=" + ULmin +
//                ", ULmedian=" + ULmedian +
//                ", ULmean=" + ULmean +
//                ", ULmax=" + ULmax +
//                ", ULlast=" + ULlast +
//                '}';
    }
}
