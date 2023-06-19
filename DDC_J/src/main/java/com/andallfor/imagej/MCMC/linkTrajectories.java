package com.andallfor.imagej.MCMC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

import org.orangepalantir.leastsquares.Fitter;
import org.orangepalantir.leastsquares.fitters.NonLinearSolver;

import com.andallfor.imagej.DDC_;
import com.andallfor.imagej.linearSolver;
import com.andallfor.imagej.util;
import com.andallfor.imagej.passes.first.primaryPassCollector;
import com.andallfor.imagej.passes.second.secondaryPassCollector;
import com.jmatio.io.MatFileWriter;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLDouble;

public class linkTrajectories {
    private HashMap<Double, ArrayList<Integer>> frameLookup;
    private double[][] loc;
    private double[] frame;
    private int imageNum;

    private HashMap<Integer, int[]> trajectoryIndexMap;
    private int[] trajectories;

    public double[][] probDist;
    public boolean[] locBlink; // TODO: why is this inconsistent?? (blink then nonblink)
    public int nonBlink;

    public linkTrajectories(int imageNum, double[] frame, double[][] loc, HashMap<Double, ArrayList<Integer>> frameLookup) {
        this.frame = frame;
        this.loc = loc;
        this.frameLookup = frameLookup;
        this.imageNum = imageNum;

        nonBlink = loc.length;
        trajectories = new int[frame.length]; // value of 0 means no trajectory. otherwise contains the value of a trajectoryHash
        trajectoryIndexMap = new HashMap<Integer, int[]>(); // each key is a trajectoryHash. each value points to an index inside trajectories

        findProbDist();
    }

    private void findProbDist() {
        //---------------------------------------------------//
        // Determine_Deviation_in_Probability8.m             //
        //---------------------------------------------------//
        primaryPassCollector p1 = DDC_.firstPass.processedData[imageNum];
        secondaryPassCollector p2 = DDC_.secondPass.processedData[imageNum];

        link();

        double[] localDScaleStore = new double[DDC_.blinkDist.d_scale_store.length];
        probDist = new double[DDC_.N][p2.trueDist.length];
        Fitter solver = new NonLinearSolver(linearSolver.func);
        double[] initialPara = new double[] {1};

        double nb = (frame.length - nonBlink) / (double) nonBlink;
        double _nb = nb + nb + nb * nb;
        double m = 1 - (_nb / (1 + _nb));

        double[][] t = new double[p2.trueDistBig.length][2];
        for (int j = 0; j < p2.trueDistBig.length; j++) {
            t[j] = new double[] {p2.trueDistBig[j], p2.distBlinkBig[j]};
        }

        double minProb = 1000;

        for (int i = 0; i < DDC_.N; i++) {
            // remove zero prob from _d_blink
            double[] _d_blink = util.compressBins(p1.binsFittingBlink[i]);
            double[] d_blink = new double[p2.trueDistBig.length];
            System.arraycopy(_d_blink, 0, d_blink, 0, d_blink.length);

            double d = m * DDC_.blinkDist.d_scale_store[i];
            double[] y = util.arrSubOut(d_blink, util.arrMultiOut(p2.trueDistBig, d));

            for (int j = 0; j < y.length; j++) {if (y[j] < 0) y[j] = 0;}
            double s = util.sumArr(y, 0, y.length);
            for (int j = 0; j < y.length; j++) y[j] /= s;

            solver.setData(t, y);
            solver.setParameters(initialPara);
            solver.fitData();
            double x = solver.getParameters()[0];
            if (x > 1) x = 1;
            else if (x < 0) x = 0.0000001;
            if (i == DDC_.N - 1) x = 1;
            localDScaleStore[i] = x;

            double[] a = util.arrMultiOut(p2.distBlink, 1.0 - x);
            double[] b = util.arrMultiOut(p2.trueDist, x);

            probDist[i] = util.arrSumOut(a, b);

            for (int j = 0; j < probDist[i].length; j++) {
                if (probDist[i][j] <= 0) continue;
                if (probDist[i][j] < minProb) minProb = probDist[i][j];
            }
        }

        minProb = minProb / 10.0;

        for (int i = 0; i < probDist.length; i++) {
            for (int j = 0; j < probDist[i].length; j++) {
                probDist[i][j] = Math.log(probDist[i][j] + minProb);
            }
        }
    }

