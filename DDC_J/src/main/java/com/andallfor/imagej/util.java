package com.andallfor.imagej;

public class util {
    public static double dist(double[] a, double[] b) {
        double interior = 0;
        for (int i = 0; i < a.length; i++) interior += (a[i] - b[i]) * (a[i] - b[i]);
        return Math.sqrt(interior);
    }

    public static int[] makeBins(double size, double step) {
        int n = (int) (size / step) + 2;  // +1 to include end, +1 to end with inf
        int[] bins = new int[n];
        for (int i = 0; i < n - 1; i++) bins[i] = (int) (i * step);
        bins[n - 1] = Integer.MAX_VALUE;

        return bins;
    }

    public static int sumFactorial(int x) {
        if (x % 2 == 0) return (x + 1) * (x / 2);
        return (x + 1) * ((x - 1) / 2) + ((x + 1) / 2);
    }

    public static double sumArr(double[] arr, int start, int end) {
        double sum = 0;
        for (int i = start; i < end; i++) sum += arr[i];
        return sum;
    }
}
