package com.andallfor.imagej.passes.second;

import com.andallfor.imagej.DDC_;
import com.andallfor.imagej.util;
import com.andallfor.imagej.imagePass.imagePassAction;

public class secondaryPassAction extends imagePassAction {
    private int N, res;
    private double maxLocDist;

    public int[] binsTrueDist;
    public int imageNum = 0;
    public double maxLocDistControl = 0;
    

    public secondaryPassAction(double maxLocDist, int res, int N, int imageNum) {
        this.N = N;
        this.maxLocDist = maxLocDist;
        this.res = res;
        this.imageNum = imageNum;
    }

    public void run() {
        binsTrueDist = new int[(int) Math.floor(maxLocDist / res) + 1];

        for (int i = start; i < end; i++) {
            for (int j = i + 1; j < frame.length; j++) {
                // TODO: would pre allocating these be faster? (and other vars defined in the loop)
                double locDist = util.dist(loc[i], loc[j]);
                double frameDist = Math.abs(frame[i] - frame[j]);

                if (frameDist > N && DDC_.firstPass.processedData[imageNum].distMatrixValidator[(int) frameDist - N - 1]) {
                    incrementBin(binsTrueDist, locDist);
                    if (locDist > maxLocDistControl) maxLocDistControl = locDist;
                }
            }
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
