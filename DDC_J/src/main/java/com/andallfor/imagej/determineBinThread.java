package com.andallfor.imagej;

public class determineBinThread implements Runnable {
    public int iter, countBlink, countNoBlink;
    public double maxLocDist;
    public int[] binsBlink, binsNoBlink;
    private int N;
    private double[][] loc;
    private double[] fInfo;
    public static int locQuantization = 20; // because DDC is not super sensitive to bin width (as per user manual),
                                            //   quantize frameInfo values to make it faster to calculate custom bin widths
                                            // each distance will be rounded to their nearest locQuant, and these distances
                                            //   will be used to calc bins
    private final int MAX_BIN = 10_000;
    public determineBinThread(int iter, double[][] loc, double[] fInfo, int N) {
        this.iter = iter;
        this.loc = loc;
        this.fInfo = fInfo;
        this.N = N;
    }

    public void run() {
        // dictionaries are very slow for larger sizes (1k+ keys) so instead use array
        // dont know how big this arr needs to be, in example data max dist is 3k but im setting as 10k just in case
        int[] _binsBlink = new int[MAX_BIN / locQuantization];
        int[] _binsNoBlink = new int[MAX_BIN / locQuantization];
        // TODO: eventually use vectorization cause this is sig slower than matlab/python
        for (int i = 0; i < loc.length - 1; i++) {
            for (int j = i + 1; j < loc.length; j++) {
                double locDist = util.dist(loc[i], loc[j]);
                double frameDist = Math.abs(fInfo[i] - fInfo[j]);

                if (locDist > maxLocDist) maxLocDist = locDist;
                
                // no = comparison in src, assuming thats a typo/oversight
                // int division bc bin size does not need to be perfect
                if (frameDist < N) _binsBlink[(int) locDist / locQuantization]++;
                else _binsNoBlink[(int) locDist / locQuantization]++;
            }
        }

        binsBlink = compressArray(_binsBlink);
        binsNoBlink = compressArray(_binsNoBlink);

        for (int i : binsBlink) countBlink += i;
        for (int i : binsNoBlink) countNoBlink += i;
    }

    private int[] compressArray(int[] src) {
        for (int i = src.length - 1; i >= 0; i--) {
            if (src[i] != 0) {
                int[] arr = new int[i + 1];
                System.arraycopy(src, 0, arr, 0, i + 1);
                return arr;
            }
        }

        return null;
    }
}
