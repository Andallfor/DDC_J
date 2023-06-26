package com.andallfor.imagej.preprocessing;

import java.io.IOException;
import java.util.Arrays;

import com.andallfor.imagej.util;
import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLCell;
import com.jmatio.types.MLDouble;

import ij.IJ;
import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.plugin.PlugIn;

public class determine_n implements PlugIn {
    static int NFTP = 1000, GAP = 10, binMax = 5000, binSize = 250;

    public void run(String arg) {
        GenericDialog gd = new GenericDialog("Determine N Parameters");
        gd.addMessage("Parameters to determine the size of N.");
        gd.addHelp("https://github.com/Andallfor/DDC_J/blob/main/papers/DDC%20User%20Guide%20(GO%20HERE).pdf");
        gd.addFileField("File to Parse", "");
        gd.addNumericField("NFTP", NFTP, 0);
        gd.addNumericField("GAP", GAP, 0);
        gd.addNumericField("Bin Max", binMax, 0);
        gd.addNumericField("Bin Size", binSize, 0);

        gd.showDialog();

        String filePath = "";

        if (gd.wasOKed()) {
            filePath = gd.getNextString();
            NFTP = (int) gd.getNextNumber();
            GAP = (int) gd.getNextNumber();
            binMax = (int) gd.getNextNumber();
            binSize = (int) gd.getNextNumber();
        } else if (gd.wasCanceled()) return;

        // validate
        if (NFTP % GAP != 0) {IJ.showMessage("NFTP must be divisible by GAP."); return;}
        if (binMax % binSize != 0) {IJ.showMessage("Bin max must be divisible by bin size"); return;}
        if (binMax < binSize) {IJ.showMessage("Bin max must be greater than bin size"); return;}

        MatFileReader mfr = null;
        try {mfr = new MatFileReader(filePath);}
        catch (IOException e) {
            System.out.println(e);
            IJ.showMessage("Invalid File Path");
            return;
        }

        MLCell FRAME_INFO = (MLCell) mfr.getMLArray("Frame_Information");
        MLCell LOC_FINAL = (MLCell) mfr.getMLArray("LocalizationsFinal");

        int[] expectedSize = FRAME_INFO.getDimensions();
        assert Arrays.equals(LOC_FINAL.getDimensions(), expectedSize);

        final int nIter = (int) Math.ceil((double) NFTP / GAP);

        int nBins = (int) (binMax / binSize) + 2;
        int[][] iterationBins = new int[nIter][nBins - 1];

        IJ.showProgress(0);

        // can multithread this with a few changes (need a way to track index)
        // will prob have to write own 
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
                        double locDist = util.dist(cache[pDistIndex], cache[k]);

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

            IJ.showProgress((double) i / expectedSize[1]);
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

        IJ.showProgress(1);

        Plot p = new Plot("Determine N", "Frame", "Z");
        p.addPoints(frame_store, Z, Plot.LINE);
        p.show();
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
