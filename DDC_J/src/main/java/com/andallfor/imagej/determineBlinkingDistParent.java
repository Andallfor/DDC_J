package com.andallfor.imagej;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class determineBlinkingDistParent implements Runnable {
    private double[] frame;
    private double[][] loc;
    private double max;
    private int N, res;

    public double[] binsBlink, binsNoBlink;
    public double[][] binsFittingBlink;

    private int pointsPerThread = 10_000_000;

    public determineBlinkingDistParent(double[] frame, double[][] loc, double max, int res, int N) {
        this.frame = frame;
        this.loc = loc;
        this.max = max;
        this.res = res;
        this.N = N;
    }

    public void run() {
        ExecutorService es = Executors.newCachedThreadPool();

        int numThreads = (int) Math.ceil(util.sumFactorial(frame.length - 1) / (double) pointsPerThread);
        determineBlinkingDistChild[] threads = new determineBlinkingDistChild[numThreads];

        /*
         * area of below triangle is # results from pdist (P_n = length of input arr - 1)
         * goal is to divide area into numThreads parts of equal area
         *     |+
         *     |  +
         *     |   |+
         * P_n |   |  +
         *     d   c   +
         *     |   |     +
         *     +-a-|--b---+
         *       P_n + 1
         * x axis is outer loop
         * note that # threads > # cpu cores will have no effect (and may actually be worse)
         */

        double b = 0; // previous length
        double n = frame.length; // P_n
        double t = pointsPerThread; // target area
        for (int i = 0; i < numThreads; i++) {
            // start from (P_n + 1, 0) and iterate towards (0, 0)

            double d = 2 * n + 2;
            double a = Math.floor(Math.sqrt((d * (t + (b * b * n / d))) / n) - b);

            determineBlinkingDistChild child = null;
            if (i == numThreads - 1) child = new determineBlinkingDistChild(frame, loc, (int) b, (int) n, max, res, N); // last iter, take all 
            else child = new determineBlinkingDistChild(frame, loc, (int) b, (int) (b + a), max, res, N);               //   remaining area

            threads[i] = child;
            es.execute(child);

            b += a;
        }

        es.shutdown();
        
        try {es.awaitTermination(10_000, TimeUnit.MINUTES);}
        catch (InterruptedException e) {return;}

        int[] _binsBlink = new int[(int) Math.floor(max / res) + 1];
        int[] _binsNoBlink = new int[(int) Math.floor(max / res) + 1];
        int[][] _binsFittingBlink = new int[N][(int) Math.floor(max / res) + 1];

        // combine results from children
        for (int i = 0; i < threads.length; i++) {
            determineBlinkingDistChild child = threads[i];
            for (int j = 0; j < child.binsBlink.length; j++) {
                _binsBlink[j] += child.binsBlink[j];
                _binsNoBlink[j] += child.binsNoBlink[j];
            }

            for (int j = 0; j < N; j++) {
                for (int k = 0; k < child.binsBlink.length; k++) {
                    _binsFittingBlink[j][k] += child.binsFittingBlink[j][k];
                }
            }
        }

        int blinkCount = IntStream.of(_binsBlink).sum();
        int noBlinkCount = IntStream.of(_binsNoBlink).sum();

        binsBlink = new double[_binsBlink.length];
        binsNoBlink = new double[_binsNoBlink.length];
        for (int i = 0; i < binsBlink.length; i++) {
            binsBlink[i] = (double) _binsBlink[i] / blinkCount;
            binsNoBlink[i] = (double) _binsNoBlink[i] / noBlinkCount;
        }

        binsFittingBlink = new double[N][_binsBlink.length];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < _binsBlink.length; j++)  {
                double size = IntStream.of(_binsFittingBlink[i]).sum();
                binsFittingBlink[i][j] = (double) _binsFittingBlink[i][j] / size;
            }
        }
    }
}
