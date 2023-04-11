package com.andallfor.imagej.passes.second;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

import org.orangepalantir.leastsquares.fitters.NonLinearSolver;
import org.orangepalantir.leastsquares.Fitter;

import com.andallfor.imagej.linearSolver;
import com.andallfor.imagej.util;
import com.andallfor.imagej.imagePass.imagePassAction;
import com.andallfor.imagej.imagePass.imagePassCallback;

public class secondaryPassCollector implements imagePassCallback {
    private double max;
    private int res, N;

    public secondaryPassCollector(double max, int res, int N) {
        this.max = max;
        this.res = res;
        this.N = N;
    }

    public void callback(imagePassAction[] threads) {
        secondaryPassAction child = (secondaryPassAction) threads[0]; // guaranteed to only have one thread per image

        //---------------------------------------------------//
        // DDC_MCMC.m                                        //
        //---------------------------------------------------//

        ArrayList<Double> _trueDist = new ArrayList<Double>();
        ArrayList<Double> _distBlink = new ArrayList<Double>();
        for (int i = 0; i < child.binsTrueDist.length; i++) { // cull all bins with a zero prob
            if (i * res > child.maxLocDistControl) break;
            _trueDist.add((double) child.binsTrueDist[i]);
            _distBlink.add(secondaryPass.distribution_for_blink[i]);
        }

        double[] trueDist = new double[_trueDist.size()];
        double[] distBlink = new double[_distBlink.size()];
        for (int i = 0; i < trueDist.length; i++) {
            trueDist[i] = _trueDist.get(i);
            distBlink[i] = _distBlink.get(i);
        }
        double trueDistSum = util.sumArr(trueDist, 0, trueDist.length);
        for (int i = 0; i < trueDist.length; i++) trueDist[i] /= trueDistSum;

        double[] trueDistBig = util.compressBins(trueDist);
        double[] distBlinkBig = util.compressBins(distBlink);

        //---------------------------------------------------//
        // Determine_Deviation_in_Probability8.m             //
        //---------------------------------------------------//
        double[] localDScaleStore = new double[secondaryPass.d_scale_store.length];
        Fitter solver = new NonLinearSolver(linearSolver.func);
        double[] initialPara = new double[] {1};

        double nb = (child.frame.length - child.numBlinks) / (double) child.numBlinks;
        double _nb = nb + nb + nb * nb;
        double m = 1 - (_nb / (1 + _nb));

        double[][] t = new double[trueDistBig.length][2];
        for (int j = 0; j < trueDistBig.length; j++) {t[j] = new double[] {trueDistBig[j], distBlinkBig[j]};}

        for (int i = 0; i < N; i++) {
            // remove zero prob from _d_blink
            double[] _d_blink = util.compressBins(secondaryPass.binsFittingBlink[child.imageNum][i]);
            double[] d_blink = new double[trueDistBig.length];
            System.arraycopy(_d_blink, 0, d_blink, 0, d_blink.length);

            double d = m * secondaryPass.d_scale_store[i];
            double[] y = util.arrSubOut(d_blink, util.arrMultiOut(trueDistBig, d));

            for (int j = 0; j < y.length; j++) {if (y[j] < 0) y[j] = 0;}
            double s = util.sumArr(y, 0, y.length);
            for (int j = 0; j < y.length; j++) y[j] /= s;

            solver.setData(t, y);
            solver.setParameters(initialPara);
            solver.fitData();
            double x = solver.getParameters()[0];
            if (x > 1) x = 1;
            else if (x < 0) x = 0.0000001;
            localDScaleStore[i] = x;
        }

        localDScaleStore[localDScaleStore.length - 1] = 1;
    }
}
