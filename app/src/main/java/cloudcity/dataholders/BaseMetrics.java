package cloudcity.dataholders;

class BaseMetrics {
        private final double min;
        private final double median;
        private final double mean;
        private final double max;
        private final double last;

        public BaseMetrics(double min, double median, double mean, double max, double last) {
            this.min = min;
            this.median = median;
            this.mean = mean;
            this.max = max;
            this.last = last;
        }

        double getMin() {
            return min;
        }

        double getMedian() {
            return median;
        }

        double getMean() {
            return mean;
        }

        double getMax() {
            return max;
        }

        double getLast() {
            return last;
        }
    }
