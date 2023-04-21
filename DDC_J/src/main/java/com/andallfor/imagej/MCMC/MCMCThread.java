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
        // pick a random index, this will be our starting point
        for (int i = 0; i < 600; i++) {
            int r = (int) (Math.random() * frame.length);

            if (repeatMask[r]) continue;

            //System.out.println(getTrueScore());
            //System.out.println("s: " + checkAddT(r));

            truthIndexes.add(r);
            //System.out.println(result);
            repeatMask[r] = true;
            //System.out.println(getTrueScore());
            //System.out.println("-------");
        }

        int ra = 0;
        while (true) {
            ra = (int) (Math.random() * frame.length);
            if (!repeatMask[ra]) break;
        }

        System.out.println(getTrueScore());
        System.out.println(checkAddT(ra));
        truthIndexes.add(ra);
        repeatMask[ra] = true;
        System.out.println(getTrueScore());
        


        return 0;
    }

    private double checkAddT(int index) {
        int[] indices = truthIndexes.toArray(); // TODO: dont do this

        double s = 0;

        for (int i = 0; i < indices.length; i++) {
            int dFra = (int) Math.abs(frame[indices[i]] - frame[index]);
            if (dFra >= N || dFra == 0) continue;

            int dLoc = (int) (util.dist(loc[indices[i]], loc[index]) / res);
            if (dLoc >= probDistCols) dLoc = probDistCols - 1;

            //System.out.println(p2.probDist[dFra - 1][dLoc]);
            //System.out.println(p2.probDist[probDistRows - 1][dLoc]);

            s -= p2.probDist[dFra - 1][dLoc]; // P_r1
            s += p2.probDist[probDistRows - 1][dLoc]; // P_t
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

    public void test() { // -1.181903.809881098e+06 -> -1181903 // -1187740.6708987467
        double current = prepare();
        /*

        int maxSteps = frame.length - 1;
        int step = 0;
        int l = frame.length;

        System.out.println(current);

        while (step < maxSteps) {
            double maxScore = 0;
            int index = 0;
            for (int i = 0; i < l; i++) {
                if (repeatMask[i]) continue;

                double s = getChangeAdd(i);
                if (maxScore < s) {
                    maxScore = s;
                    index = i;
                }
            }

            if (maxScore == 0) break;

            truthIndexes.add(index);
            repeatMask[index] = true;
            current += maxScore;

            step++;

            //System.out.println(maxScore);
        }

        System.out.println("-----");

        System.out.println(current);
        System.out.println(truthIndexes.size());
        //System.out.println(truthIndexes.toString());
        System.out.println(getTrueScore());
        */
    }

    public void run() {}

    public void calcOptimalFrameOrder() {
        long s1 = System.currentTimeMillis();

        // TODO: rewrite explanation! basic idea remains however details are different- ex lowest frame is no longer correct
        // frames with multi represents points that are on the same and relatively close to each other (res * 4)
        // we are allowed to switch these points interchangeably with each other (array order-wise not their actual values)
        //      "these points" are all frame values == to frameWithMulti
        // which will cause the trajectory linking alg to change the order of which they are linked, thus changing which
        // points are considered to be a blink and which are a link. we will then simulate the change in linking via calculating
        // how the calculated score would change depending on which position is blink/link
        // valid positions are the intersection of frameWithMulti ("these points" from above) and the lowest frame of each trajectory
        // (as each trajectory's blink will always be the lowest frame available)
        // rather than actually calculate the new score for each possible variation, we will estimate to save time
        // this is done by in a previous pass that rounds each axis of each point to the nearest res, then counting the number with
        // that res. we round to the nearest res because we have to round it to the nearest res anyways in order to get the probDist index

        // for each trajectory, get the points we can play around with (intersection of lowest frame of trajectory and framesWIthMulti)
        HashSet<Integer> framesWithMultiValue = new HashSet<Integer>(framesWithMulti.length);
        for (int i = 0; i < framesWithMulti.length; i++) framesWithMultiValue.add((int) frame[(int) framesWithMulti[i]]);

        // get all the frames that match frameWithMulti
        HashMap<Integer, ArrayList<Integer>> framesWithMultiIndices = new HashMap<Integer, ArrayList<Integer>>(framesWithMulti.length); // value, indices
        HashMap<Integer, frameOrderTrajectoryWrapper> intersectedTrajectories = new HashMap<Integer, frameOrderTrajectoryWrapper>(framesWithMulti.length); // trajectory num, indices
        HashMap<Integer, Double> hashedFrameWithMultiScores = new HashMap<Integer, Double>(); // hash (loc), score
        Double dCache = 0.0;
        for (int i = 0; i < frame.length; i++) {
            Integer f = (int) frame[i];
            if (!framesWithMultiValue.contains(f)) {
                // check to see if this belongs to a trajectory
                if (intersectedTrajectories.containsKey(p2.trajectories[i])) intersectedTrajectories.get(p2.trajectories[i]).indices.add(i);
                continue;
            }

            if (framesWithMultiIndices.containsKey(f)) framesWithMultiIndices.get(f).add(i);
            else {
                ArrayList<Integer> arr = new ArrayList<Integer>();
                arr.add(i);
                framesWithMultiIndices.put(f, arr);
            }

            Integer h = util.hashNDPoint(loc[i], res, p1.dOverallBoundsHalf, p1.dOverallHashOffset);
            if (!hashedFrameWithMultiScores.containsKey(h)) hashedFrameWithMultiScores.put(h, dCache);

            if (p2.trajectories[i] == 0) continue; // == 0 means not part of a trajectory

            // get all trajectories that intersect a frameWithMulti
            if (intersectedTrajectories.containsKey(p2.trajectories[i])) {
                intersectedTrajectories.get(p2.trajectories[i]).indices.add(i);
                intersectedTrajectories.get(p2.trajectories[i]).hits.add(i);
                intersectedTrajectories.get(p2.trajectories[i]).framesWithMultiIntersections.add(f);
                if (p2.blinksMask[i]) intersectedTrajectories.get(p2.trajectories[i]).blinks.add(i);
            } else {
                ArrayList<Integer> idx = new ArrayList<Integer>(); idx.add(i);
                HashSet<Integer> intersection = new HashSet<Integer>(); intersection.add(f);
                ArrayList<Integer> hits = new ArrayList<Integer>(); hits.add(i);
                ArrayList<Integer> blinks = new ArrayList<Integer>(); if (p2.blinksMask[i]) blinks.add(i);
                frameOrderTrajectoryWrapper wrapper = new frameOrderTrajectoryWrapper(p2.trajectories[i], idx, intersection, hits, blinks);

                intersectedTrajectories.put(p2.trajectories[i], wrapper);
            }
        }

        // organize trajectories by which framesWithMulti they intersect
        HashMap<Integer, ArrayList<frameOrderTrajectoryWrapper>> trajectoryFramesWithMulti = new HashMap<Integer, ArrayList<frameOrderTrajectoryWrapper>>(framesWithMulti.length);
        for (frameOrderTrajectoryWrapper v : intersectedTrajectories.values()) {
            for (Integer key : v.framesWithMultiIntersections) {
                if (!trajectoryFramesWithMulti.containsKey(key)) trajectoryFramesWithMulti.put(key, new ArrayList<frameOrderTrajectoryWrapper>());

                trajectoryFramesWithMulti.get(key).add(v);
            }
        }

        // need to round down loc frame with multi, then for each unique one calc score
        // for each blink within trajectory that intersects (.blinks), check to see if their score is the hgihest out of the available scores on that frame (will have alr been calced in prior step
        //      since each blink is also part of frame with multi
        int offset = p1.dOverallBoundsHalf / res;
        for (Integer hash : hashedFrameWithMultiScores.keySet()) {
            int[] point = util.unHashNDPoint(hash, 1, offset, p1.dOverallHashOffset);

            double score = 0;
            for (Integer dHash : p1.dOverallCount.keySet()) {
                int[] target = util.unHashNDPoint(dHash, 1, offset, p1.dOverallHashOffset);
                int i = util.dist(point, target);
                if (i + 1 >= p2.probDist[0].length) i = p2.probDist[0].length - 2;

                score += p2.probDist[p2.probDist.length - 1][i + 1];
            }
        }

        // TODO this only calcs lik, not lik2 rn!!



        // to check, first self verify if p2.trajectories are valid, then generate loc and fram (onl the blink values), then run in matlab calc_score. then run etts.m with vvse=2 for like 15 mins and see what their max score is

        //framesWithMultiIndices.forEach((k, v) -> System.out.println(k + v.toString()));
        //intersectedTrajectories.forEach((k, v) -> System.out.println(v));

        // for each blink, try out each loc

        // NOTE: in src, vvse==2 changes order of loc but not frame!!!

        // dont need to calc how the score changes when removing the og point -> we need change in score, not actual score and since they will all start from same point, og score (remnant) does not matter
        // though may have to calc in order to determine if it could be part of the trajectory
        // maybe have function that optimizes not only score but also increases teh probabilty of as much as possible (the second check in trajectory assignment)
        // what is easier to calc, score or validily? -> do easier one first so we can throw away points asap

        // also needs to have a way to throw data into matlab for it to calc score for us

        // also considering listing the points that we "want" (would be very benefical for score, but are not valid) since in the other two algs we change the weights, so could make them valid

        // need to focus on allowing alg to throw away as many values as possiebl

        // consider making an ai for this? it seems like itd actualyl be a pretty good use case (optimizing)

        // frame never changes order, just the underlying loc values
        // therefore blinks will not change frame position

        // each score calc change is O(n) while full score calc is O(n^2) (although n here is smaller as it is only the blinks)
        // but this is then easier since we round everthing before hand (dOverall) so itll be anywhere from O(n) to O(1)

        System.out.println("Preliminary vvse 2: " + (System.currentTimeMillis() - s1));
    }
}
