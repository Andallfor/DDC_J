package com.andallfor.imagej.passes.second;

import java.util.stream.IntStream;

import com.andallfor.imagej.imagePass.imagePassAction;
import com.andallfor.imagej.imagePass.imagePassCallback;

public class secondaryPassCollector implements imagePassCallback {
    private double max;
    private int res, N;

    public double[] trueDist;

    public secondaryPassCollector(double max, int res, int N) {
        this.max = max;
        this.res = res;
        this.N = N;
    }

    public void callback(imagePassAction[] threads) {
        int[] _trueDist = new int[(int) Math.floor(max / res) + 1];

        // combine
        for (int i = 0; i < threads.length; i++) {
            secondaryPassAction child = (secondaryPassAction) threads[i];

            for (int j = 0; j < child.binsTrueDist.length; j++) {
                _trueDist[j] += child.binsTrueDist[j];
            }
        }

        // assemble

        //---------------------------------------------------//
        // DDC_MCMC.m                                        //
        //---------------------------------------------------//

        // True_Distribution (in src its spelled True_Distribuiton fyi incase you're ctrl+f for it)
        int trueDistCount = IntStream.of(_trueDist).sum();

        trueDist = new double[_trueDist.length];
        // dont want 0 prob
        if (_trueDist[_trueDist.length - 1] == 0) trueDist = new double[_trueDist.length - 1];
        for (int i = 0; i < trueDist.length; i++) trueDist[i] = (double) _trueDist[i] / (double) trueDistCount;
    }
}
