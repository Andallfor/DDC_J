package com.andallfor.imagej.MCMC;

import java.util.HashMap;

import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import com.andallfor.imagej.DDC_;
import com.andallfor.imagej.util;
import com.andallfor.imagej.passes.first.primaryPassCollector;

public class MCMCThread implements Runnable {
    private int[] framesWithMulti;
    private double[] frame;
    private double[][] loc;

    // this is not efficient, but as this is just a proof of concept it is fine
    // replace this with a true caching system later
    private MutableIntSet truthIndexes; // TODO: try implementing without set (self impl) (for speed)
    HashMap<Integer, IntArrayList> trajectories;
    private boolean[] repeatMask; // false = repeat true = truth

    private int probDistCols, probDistRows;
    private int imageNum;

    private primaryPassCollector p1;

    private linkTrajectories t;

    public MCMCThread(int imageNum, double[] frame, double[][] loc) {
        this.frame = frame;
        this.loc = loc;
        this.imageNum = imageNum;
        this.t = new linkTrajectories(imageNum, frame, loc, DDC_.firstPass.processedData[imageNum].frameLookup);

        this.p1 = DDC_.firstPass.processedData[imageNum];

        framesWithMulti = new int[p1.framesWithMulti.size()]; // copy values to arr bc java bad or something
        int c = 0;
        for (Integer i : p1.framesWithMulti) {
            framesWithMulti[c] = i;
            c++;
        }

        probDistCols = t.probDist[0].length;
        probDistRows = t.probDist.length;

        t.save("C:/Users/leozw/Desktop/outputTrajectories.mat");
    }

    private double prepare() {
        truthIndexes = new IntHashSet((int) (t.nonBlink * 1.25));
        repeatMask = new boolean[frame.length];
        
        // use trajectories as our starting point
        for (int i = 0; i < t.locBlink.length; i++) {
            repeatMask[i] = !t.locBlink[i];
            if (repeatMask[i]) truthIndexes.add(i);
        }

        return getTrueScore(truthIndexes.toArray(), repeatMask);
    }

    private double checkAddT(int index, int[] indices) {
        double s = 0;

        for (int i = 0; i < indices.length; i++) {
            int dFra = (int) Math.abs(frame[indices[i]] - frame[index]);
            if (dFra >= DDC_.N || dFra == 0) continue;

            int dLoc = (int) (util.dist(loc[indices[i]], loc[index]) / DDC_.res);
            if (dLoc >= probDistCols) dLoc = probDistCols - 1;

            s -= t.probDist[dFra - 1][dLoc]; // P_r1
            s += t.probDist[probDistRows - 1][dLoc]; // P_t
        }

        return s;
    }

    private double checkRemoveT(int index, int[] indices) {
        double s = 0;

        for (int i = 0; i < indices.length; i++) {
            int dFra = (int) Math.abs(frame[indices[i]] - frame[index]);
            if (dFra >= DDC_.N || dFra == 0) continue;

            int dLoc = (int) (util.dist(loc[indices[i]], loc[index]) / DDC_.res);
            if (dLoc >= probDistCols) dLoc = probDistCols - 1;

            s += t.probDist[dFra - 1][dLoc]; // P_r1
            s -= t.probDist[probDistRows - 1][dLoc]; // P_t
        }

        return s;
    }

    private double getTrueScore(int[] indices, boolean[] mask) { 
        double P_t = 0;
        double P_r1 = 0;

        // T -> T
        for (int i = 0; i < indices.length; i++) {
            for (int j = i + 1; j < indices.length; j++) {
                int dFra = (int) Math.abs(frame[indices[i]] - frame[indices[j]]);
                if (dFra >= DDC_.N || dFra == 0) continue;

                int dLoc = (int) (util.dist(loc[indices[i]], loc[indices[j]]) / DDC_.res);
                if (dLoc >= probDistCols) dLoc = probDistCols - 1;

                P_t += t.probDist[probDistRows - 1][dLoc];
            }
        }

        // R -> R
        for (int i = 0 ; i < frame.length; i++) {
            if (mask[i]) continue;

            for (int j = i + 1; j < frame.length; j++) {
                if (mask[j]) continue;

                int dFra = (int) Math.abs(frame[i] - frame[j]);
                if (dFra >= DDC_.N || dFra == 0) continue;

                int dLoc = (int) (util.dist(loc[i], loc[j]) / DDC_.res);
                if (dLoc >= probDistCols) dLoc = probDistCols - 1;

                P_r1 += t.probDist[dFra - 1][dLoc];
            }
        }

        // R -> T
        for (int i = 0 ; i < frame.length; i++) {
            if (mask[i]) continue;

            for (int j = 0; j < indices.length; j++) {
                int dFra = (int) Math.abs(frame[i] - frame[indices[j]]);
                if (dFra >= DDC_.N || dFra == 0) continue;

                int dLoc = (int) (util.dist(loc[i], loc[indices[j]]) / DDC_.res);
                if (dLoc >= probDistCols) dLoc = probDistCols - 1;

                P_r1 += t.probDist[dFra - 1][dLoc];
            }
        }

        return P_t + P_r1;
    }

    public void run() {}
}
