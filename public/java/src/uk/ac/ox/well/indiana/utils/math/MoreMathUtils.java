package uk.ac.ox.well.indiana.utils.math;

public class MoreMathUtils {
    private MoreMathUtils() {}

    public static double max(double... values) {
        double max = Double.NEGATIVE_INFINITY;

        for (double tmp : values) {
            max = max < tmp ? tmp : max;
        }

        return max;
    }

    public static int whichMax(double... values) {
        int maxIndex = -1;
        double maxValue = Double.MIN_VALUE;

        for (int i = 0; i < values.length; i++) {
            if (values[i] > maxValue) {
                maxIndex = i;
                maxValue = values[i];
            }
        }

        return maxIndex;
    }

    public static int roundUp(int n, int nearest) {
        return (n + nearest - 1) / nearest * nearest;
    }

    public static int roundDown(int n, int nearest) {
        return (int) Math.floor(n/nearest)*nearest;
    }

    public static boolean equals(double x, double y, double tol) { return Math.abs(x - y) < tol; }
}
