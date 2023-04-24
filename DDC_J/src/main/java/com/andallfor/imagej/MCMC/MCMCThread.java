package com.andallfor.imagej.MCMC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import com.andallfor.imagej.util;
import com.andallfor.imagej.passes.first.primaryPassCollector;
import com.andallfor.imagej.passes.second.secondaryPassCollector;

import com.jmatio.io.MatFileReader;
import com.jmatio.io.MatFileWriter;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLCell;
import com.jmatio.types.MLDouble;

public class MCMCThread implements Runnable {
    private int[] framesWithMulti;
    private double[] frame;
    private double[][] loc;

    // this is not efficient, but as this is just a proof of concept it is fine
    // replace this with a true caching system later
    private MutableIntSet truthIndexes; // TODO: try implementing without set (self impl) (for speed)
    private boolean[] repeatMask; // false = repeat true = truth

    private int res, N, probDistCols, probDistRows;

    private primaryPassCollector p1;
    private secondaryPassCollector p2;

    public MCMCThread(double[] frame, double[][] loc, int res, int N, primaryPassCollector p1, secondaryPassCollector p2) {
        this.p1 = p1;
        this.p2 = p2;
        this.frame = frame;
        this.loc = loc;
        this.res = res;
        this.N = N;

        framesWithMulti = new int[p1.framesWithMulti.size()]; // copy values to arr bc java bad or something
        int c = 0;
        for (Integer i : p1.framesWithMulti) {
            framesWithMulti[c] = i;
            c++;
        }

        probDistCols = p2.probDist[0].length;
        probDistRows = p2.probDist.length;
    }

    private double prepare() {
        truthIndexes = new IntHashSet((int) (p2.numTruth * 1.25));
        repeatMask = new boolean[frame.length];
        
        // use trajectories as our starting point
        for (int i = 0; i < p2.blinksMask.length; i++) {
            repeatMask[i] = !p2.blinksMask[i];
            if (repeatMask[i]) truthIndexes.add(i);
        }

        return getTrueScore();
    }

    private double checkAddT(int index, int[] indices) {
        double s = 0;

        for (int i = 0; i < indices.length; i++) {
            int dFra = (int) Math.abs(frame[indices[i]] - frame[index]);
            if (dFra >= N || dFra == 0) continue;

            int dLoc = (int) (util.dist(loc[indices[i]], loc[index]) / res);
            if (dLoc >= probDistCols) dLoc = probDistCols - 1;

            s -= p2.probDist[dFra - 1][dLoc]; // P_r1
            s += p2.probDist[probDistRows - 1][dLoc]; // P_t
        }

        return s;
    }

    private double checkRemoveT(int index, int[] indices) {
        double s = 0;

        for (int i = 0; i < indices.length; i++) {
            int dFra = (int) Math.abs(frame[indices[i]] - frame[index]);
            if (dFra >= N || dFra == 0) continue;

            int dLoc = (int) (util.dist(loc[indices[i]], loc[index]) / res);
            if (dLoc >= probDistCols) dLoc = probDistCols - 1;

            s += p2.probDist[dFra - 1][dLoc]; // P_r1
            s -= p2.probDist[probDistRows - 1][dLoc]; // P_t
        }

        return s;
    }

    private double getTrueScore() { // checked, is acc (up to ~5-6 digits)
        int[] indices = truthIndexes.toArray();

        double P_t = 0;
        double P_r1 = 0;

        // T -> T
        for (int i = 0; i < indices.length; i++) {
            for (int j = i + 1; j < indices.length; j++) {
                int dFra = (int) Math.abs(frame[indices[i]] - frame[indices[j]]);
                if (dFra >= N || dFra == 0) continue;

                int dLoc = (int) (util.dist(loc[indices[i]], loc[indices[j]]) / res);
                if (dLoc >= probDistCols) dLoc = probDistCols - 1;

                P_t += p2.probDist[probDistRows - 1][dLoc];
            }
        }

        // R -> R
        for (int i = 0 ; i < frame.length; i++) {
            if (repeatMask[i]) continue;

            for (int j = i + 1; j < frame.length; j++) {
                if (repeatMask[j]) continue;

                int dFra = (int) Math.abs(frame[i] - frame[j]);
                if (dFra >= N || dFra == 0) continue;

                int dLoc = (int) (util.dist(loc[i], loc[j]) / res);
                if (dLoc >= probDistCols) dLoc = probDistCols - 1;

                P_r1 += p2.probDist[dFra - 1][dLoc];
            }
        }

        // R -> T
        for (int i = 0 ; i < frame.length; i++) {
            if (repeatMask[i]) continue;

            for (int j = 0; j < indices.length; j++) {
                int dFra = (int) Math.abs(frame[i] - frame[indices[j]]);
                if (dFra >= N || dFra == 0) continue;

                int dLoc = (int) (util.dist(loc[i], loc[indices[j]]) / res);
                if (dLoc >= probDistCols) dLoc = probDistCols - 1;

                P_r1 += p2.probDist[dFra - 1][dLoc];
            }
        }

        return P_t + P_r1;
    }

    public void test() {
        double current = prepare();

        int maxSteps = 1000;
        int step = 0;

        int prev = -1;
        while (step < maxSteps) {
            double maxScore = 0;
            int t = 0;
            int[] idx = truthIndexes.toArray();
            for (int i = 0; i < repeatMask.length; i++) {
                double s = 0;
                if (repeatMask[i]) s = checkRemoveT(i, idx);
                else s = checkAddT(i, idx);

                if (s > maxScore) {
                    maxScore = s;
                    t = i;
                }
            }

            if (maxScore == 0) break;

            if (t == prev) break;
            prev = t;

            if (repeatMask[t]) truthIndexes.remove(t);
            else truthIndexes.add(t);
            repeatMask[t] = !repeatMask[t];
            current += maxScore;

            step++;
        }

        System.out.println("Predicted score: " + current);
        System.out.println("True score: " + getTrueScore());
        System.out.println("Num locs: " + truthIndexes.size());
        System.out.println("Steps: " + step);
        System.out.println("Indices: " + truthIndexes);
    }

    public void run() {}
}
