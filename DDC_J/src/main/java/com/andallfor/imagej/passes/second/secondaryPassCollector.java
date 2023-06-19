package com.andallfor.imagej.passes.second;

import java.util.ArrayList;

import com.andallfor.imagej.DDC_;
import com.andallfor.imagej.util;
import com.andallfor.imagej.imagePass.imagePassAction;
import com.andallfor.imagej.imagePass.imagePassCallback;

public class secondaryPassCollector implements imagePassCallback {
    private double max;
    private int res, N;

    public double[] trueDistBig, distBlinkBig, trueDist, distBlink;

    public secondaryPassCollector(double max, int res, int N) {
        this.max = max;
        this.res = res;
        this.N = N;
    }

    public void callback(imagePassAction[] threads) {
        // TODO allow this to be threaded again
        secondaryPassAction child = (secondaryPassAction) threads[0];

        //---------------------------------------------------//
        // DDC_MCMC.m                                        //
        //---------------------------------------------------//

        ArrayList<Double> _trueDist = new ArrayList<Double>();
        ArrayList<Double> _distBlink = new ArrayList<Double>();
        for (int i = 0; i < child.binsTrueDist.length; i++) { // cull all bins with a zero prob
            if (i * res > child.maxLocDistControl) break;
            _trueDist.add((double) child.binsTrueDist[i]);
            _distBlink.add(DDC_.blinkDist.distribution_for_blink[i]);
        }

        trueDist = new double[_trueDist.size()];
        distBlink = new double[_distBlink.size()];
        for (int i = 0; i < trueDist.length; i++) {
            trueDist[i] = _trueDist.get(i);
            distBlink[i] = _distBlink.get(i);
        }
        double trueDistSum = util.sumArr(trueDist, 0, trueDist.length);
        for (int i = 0; i < trueDist.length; i++) trueDist[i] /= trueDistSum;

        trueDistBig = util.compressBins(trueDist);
        distBlinkBig = util.compressBins(distBlink);
    }
}
