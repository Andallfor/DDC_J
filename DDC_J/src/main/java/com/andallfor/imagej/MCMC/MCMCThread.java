package com.andallfor.imagej.MCMC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import com.andallfor.imagej.util;
import com.andallfor.imagej.passes.first.primaryPassCollector;
import com.andallfor.imagej.passes.second.secondaryPassCollector;

public class MCMCThread implements Runnable {
    private int[] framesWithMulti;
    private double[] frame;
    private double[][] loc;

    private int res;

    private primaryPassCollector p1;
    private secondaryPassCollector p2;

    public MCMCThread(double[] frame, double[][] loc, int res, primaryPassCollector p1, secondaryPassCollector p2) {
        this.p1 = p1;
        this.p2 = p2;
        this.frame = frame;
        this.loc = loc;
        this.res = res;

        framesWithMulti = new int[p1.framesWithMulti.size()]; // copy values to arr bc java bad or something
        int c = 0;
        for (Integer i : p1.framesWithMulti) {
            framesWithMulti[c] = i;
            c++;
        }
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
