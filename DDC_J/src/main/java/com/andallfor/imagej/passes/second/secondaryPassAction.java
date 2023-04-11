package com.andallfor.imagej.passes.second;

import java.util.HashMap;

import com.andallfor.imagej.util;
import com.andallfor.imagej.imagePass.imagePassAction;

public class secondaryPassAction extends imagePassAction {
    private int N, res;
    private double maxLocDist;

    public int[] binsTrueDist;
    public int numBlinks = 0, imageNum = 0;
    public double maxLocDistControl = 0;

    public secondaryPassAction(double maxLocDist, int res, int N, int imageNum) {
        this.N = N;
        this.maxLocDist = maxLocDist;
        this.res = res;
        this.imageNum = imageNum;
    }

    public void run() { // the more comments i write the less i understand what the fuck i am doing
        binsTrueDist = new int[(int) Math.floor(maxLocDist / res) + 1];
        int[] trajectories = new int[frame.length]; // value of 0 means no trajectory. otherwise contains the value of a trajectoryHash
        boolean[] locBlink = new boolean[frame.length];
        boolean[] locLinked = new boolean[frame.length];
        double[][] framerCache = new double[frame.length][2]; // [trajectory hash][min, max]
        int trajectoryHash = 1; // offset by -1, 0 is used to signify that there is no trajectory
        HashMap<Integer, int[]> trajectoryIndexMap = new HashMap<Integer, int[]>(); // each key is a trajectoryHash. each value points to an index inside trajectories

        // cache
        double[] distMatrix = new double[4];
        // _t1 and _t2 represent trajectories (specifically the indices) with 1 element (aka have not yet been assigned to a trajectory)
        int[] _t1 = new int[1], _t2 = new int[1];
        int[] t1 = null, t2 = null;
        int ddtClamp = secondaryPass.deviation_in_prob[0].length;

        for (int i = start; i < end; i++) {
            for (int j = i + 1; j < frame.length; j++) {
                // TODO: would pre allocating these be faster? (and other vars defined in the loop)
                double locDist = util.dist(loc[i], loc[j]);
                double frameDist = Math.abs(frame[i] - frame[j]);

                if (frameDist > N && secondaryPass.distMatrixValidator[imageNum][(int) frameDist - N - 1]) {
                    incrementBin(binsTrueDist, locDist);
                    if (locDist > maxLocDistControl) maxLocDistControl = locDist;
                }

                // this is similar to the second check (Math.abs(distMatrix[0]...)), but rather than check each element (or rather the mins and maxes) we check the start and end
                if (frameDist <= N && frameDist != 0) {
                    // in src, arr is sorted however it is not here so simulate it
                    int first = i, last = j;
                    if (frame[i] > frame[j]) {
                        first = j;
                        last = i;
                    }

                    // start cannot have started another trajectory and last cannot be part of another trajectory
                    if (!locBlink[first] && !locLinked[last]) {
                        // trajectories can be both one element (not yet claimed) or an array (already a trajectory) to account for both we put the one element into an array
                        // in cases where trajectory already exists, we do not need to account for the current value because by definition they will have already been included in the already existing trajectory
                        t1 = writeValues(first, framerCache, distMatrix, 0, trajectories, _t1, trajectoryIndexMap);
                        t2 = writeValues(last, framerCache, distMatrix, 2, trajectories, _t2, trajectoryIndexMap);

                        // check and make sure max dist is < N
                        if (Math.abs(distMatrix[0] - distMatrix[2]) > N || Math.abs(distMatrix[1] - distMatrix[3]) > N) continue;

                        // finished all the simple checks now go through and calc the new distances between first and last trajectories
                        // the order of which framer and ddt are calculated do not matter, they just need to be calculated in the same way so that they "match up" with each other for when we apply their
                        //      indices to deviation_in_prob
                        double avg = 0;
                        double totalLength = t1.length * t2.length;

                        for (int ii = 0; ii < t1.length; ii++) {
                            for (int jj = 0; jj < t2.length; jj++) {
                                // having to recalculate values isnt great but i cant think of a faster method :/
                                int r = (int) Math.abs(frame[t1[ii]] - frame[t2[jj]]);
                                int c = (int) (util.dist(loc[t1[ii]], loc[t2[jj]]) / res) + 1;
                                if (c > ddtClamp) c = ddtClamp;

                                avg += secondaryPass.deviation_in_prob[r - 1][c - 1];
                            }
                        }

                        if (avg / totalLength <= 0.5) continue;

                        // is part of trajectory
                        locBlink[first] = true;
                        locLinked[last] = true;

                        // for simplicity just combine trajectories into start. if performance is an issue set trajectories to the shortest trajectory
                        int[] combinedIndexes = new int[t1.length + t2.length];
                        System.arraycopy(t1, 0, combinedIndexes, 0, t1.length);
                        System.arraycopy(t2, 0, combinedIndexes, t1.length, t2.length);
                        if (trajectories[first] == 0) {
                            trajectoryIndexMap.put(trajectoryHash - 1, combinedIndexes);
                            trajectories[first] = trajectoryHash;
                            trajectoryHash++;
                        } else trajectoryIndexMap.put(trajectories[first], combinedIndexes);

                        // set all last trajectory references to start
                        for (int ii = 0; ii < t2.length; ii++) trajectories[ii] = trajectories[first];

                        // update framerCache with new mins and maxes
                        if (distMatrix[0] < distMatrix[2]) framerCache[trajectories[first] - 1][0] = distMatrix[0];
                        else framerCache[trajectories[first] - 1][0] = distMatrix[2];

                        if (distMatrix[1] > distMatrix[3]) framerCache[trajectories[first] - 1][1] = distMatrix[1];
                        else framerCache[trajectories[first] - 1][1] = distMatrix[3];
                    }
                }
            }
        }
        
        // collect locBlink here since currently secondaryPass is guaranteed to be a single thread
        for (int i = 0; i < loc.length; i++) {
            if (!locBlink[i]) numBlinks++;
        }
    }

    // just pass all the values lol (something about global variables dont get gc yada yada i just dont feel like it tbh)
    private int[] writeValues(int frameIndex, double[][] framerCache, double[] distMatrix, int distMatrixOffset, int[] trajectories, int[] _t, HashMap<Integer, int[]> indexMap) {
        if (trajectories[frameIndex] == 0) {
            distMatrix[distMatrixOffset + 0] = frame[frameIndex];
            distMatrix[distMatrixOffset + 1] = frame[frameIndex];
            _t[0] = frameIndex;
            return _t;
        } else {
            distMatrix[distMatrixOffset + 0] = framerCache[trajectories[frameIndex] - 1][0]; // -1 bc trajectories = 0 represents no trajectories, so everything is offset by one but we dont want to waste space
            distMatrix[distMatrixOffset + 1] = framerCache[trajectories[frameIndex] - 1][1];
            return indexMap.get(trajectories[frameIndex] - 1);
        }
    }

    private void incrementBin(int[] b, double v) {
        if (v > maxLocDist) b[b.length - 1]++;
        else b[(int) (v / res)]++;
    }

    public imagePassAction createSelf(double[] frame, double[][] loc, double ...parameters) {
        secondaryPassAction act = new secondaryPassAction(
            (double) parameters[0],
            (int) parameters[1],
            (int) parameters[2],
            (int) parameters[3]);
        act.frame = frame;
        act.loc = loc;
        return act;
    }
}
