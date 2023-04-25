package com.andallfor.imagej.MCMC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import org.eclipse.collections.api.list.primitive.MutableDoubleList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.MutableDoubleDoubleMap;
import org.eclipse.collections.api.map.primitive.MutableIntDoubleMap;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.api.stack.primitive.MutableIntStack;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.DoubleDoubleHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntDoubleHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntIntHashMap;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import com.andallfor.imagej.util;
import com.andallfor.imagej.passes.first.primaryPassCollector;
import com.andallfor.imagej.passes.second.secondaryPass;
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

    private MutableIntList getDesired() {
        long s1 = System.currentTimeMillis();
        double current = prepare();
        System.out.println("\nPreparation: " + (System.currentTimeMillis() - s1));
        s1 = System.currentTimeMillis();

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
        System.out.println("Greedy Search: " + (System.currentTimeMillis() - s1));
        s1 = System.currentTimeMillis();

        MutableIntIntMap desired = new IntIntHashMap(truthIndexes.size());
        int[] map = truthIndexes.toArray();
        for (int i = 0; i < map.length; i++) desired.put(map[i], (int) (checkRemoveT(map[i], map) * 10_000));
        MutableIntList d = desired.keySet().toSortedList((k1, k2) -> { // maybe use stack?
            if (desired.get(k1) == desired.get(k2)) return 0;
            if (desired.get(k1) > desired.get(k2)) return 1;
            return -1;
        });

        System.out.println("Sorting: " + (System.currentTimeMillis() - s1));

        return d;
    }

    public void test() {
        long s1 = System.currentTimeMillis();

        MutableIntList desired = getDesired();
        MutableIntList filtered = new IntArrayList(desired.size());

        boolean[] trajectoryMask = new boolean[frame.length];
        int ddtClamp = secondaryPass.blinkDist.m_mat[0].length;
        while (desired.size() > 0) {
            // goal is to maximize size of trajectory
            // find the point that would increase the prob of each trajectory the most
            // termination conditions: no new variation is valid (p<0.5), all points have been assigned to a trajectory
            // current trajectory is not allowed to join any other trajectories
            // need to test allowing trajectory to take other locs (if score <, or no restrictions)

            int currentIndex = desired.getLast();
            desired.removeAtIndex(desired.size() - 1); // just use stack

            MutableIntList currentTrajectory = new IntArrayList(15);
            MutableIntSet currentTrajectoryFrames = new IntHashSet(15);
            currentTrajectory.add(currentIndex);
            currentTrajectoryFrames.add((int) frame[currentIndex]);

            double rangeMin = frame[currentIndex], rangeMax = frame[currentIndex];

            trajectoryMask[currentIndex] = true;
            while (true) {
                int initialSize = currentTrajectory.size();
                for (int i = 0; i < frame.length; i++) {
                    if (trajectoryMask[i]) continue;
    
                    double fDist = Math.max(Math.abs(rangeMin - frame[i]), Math.abs(rangeMax - frame[i]));
    
                    if (fDist > N) continue;
                    if (currentTrajectoryFrames.contains((int) frame[i])) continue;

                    double currentScore = 0;
                    for (int j = 0; j < currentTrajectory.size(); j++) {
                        int r = (int) Math.abs(frame[currentTrajectory.get(j)] - frame[i]);
                        int c = (int) (util.dist(loc[currentTrajectory.get(j)], loc[i]) / res) + 1;
                        if (c > ddtClamp) c = ddtClamp;

                        currentScore += secondaryPass.blinkDist.m_mat[r - 1][c - 1];
                    }

                    if (currentScore / (currentTrajectory.size() + 1) <= 0.5) continue;
                    trajectoryMask[i] = true;
                    currentTrajectory.add(i);
                    currentTrajectoryFrames.add((int) frame[i]);

                    if (desired.contains(i)) desired.remove(i);

                    if (frame[i] < rangeMin) rangeMin = frame[i];
                    if (frame[i] > rangeMax) rangeMax = frame[i];

                    break;
                }

                if (currentTrajectory.size() == initialSize) break;
            }

            if (currentTrajectory.size() == 1) {
                trajectoryMask[currentIndex] = false;
            } else {
                filtered.add(currentIndex);
                System.out.println(currentTrajectory);
            }
        }

        System.out.println(filtered);
        System.out.println(filtered.size());
        System.out.println(desired.size());


        System.out.println("Search Total Time: " + (System.currentTimeMillis() - s1) + "\n");
    }

    public void run() {}
}
