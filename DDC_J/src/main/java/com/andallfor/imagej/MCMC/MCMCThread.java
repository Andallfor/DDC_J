package com.andallfor.imagej.MCMC;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class MCMCThread implements Runnable {
    private int[] framesWithMulti;
    private double[] frame;
    private double[][] loc;
    private int numLinked;
    private int[] trajectories;

    public MCMCThread(HashSet<Integer> _framesWithMulti, double[] frame, double[][] loc, int nonBlink, int[] trajectories) {
        framesWithMulti = new int[_framesWithMulti.size()]; // copy values to arr bc java bad or something
        int c = 0;
        for (Integer i : _framesWithMulti) {
            framesWithMulti[c] = i;
            c++;
        }

        this.frame = frame;
        this.loc = loc;
        this.numLinked = nonBlink;
        this.trajectories = trajectories;
    }

    public void run() {
    }

    public void calcOptimalFrameOrder() {
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
        HashMap<Integer, ArrayList<Integer>> framesWithMultiIndices = new HashMap<Integer, ArrayList<Integer>>(framesWithMulti.length);
        HashMap<Integer, frameOrderTrajectoryWrapper> intersectedTrajectories = new HashMap<Integer, frameOrderTrajectoryWrapper>(framesWithMulti.length); // trajectory num, indices
        for (int i = 0; i < frame.length; i++) {
            Integer f = (int) frame[i];
            if (!framesWithMultiValue.contains(f)) continue;

            if (framesWithMultiIndices.containsKey(f)) framesWithMultiIndices.get(f).add(i);
            else {
                ArrayList<Integer> arr = new ArrayList<Integer>();
                arr.add(i);
                framesWithMultiIndices.put(f, arr);
            }

            if (trajectories[i] == 0) continue; // == 0 means not part of a trajectory, TODO: check to make sure they are ignored when calc score

            // get all trajectories that intersect a frameWithMulti
            if (intersectedTrajectories.containsKey(trajectories[i])) {
                intersectedTrajectories.get(trajectories[i]).indices.add(i);
                intersectedTrajectories.get(trajectories[i]).framesWithMultiIntersections.add(f);
            } else {
                ArrayList<Integer> idx = new ArrayList<Integer>();
                idx.add(i);
                HashSet<Integer> intersection = new HashSet<Integer>();
                intersection.add(f);
                frameOrderTrajectoryWrapper wrapper = new frameOrderTrajectoryWrapper(trajectories[i], idx, intersection);

                intersectedTrajectories.put(trajectories[i], wrapper);
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

        for (frameOrderTrajectoryWrapper v : intersectedTrajectories.values()) {
            System.out.println(v);
        }
    }
}
