package com.andallfor.imagej.passes.second;

import com.andallfor.imagej.util;
import com.andallfor.imagej.imagePass.imagePassAction;

public class secondaryPassAction extends imagePassAction {
    private int N, res, imageNum;
    private double maxLocDist, maxFrameValue;

    public int[] binsTrueDist;

    public secondaryPassAction(double maxLocDist, double maxFrameValue, int res, int N, int imageNum) {
        this.N = N;
        this.maxLocDist = maxLocDist;
        this.res = res;
        this.imageNum = imageNum;
        this.maxFrameValue = maxFrameValue;
    }

    public void run() {
        binsTrueDist = new int[(int) Math.floor(maxLocDist / res) + 1];
        for (int i = start; i < end; i++) {
            for (int j = i + 1; j < frame.length; j++) {
                double locDist = util.dist(loc[i], loc[j]);
                double frameDist = Math.abs(frame[i] - frame[j]);

                if (frameDist > N && secondaryPass.distMatrixValidator[imageNum][(int) frameDist - N - 1]) incrementBin(binsTrueDist, locDist);
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
            (double) parameters[1],
            (int) parameters[2],
            (int) parameters[3],
            (int) parameters[4]);
        act.frame = frame;
        act.loc = loc;
        return act;
    }
}
