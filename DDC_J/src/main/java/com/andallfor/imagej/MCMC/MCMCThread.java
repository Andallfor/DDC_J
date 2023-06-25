package com.andallfor.imagej.MCMC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import com.andallfor.imagej.DDC_;
import com.andallfor.imagej.util;

public class MCMCThread {
    private double[] frame;
    private double[][] loc;

    private HashSet<Integer> truthIndices;

    private int probDistCols, probDistRows;
    private int imageNum;

    private linkTrajectories t;

    public MCMCThread(int imageNum, double[] frame, double[][] loc) {
        this.imageNum = imageNum;
        this.frame = frame;
        this.loc = loc;
        this.t = new linkTrajectories(imageNum, frame, loc);

        probDistCols = t.probDist[0].length;
        probDistRows = t.probDist.length;
    }

    public void run() {
        int step = 0;
        double maxScore = prepare();
        double tempMaxScore = maxScore;
        double currentScore = maxScore;
        int initialSize = truthIndices.size();

        HashSet<Integer> bestTruthIndices = deepCopyTruthIndices(truthIndices);

        HashSet<Integer> truthIndicesBackup = deepCopyTruthIndices(truthIndices);
        boolean[] prevLocBlink = deepCopyBoolArr(t.locBlink);
        boolean[] prevLocBlinkBackup = deepCopyBoolArr(t.locBlink);
        t.backup();

        t.save("C:/Users/leozw/Desktop/outputTrajectories.mat");

        System.out.println(maxScore);

        while (step < 100) {
            t.prod();
            t.link();

            // find out what changed
            boolean hasChanged = false;
            for (int i = 0; i < frame.length; i++) {
                if (prevLocBlink[i] != t.locBlink[i]) {
                    prevLocBlink[i] = t.locBlink[i];

                    if (prevLocBlink[i]) { // it is now a repeat, remove a truth
                        currentScore += checkRemoveT(i, new ArrayList<Integer>(truthIndices)); // fuck java, i hate having to do all these conversions
                        truthIndices.remove(i);
                    } else { // it is now a repeat, remove a truth
                        currentScore += checkAddT(i, new ArrayList<Integer>(truthIndices));
                        truthIndices.add(i);
                    }

                    hasChanged = true;
                }
            }

            if (!hasChanged) continue;

            // blinking causes more molecules to appear than there actually are, so the program will tend to over count. discourage this by penalizing any
            // deviation away from the initial size (+ and -)
            double sizePenalty = Math.sqrt(Math.abs((double) truthIndices.size() / initialSize)) * tempMaxScore;

            System.out.println("Step: " + step + "  |  Score: " + (int) currentScore + "  |  Count: " + truthIndices.size() + "  |  c1: " + t.c1.unique() + "  |  c2: " + t.c2.unique());

            if (currentScore + sizePenalty >= tempMaxScore || Math.log(Math.random()) <= (currentScore - tempMaxScore)) {
                // backup data so we can return to this point
                t.backup();
                truthIndicesBackup = deepCopyTruthIndices(truthIndices);
                prevLocBlinkBackup = deepCopyBoolArr(prevLocBlink);
                tempMaxScore = currentScore;

                if (currentScore > maxScore) {
                    maxScore = currentScore;
                    tempMaxScore = currentScore;
                    step = 10;

                    t.save("C:/Users/leozw/Desktop/outputTrajectories.mat");
                    bestTruthIndices = deepCopyTruthIndices(truthIndices);
                }
            } else {
                t.restore();
                truthIndices = deepCopyTruthIndices(truthIndicesBackup);
                prevLocBlink = deepCopyBoolArr(prevLocBlinkBackup);
                currentScore = tempMaxScore;
            }

            step++;
        }

        System.out.println(maxScore);
        System.out.println(getTrueScore(bestTruthIndices));
    }

    private HashSet<Integer> deepCopyTruthIndices(HashSet<Integer> set) {
        HashSet<Integer> output = new HashSet<Integer>(set.size());
        for (Integer i : set) output.add(i);

        return output;
    }

