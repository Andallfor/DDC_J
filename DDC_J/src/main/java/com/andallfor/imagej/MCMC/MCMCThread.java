package com.andallfor.imagej.MCMC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import com.andallfor.imagej.DDC_;
import com.andallfor.imagej.util;
import com.jmatio.io.MatFileWriter;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLCell;
import com.jmatio.types.MLDouble;

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
        ArrayList<HashSet<Integer>> outputs = new ArrayList<HashSet<Integer>>();
        int runCounts = 20;
        for (int ii = 0; ii < runCounts; ii++) {
            int step = 0;
            double maxScore = prepare();
            double tempMaxScore = maxScore;
            double currentScore = maxScore;
            this.t = new linkTrajectories(imageNum, frame, loc);

            System.out.println(maxScore);

            HashSet<Integer> bestTruthIndices = deepCopyTruthIndices(truthIndices);

            HashSet<Integer> truthIndicesBackup = deepCopyTruthIndices(truthIndices);
            boolean[] prevLocBlink = deepCopyBoolArr(t.locBlink);
            boolean[] prevLocBlinkBackup = deepCopyBoolArr(t.locBlink);
            t.backup();

            if (ii == 0) t.save("C:/Users/leozw/Desktop/outputTrajectories.mat");

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

                // the scoring function is not perfect, as trying to maximize it too much actually causes a loss in accuracy. so introduce a new parameter
                // the goal of this is now to maximize the score function whilst also making as little changes as possible
                // this can be seen when running this program with and without this option- when only trying to maximize the score, the end score will usually
                // be 100-200 better than the initial score, however the differences between the truth and generated points will be massive (usually 1.5-2x worse)
                // whereas a "good" generation would result in only a ~30 increase in score but a ~0.5-0.75x smaller difference between truth and generated
                double sizePenalty = -t.c1.unique() - t.c2.unique();

                System.out.println("Step: " + step + "  |  Score: " + (int) currentScore + "  |  Count: " + truthIndices.size());

                if (currentScore + sizePenalty >= tempMaxScore || Math.log(Math.random()) <= (currentScore - tempMaxScore)) {
                    // backup data so we can return to this point
                    truthIndicesBackup = deepCopyTruthIndices(truthIndices);
                    prevLocBlinkBackup = deepCopyBoolArr(prevLocBlink);
                    tempMaxScore = currentScore;
                    
                    // do not backup t
                    // this is to prevent the program from going down a single path- which will cause score to be better but the actual accuracy to be worse
                    // instead have the program look at variations from the initial starting point so that any score increases will not vary too much from
                    // our starting point

                    if (currentScore + sizePenalty > maxScore) {
                        maxScore = currentScore + sizePenalty;
                        tempMaxScore = currentScore;
                        step = 10;

                        bestTruthIndices = deepCopyTruthIndices(truthIndices);
                    }
                } else {
                    // only allow restarts at the beginning
                    if (step < 20) t.restore();
                    truthIndices = deepCopyTruthIndices(truthIndicesBackup);
                    prevLocBlink = deepCopyBoolArr(prevLocBlinkBackup);
                    currentScore = tempMaxScore;
                }

                step++;
            }

            System.out.println(getTrueScore(bestTruthIndices));

            System.out.println(ii);
            outputs.add(bestTruthIndices);
        }

        double[][] outFrames = {frame};
        double[][] outLocs = loc;
        double[][] outInd = new double[runCounts][frame.length];
        for (int i = 0; i < runCounts; i++) {
            HashSet<Integer> output = outputs.get(i);
            ArrayList<Integer> arr = new ArrayList<Integer>(output);

            for (int j = 0; j < output.size(); j++) outInd[i][j] = arr.get(j);
            for (int j = output.size(); j < frame.length; j++) outInd[i][j] = -1;
        }

        MatFileWriter mfw = new MatFileWriter();
        Collection<MLArray> data = new ArrayList<MLArray>();

        data.add(new MLDouble("out_final_loc", outLocs));
        data.add(new MLDouble("out_final_frame", outFrames));
        data.add(new MLDouble("out_final_inds", outInd));

        try {
            mfw.write("C:/Users/leozw/Desktop/finalTrajectories.mat", data);
        } catch (IOException e) {
            System.out.println(e);
        }
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
