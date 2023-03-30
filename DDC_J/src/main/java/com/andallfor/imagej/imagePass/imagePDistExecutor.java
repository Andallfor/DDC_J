package com.andallfor.imagej.imagePass;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.andallfor.imagej.util;

public class imagePDistExecutor implements Runnable {
    private double[] frame;
    private double[][] loc;
    private int pointsPerThread;

    private imagePassAction action;
    private imagePassCallback callback;
    private double[] inputs;

    public imagePDistExecutor(double[] frame, double[][] loc, int pointsPerThread) {
        this.frame = frame;
        this.loc = loc;
        this.pointsPerThread = pointsPerThread;
    }

    public void setParameters(imagePassAction action, imagePassCallback callback, double ...inputs) {
        this.action = action;
        this.callback = callback;
        this.inputs = inputs;
    }

    public void run() {
        ExecutorService es = Executors.newCachedThreadPool();
        int numThreads = (int) Math.ceil(util.sumFactorial(frame.length - 1) / (double) pointsPerThread);
        imagePassAction[] threads = new imagePassAction[numThreads];

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

            imagePassAction act = action.createSelf(frame, loc, inputs);
            
            if (i == numThreads - 1) act.setRange((int) b, (int) n);
            else act.setRange((int) b, (int) (b + a));

            threads[i] = act;
            es.execute(act);

            b += a;
        }

        es.shutdown();

        try {es.awaitTermination(10_000, TimeUnit.MINUTES);}
        catch (InterruptedException e) {return;}

        callback.callback(threads);
    }
}