    public void link() { // the more comments i write the less i understand what the fuck i am doing
        long s1 = System.currentTimeMillis();

        double[] distMatrix = new double[4];
        // _t1 and _t2 represent trajectories (specifically the indices) with 1 element (aka have not yet been assigned to a trajectory)
        int[] _t1 = new int[1], _t2 = new int[1];
        int[] t1 = null, t2 = null;
        int ddtClamp = DDC_.blinkDist.m_mat[0].length;

        boolean[] locLinked = new boolean[frame.length];
        locBlink = new boolean[frame.length];

        double[][] framerCache = new double[frame.length][2]; // [trajectory hash][min, max]
        int trajectoryHash = 1; // offset by 1, 0 is used to signify that there is no trajectory

        for (int x = 1; x <= DDC_.N; x++) {
            for (int i = 0; i < frame.length; i++) {
                for (int j = i + 1; j < frame.length; j++) {
                    //double locDist = util.dist(loc[i], loc[j]);
                    double frameDist = Math.abs(frame[i] - frame[j]);

                    if (frameDist != x) continue;

                    // this is similar to the second check (Math.abs(distMatrix[0]...)), but rather than check each element (or rather the mins and maxes) we check the start and end
                    if (frameDist <= DDC_.N && frameDist != 0) {
                        // in src, arr is sorted however it is not here so simulate it
                        int first = i, last = j;
                        if (frame[i] > frame[j]) {
                            first = j;
                            last = i;
                        }

                        // start cannot have started another trajectory and last cannot be part of another trajectory
                        if (!locBlink[last] && !locLinked[first]/*&& !locLinked[last]*/) {
                            // trajectories can be both one element (not yet claimed) or an array (already a trajectory) to account for both we put the one element into an array
                            // in cases where trajectory already exists, we do not need to account for the current value because by definition they will have already been included in the already existing trajectory
                            t1 = writeValues(first, framerCache, distMatrix, 0, trajectories, _t1, trajectoryIndexMap);
                            t2 = writeValues(last, framerCache, distMatrix, 2, trajectories, _t2, trajectoryIndexMap);

                            int min = (int) (distMatrix[0] < distMatrix[2] ? distMatrix[0] : distMatrix[2]);
                            int max = (int) (distMatrix[1] > distMatrix[3] ? distMatrix[1] : distMatrix[3]);

                            // check and make sure max dist is < DDC_.N
                            if (min - max > DDC_.N || max - min > DDC_.N) continue;

                            // finished all the simple checks now go through and calc the new distances between first and last trajectories
                            // the order of which framer and ddt are calculated do not matter, they just need to be calculated in the same way so that they "match up" with each other for when we apply their
                            //      indices to deviation_in_prob
                            double avg = 0;
                            double totalLength = t1.length * t2.length;

                            for (int ii = 0; ii < t1.length; ii++) {
                                for (int jj = 0; jj < t2.length; jj++) {
                                    // having to recalculate values isnt great but i cant think of a faster method :/
                                    int r = (int) Math.abs(frame[t1[ii]] - frame[t2[jj]]);
                                    int c = (int) (util.dist(loc[t1[ii]], loc[t2[jj]]) / DDC_.res) + 1;
                                    if (c > ddtClamp) c = ddtClamp;

                                    avg += DDC_.blinkDist.m_mat[r - 1][c - 1];
                                }
                            }

                            if (avg / totalLength <= 0.5) continue;

                            // is part of trajectory
                            locBlink[last] = true;
                            locLinked[first] = true;

                            // for simplicity just combine trajectories into start. if performance is an issue set trajectories to the shortest trajectory
                            int[] combinedIndexes = new int[t1.length + t2.length];
                            System.arraycopy(t1, 0, combinedIndexes, 0, t1.length);
                            System.arraycopy(t2, 0, combinedIndexes, t1.length, t2.length);
                            if (trajectories[first] == 0) {
                                trajectoryIndexMap.put(trajectoryHash, combinedIndexes);
                                trajectories[first] = trajectoryHash;
                                trajectoryHash++;
                            } else trajectoryIndexMap.put(trajectories[first], combinedIndexes);

                            // set all last trajectory references to start
                            for (int ii = 0; ii < t2.length; ii++) trajectories[t2[ii]] = trajectories[first];

                            // update framerCache with new mins and maxes
                            framerCache[trajectories[first] - 1][0] = min;
                            framerCache[trajectories[first] - 1][1] = max;

                            nonBlink--;
                        }
                    }
                }
            }
        }

        System.out.println("Trajectory Linking: " + (System.currentTimeMillis() - s1));
    }

    private int[] writeValues(int frameIndex, double[][] framerCache, double[] distMatrix, int distMatrixOffset, int[] trajectories, int[] _t, HashMap<Integer, int[]> indexMap) {
        if (trajectories[frameIndex] == 0) {
            distMatrix[distMatrixOffset + 0] = frame[frameIndex];
            distMatrix[distMatrixOffset + 1] = frame[frameIndex];
            _t[0] = frameIndex;
            return _t;
        } else {
            distMatrix[distMatrixOffset + 0] = framerCache[trajectories[frameIndex] - 1][0]; // -1 bc trajectories = 0 represents no trajectories, so everything is offset by one but we dont want to waste space
            distMatrix[distMatrixOffset + 1] = framerCache[trajectories[frameIndex] - 1][1];
            return indexMap.get(trajectories[frameIndex]);
        }
    }

    public void randomize(Integer f) {
        if (!frameLookup.containsKey(f)) return;
        Collections.shuffle(frameLookup.get(f));
    }

    public void save(String path) {
        double[][] _non_blink_loc = new double[trajectories.length][loc[0].length];
        double[] _non_blink_frame = new double[trajectories.length];
        int c = 0;
        for (int i = 0; i < trajectories.length; i++) {
            if (!locBlink[i]) {
                _non_blink_loc[c] = loc[i];
                _non_blink_frame[c] = frame[i];
                c++;
            }
        }

        double[][] non_blink_loc = new double[c][loc[0].length];
        double[][] non_blink_frame = new double[1][c];
        System.arraycopy(_non_blink_loc, 0, non_blink_loc, 0, c);
        System.arraycopy(_non_blink_frame, 0, non_blink_frame[0], 0, c);

        MatFileWriter mfw = new MatFileWriter();
        Collection<MLArray> data = new ArrayList<MLArray>();
        double[][] vals = new double[1][trajectories.length];
        for (int i = 0; i < trajectories.length; i++) vals[0][i] = trajectories[i];

        data.add(new MLDouble("outTrajectories", vals));
        data.add(new MLDouble("out_non_blink_loc", non_blink_loc));
        data.add(new MLDouble("out_non_blink_frame", non_blink_frame));
        try {
            mfw.write(path, data);
        } catch (IOException e) {
            System.out.println(e);
        }
    }
}
