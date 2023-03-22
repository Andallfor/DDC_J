package com.andallfor.imagej;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLCell;
import com.jmatio.types.MLDouble;

import ij.IJ;
import ij.ImageJ;
import ij.gui.Plot;
import ij.plugin.PlugIn;

public class determine_n implements PlugIn {
    static final int NFTP = 1000, GAP = 10, binMax = 5000, binSize = 250;
    static final String testMat = "C:/Users/leozw/Desktop/code/matlab/ddc/Main_DDC_Folder/User_Guide_Files/Simulation_2_dark_state_sparse_Clusters_10per_3_per_ROI.mat";

    public void run(String arg) {
        assert (binMax % binSize) == 0;

        MatFileReader mfr = null;
        try {mfr = new MatFileReader(testMat);}
        catch (IOException e) {return;}

        MLCell FRAME_INFO = (MLCell) mfr.getMLArray("Frame_Information");
        MLCell LOC_FINAL = (MLCell) mfr.getMLArray("LocalizationsFinal");

        int[] expectedSize = FRAME_INFO.getDimensions();
        assert Arrays.equals(LOC_FINAL.getDimensions(), expectedSize);

        final int nIter = (int) Math.ceil((double) NFTP / GAP);
        final int nBins = (binMax / binSize) + 1 + 1; // +1 to include end, +1 to end with inf
        int[] bins = new int[nBins];
        for (int i = 0; i < nBins - 1; i++) bins[i] = i * 250;
        bins[nBins - 1] = Integer.MAX_VALUE;

        int[][] iterationBins = new int[nIter][nBins - 1];

        // need to calculate for every frameInfo instance (frameInfo and locFinal have same length)
        for (int i = 0; i < expectedSize[1]; i++) {
            double[] data = ((MLDouble) FRAME_INFO.get(i)).getArray()[0];
            double[][] cache = ((MLDouble) LOC_FINAL.get(0, i)).getArray();

            // calculate pdist for every index
            // pdist works by getting the distance from the current element to every other element in front of it
            // however since frameInfo is always 1D, its just a subtraction and so very fast
            // we calculate the pdist for frameInfo and only dist for locFinal when needed
            //      as pdist for locFinal is very expensive
            for (int pDistIndex = 0; pDistIndex < data.length - 1; pDistIndex++) {
                double initial = data[pDistIndex];
                for (int k = pDistIndex + 1; k < data.length; k++) {
                    double frameDist = Math.abs(initial - data[k]);

                    // in the src, we look for distances that are equal to iis
                    // where iis is defined as 1 + GAP * n (n is current iter) (+1 as matlab arr starts at 1)
                    // rather than loop n times, just get every possible value here and then reverse calc n
                    if ((frameDist - 1) % GAP == 0) {
                        double locDist = dist(cache[pDistIndex], cache[k]);

                        // determine iteration as tech each frameDist needs to only be sorted with similar frameDist
                        int n = (int) (frameDist - 1) / GAP;
                        if (n >= nIter) continue;

                        // determine which bin it goes into
                        // bins are [start, end)
                        int bin = 0;
                        if (locDist >= binMax) bin = nBins - 1;
                        else if (locDist >= binSize) bin = (int) (locDist / binSize);

                        iterationBins[n][bin]++;
                    }
                }
            }
        }

        // calculate CDF
        double[][] cum_sum_store = new double[nIter][nBins - 1];
        for (int i = 0; i < nIter; i++) {
            // get sum
            double sum = 0;
            for (int ii : iterationBins[i]) sum += ii;

            double last = 0;
            for (int j = 0; j < nBins - 1; j++) {
                cum_sum_store[i][j] = last + iterationBins[i][j] / sum;
                last = cum_sum_store[i][j];
            }
        }

        // calculate Z
        double[] Z = new double[nIter];
        double[] frame_store = new double[nIter];
        for (int i = 0; i < nIter; i++) {
            double sum = 0;
            for (int j = 0; j < nBins - 1; j++) sum += Math.abs(cum_sum_store[0][j] - cum_sum_store[i][j]);
            Z[i] = sum;
            frame_store[i] = i * GAP + 1;
        }

        Plot p = new Plot("Determine N", "Frame", "Z");
        p.addPoints(frame_store, Z, Plot.LINE);
        p.show();
    }

    private double dist(double[] a, double[] b) {
        double interior = 0;
        for (int i = 0; i < a.length; i++) interior += (a[i] - b[i]) * (a[i] - b[i]);
        return Math.sqrt(interior);
    }

    public static void main(String[] args) throws Exception {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		// see: https://stackoverflow.com/a/7060464/1207769
		Class<?> clazz = determine_n.class;
		java.net.URL url = clazz.getProtectionDomain().getCodeSource().getLocation();
		java.io.File file = new java.io.File(url.toURI());
		System.setProperty("plugins.dir", file.getAbsolutePath());

		// start ImageJ
		new ImageJ();

		// open the Clown sample
		//ImagePlus image = IJ.openImage("http://imagej.net/images/clown.jpg");
		//image.show();

		// run the plugin
		IJ.runPlugIn(clazz.getName(), "");
	}
}
