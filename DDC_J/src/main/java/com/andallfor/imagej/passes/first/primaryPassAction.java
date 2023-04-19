package com.andallfor.imagej.passes.first;

import java.util.ArrayList;
import java.util.HashMap;

import com.andallfor.imagej.util;
import com.andallfor.imagej.imagePass.imagePassAction;

public class primaryPassAction extends imagePassAction {
    private int N, res;
    private double maxLocDist, maxFrameDist, maxFrameValue;

    public int[] binsBlink, binsNoBlink, density, dOverallHashOffset;
    public HashMap<Integer, Integer> dOverallCount;
    public int[][] binsFittingBlink;
    public byte[] distMatrixValidator;
    public ArrayList<Integer> framesWithMulti; // stores indexes of frame value, not the actual frame value
    public int dOverallBoundsHalf;

    public primaryPassAction(double maxLocDist, double maxFrameDist, double maxFrameValue, int res, int N) {
        this.N = N;
        this.res = res;
        this.maxLocDist = maxLocDist;
        this.maxFrameDist = maxFrameDist;
        this.maxFrameValue = maxFrameValue;
    }

    public void run() {
        binsBlink =         new int   [(int) Math.floor(maxLocDist / res) + 1];
        binsNoBlink =       new int   [(int) Math.floor(maxLocDist / res) + 1];

        dOverallCount = new HashMap<Integer, Integer>(frame.length); // worse case scenario
        binsFittingBlink =  new int[N][(int) Math.floor(maxLocDist / res) + 1];
        density =           new int[frame.length];

        int dOverallBounds = (int) (Math.floor(maxLocDist / res) + 1) * 2;
        dOverallBoundsHalf = (int) Math.floor(maxLocDist / res) + 1;
        dOverallHashOffset = new int[loc[0].length];
        for (int i = 0; i < loc[0].length; i++) dOverallHashOffset[i] = (int) Math.pow(dOverallBounds, i);

        boolean[] _framesWithMultiBuffer = new boolean[(int) maxFrameValue]; // io from arrList is expensive, but we also dont want to iterate across the entire arr
                                                                             // so instead write to a buffer here, and if we succeed then write to arrList. this is better
                                                                             // because it prevents autoboxing (since arrList has to be with objects for reading and we use
                                                                             // primitives). additionally memory cost is fine since this is boolean (1 byte (not 1 bit due to padding))
                                                                             // the rest of the program is like 1.5 gib as of writing, so 0.01 mb isnt going to do anything
        framesWithMulti = new ArrayList<Integer>();
        double resThreshold = res * 4;
        double distanceDensityThreshold = res * 2;

        distMatrixValidator = new byte[(int) maxFrameDist - N];

        for (int i = start; i < end; i++) {
            for (int j = i + 1; j < frame.length; j++) {
                double locDist = util.dist(loc[i], loc[j]);
                double frameDist = Math.abs(frame[i] - frame[j]);

                // optimize this!!! this is a major slow down rn

                // again there is no == operator here
                // since this needs to be acc im assuming thats intentional
                // if not this can be optimized by removing frameDist > N
                if (frameDist < N) incrementBin(binsBlink, locDist);
                else if (N * 5 > frameDist && frameDist > N) incrementBin(binsNoBlink, locDist);

                // again matlab is 1 based so convert to 0 based here
                if (frameDist > 0 && frameDist <= N) incrementBin(binsFittingBlink[(int) frameDist - 1], locDist);

                // for creation of distanceControl2 in DDC_MCMC.m
                if (frameDist != 0 && frameDist > N && frameDist < maxFrameValue + 1) {
                    // this is like slightly faster (20ms across 8m points) than an if
                    // rather than have an if < 100 ++, this bit shifting operation will auto add 1 if < 128 and 0 if >= 128
                    // but since java is bad theres no unsigned bytes so it'll wrap to -128 but can just check for it afterwards
                    distMatrixValidator[(int) frameDist - N - 1] += 1 - ((distMatrixValidator[(int) frameDist - N - 1] >> 7) & 1);
                }

                if (frameDist == 0 && !_framesWithMultiBuffer[(int) frame[i]] && locDist < resThreshold) {
                    _framesWithMultiBuffer[(int) frame[i]] = true;
                    framesWithMulti.add(i);
                }

                if (locDist <= distanceDensityThreshold && frameDist > N) {
                    density[i]++;
                    density[j]++;
                }
            }

            // because loc can be of varying dimensions (2d, 3d, etc) rather than have unique cases for each dimension,
            // we hash all dimensions into one index that is reversible
            // x + y * xBounds + z * yBounds * xBounds 
            // however all the bounds are the same so it becomes x + y * b + z * b * b
            Integer h = util.hashNDPoint(loc[i], res, dOverallBoundsHalf, dOverallHashOffset);
            if (dOverallCount.containsKey(h)) dOverallCount.put(h, dOverallCount.get(h) + 1);
            else dOverallCount.put(h, 1);
        }
    }

    private void incrementBin(int[] b, double v) {
        if (v > maxLocDist) b[b.length - 1]++;
        else b[(int) (v / res)]++;
    }

    public imagePassAction createSelf(double[] frame, double[][] loc, double ...parameters) {
        primaryPassAction act = new primaryPassAction(
            (double) parameters[0],
            (double) parameters[1],
            (double) parameters[2],
            (int) parameters[3],
            (int) parameters[4]);
        act.frame = frame;
        act.loc = loc;
        return act;
    }
}
