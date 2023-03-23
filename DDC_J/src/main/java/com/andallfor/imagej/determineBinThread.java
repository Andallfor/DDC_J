package com.andallfor.imagej;

import java.util.ArrayList;

public class determineBinThread implements Runnable {
    public int iter;
    public double maxLocDist;
    public ArrayList<Double> blink, noBlink;
    private int N;
    private double[][] loc;
    private double[] fInfo;
    public determineBinThread(int iter, double[][] loc, double[] fInfo, int N) {
        this.iter = iter;
        this.blink = new ArrayList<Double>();
        this.noBlink = new ArrayList<Double>();
        this.loc = loc;
        this.fInfo = fInfo;
        this.N = N;
    }

    public void run() {
        // TODO: eventually use vectorization cause this is sign slower than matlab/python
        for (int i = 0; i < loc.length - 1; i++) {
            for (int j = i + 1; j < loc.length; j++) {
                double locDist = util.dist(loc[i], loc[j]);
                double frameDist = Math.abs(fInfo[i] - fInfo[j]);

                if (locDist > maxLocDist) maxLocDist = locDist;
                
                // no = comparison in src, assuming thats a typo/oversight
                if (frameDist < N) blink.add(locDist);
                else noBlink.add(locDist);
            }
        }
    }
}
