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
        return (x * (x + 1)) / 2;
    }

    public static double sumArr(double[] arr, int start, int end) {
        double sum = 0;
        for (int i = start; i < end; i++) sum += arr[i];
        return sum;
    }

    public static int sumArr(int[] arr, int start, int end) {
        int sum = 0;
        for (int i = start; i < end; i++) sum += arr[i];
        return sum;
    }

    public static double[] arrMultiOut(double[] src, double v) {
        double[] arr = new double[src.length];
        for (int i = 0; i < src.length; i++) arr[i] = src[i] * v;
        return arr;
    }

    public static double[] arrMultiOut(double[] a, double[] b) {
        double[] arr = new double[a.length];
        for (int i = 0; i < a.length; i++) arr[i] = a[i] * b[i];
        return arr;
    }

    public static double[] arrSubOut(double[] a, double[] b) {
        double[] arr = new double[a.length];
        for (int i = 0; i < a.length; i++) arr[i] = a[i] - b[i];
        return arr;
    }

    public static double[] compressBins(double[] src) {
        double[] out = new double[(src.length + 1) / 2];
        int reminder = src.length % 2;
        for (int i = 0; i < src.length - reminder; i += 2) out[i / 2] = src[i] + src[i + 1];

        if (reminder == 1) out[out.length - 1] = src[src.length - 1];

        return out;
    }
}