    private boolean[] deepCopyBoolArr(boolean[] arr) {
        boolean[] output = new boolean[arr.length];
        for (int i = 0; i < output.length; i++) output[i] = arr[i];

        return output;
    }

    private double prepare() {
        truthIndices = new HashSet<Integer>((int) (t.nonBlink * 1.25));
        
        // use trajectories as our starting point
        for (int i = 0; i < t.locBlink.length; i++) {
            if (!t.locBlink[i]) truthIndices.add(i);
        }

        return getTrueScore(truthIndices);
    }

    private double checkAddT(int index, ArrayList<Integer> indices) {
        double s = 0;

        for (int i = 0; i < indices.size(); i++) {
            int dFra = (int) Math.abs(frame[indices.get(i)] - frame[index]);
            if (dFra >= DDC_.N || dFra == 0) continue;

            int dLoc = (int) (util.dist(loc[indices.get(i)], loc[index]) / DDC_.res);
            if (dLoc >= probDistCols) dLoc = probDistCols - 1;

            s -= t.probDist[dFra - 1][dLoc]; // P_r1
            s += t.probDist[probDistRows - 1][dLoc]; // P_t
        }

        return s;
    }

    private double checkRemoveT(int index, ArrayList<Integer> indices) {
        double s = 0;

        for (int i = 0; i < indices.size(); i++) {
            int dFra = (int) Math.abs(frame[indices.get(i)] - frame[index]);
            if (dFra >= DDC_.N || dFra == 0) continue;

            int dLoc = (int) (util.dist(loc[indices.get(i)], loc[index]) / DDC_.res);
            if (dLoc >= probDistCols) dLoc = probDistCols - 1;

            s += t.probDist[dFra - 1][dLoc]; // P_r1
            s -= t.probDist[probDistRows - 1][dLoc]; // P_t
        }

        return s;
    }

    private double getTrueScore(HashSet<Integer> truths) { 
        ArrayList<Integer> indices = new ArrayList<Integer>(truths);

        double P_t = 0;
        double P_r1 = 0;

        // T -> T
        for (int i = 0; i < indices.size(); i++) {
            for (int j = i + 1; j < indices.size(); j++) {
                int dFra = (int) Math.abs(frame[indices.get(i)] - frame[indices.get(j)]);
                if (dFra >= DDC_.N || dFra == 0) continue;

                int dLoc = (int) (util.dist(loc[indices.get(i)], loc[indices.get(j)]) / DDC_.res);
                if (dLoc >= probDistCols) dLoc = probDistCols - 1;

                P_t += t.probDist[probDistRows - 1][dLoc];
            }
        }

        // R -> R
        for (int i = 0 ; i < frame.length; i++) {
            if (truths.contains(i)) continue;

            for (int j = i + 1; j < frame.length; j++) {
                if (truths.contains(j)) continue;

                int dFra = (int) Math.abs(frame[i] - frame[j]);
                if (dFra >= DDC_.N || dFra == 0) continue;

                int dLoc = (int) (util.dist(loc[i], loc[j]) / DDC_.res);
                if (dLoc >= probDistCols) dLoc = probDistCols - 1;

                P_r1 += t.probDist[dFra - 1][dLoc];
            }
        }

        // R -> T
        for (int i = 0 ; i < frame.length; i++) {
            if (truths.contains(i)) continue;

            for (int j = 0; j < indices.size(); j++) {
                int dFra = (int) Math.abs(frame[i] - frame[indices.get(j)]);
                if (dFra >= DDC_.N || dFra == 0) continue;

                int dLoc = (int) (util.dist(loc[i], loc[indices.get(j)]) / DDC_.res);
                if (dLoc >= probDistCols) dLoc = probDistCols - 1;

                P_r1 += t.probDist[dFra - 1][dLoc];
            }
        }

        return P_t + P_r1;
    }
}
