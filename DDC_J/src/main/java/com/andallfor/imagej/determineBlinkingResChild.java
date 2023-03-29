package com.andallfor.imagej;

public class determineBlinkingResChild implements Runnable {
    private double[] frame;
    private double[][] loc;
    private double max;
    private int N, res, start, end;

    // store locally in order to prevent race conditions
    public int[] binsBlink, binsNoBlink;
    public int[][] binsFittingBlink;

    public determineBlinkingResChild(double[] frame, double[][] loc, int start, int end, double max, int res, int N) {
        this.frame = frame;
        this.loc = loc;
        this.start = start;
        this.end = end;
        this.max = max;
        this.res = res;
        this.N = N;
    }

    public void run() {
        binsBlink = new int[(int) Math.floor(max / res) + 1];
        binsNoBlink = new int[(int) Math.floor(max / res) + 1];
        binsFittingBlink = new int[N][(int) Math.floor(max / res) + 1];

        for (int i = start; i < end; i++) {
            for (int j = i + 1; j < frame.length; j++) {
                double locDist = util.dist(loc[i], loc[j]);
                double frameDist = Math.abs(frame[i] - frame[j]);

                // again there is no == operator here
                // since this needs to be acc im assuming thats intentional
                // if not this can be optimized by removing frameDist > N
                if (frameDist < N) incrementBin(binsBlink, locDist);    
                else if (N * 5 > frameDist && frameDist > N) incrementBin(binsNoBlink, locDist);

                // again matlab is 1 based so convert to 0 based here
                if (frameDist > 0 && frameDist <= N) incrementBin(binsFittingBlink[(int) frameDist - 1], locDist);
            }
        }
    }

    private void incrementBin(int[] b, double v) {
        if (v > max) b[b.length - 1]++;
        else b[(int) (v / res)]++;
    }
}
