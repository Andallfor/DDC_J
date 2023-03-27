package com.andallfor.imagej;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ij.IJ;
import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLCell;
import com.jmatio.types.MLDouble;

/*
 * It may be worth combining determine_n and determine_bins together as they share some data together
 *   and doing so would allow us to not have to calc things twice. however given how fast both scripts are,
 *   i dont think its a good use of time
 */

public class determine_bins implements PlugIn {
    public void run(String arg) {
        GenericDialog gd = new GenericDialog("Determine Bin Parameters");
        gd.addMessage("Parameters to determine the size of each bin.");
        gd.addHelp("https://github.com/Andallfor/DDC_J/blob/main/papers/DDC%20User%20Guide%20(GO%20HERE).pdf");
        gd.addFileField("File to Parse", "");
        gd.addNumericField("N (From Determine N Script)", 200, 0);
        gd.addNumericField("Localization Distance Quantization", 20, 0); // see determineBinThread.java

        gd.showDialog();

        String filePath = "";
        int N = 0;

        if (gd.wasOKed()) {
            filePath = gd.getNextString();
            N = (int) gd.getNextNumber();
            determineBinThread.locQuantization = (int) gd.getNextNumber();
        } else if (gd.wasCanceled()) return;

        // validate
        if (N <= 0) {IJ.showMessage("N must be greater than 0."); return;}
        if (determineBinThread.locQuantization <= 0) {IJ.showMessage("Quant must be greater than 0"); return;}

        MatFileReader mfr = null;
        try {mfr = new MatFileReader(filePath);}
        catch (IOException e) {
            IJ.showMessage("Invalid File Path");
            return;
        }

        MLCell LOC_FINAL = (MLCell) mfr.getMLArray("LocalizationsFinal");
        MLCell FRAME_INFO = (MLCell) mfr.getMLArray("Frame_Information");

        // get max dist in locFinal
        int[] expectedSize = LOC_FINAL.getDimensions();
        double maxLocDist = 0;
        ArrayList<determineBinThread> threads = new ArrayList<determineBinThread>();

        ExecutorService es = Executors.newCachedThreadPool();
        for (int iter = 0; iter < expectedSize[1]; iter++) {
            double[][] loc = ((MLDouble) LOC_FINAL.get(iter)).getArray();
            double[] fInfo = ((MLDouble) FRAME_INFO.get(iter)).getArray()[0];

            // TODO: since pdist is repeated freq, maybe worth to move it to its own class?
            //      at very least should def find way to optimize
            // calc pdist
            determineBinThread thread = new determineBinThread(iter, loc, fInfo, N);
            threads.add(thread);
            es.execute(thread);
        }

        es.shutdown();
        try {es.awaitTermination(10_000, TimeUnit.MINUTES);}
        catch (InterruptedException e) {
            IJ.showMessage("Unable to run initial threads. Maybe distances between localizations is too great? (>10,000)");
            IJ.showMessage(e.toString());
            return;
        }

        // get max dist out of all threads
        for (determineBinThread thread : threads) if (thread.maxLocDist > maxLocDist) maxLocDist = thread.maxLocDist;

        // utilize binary search-esq alg to determine correct bin size
        // 0 -> 150 step size of 10
        // this works because the change is monotonic [111111111110000000000000] always
        // there is very likely a way to determine bin res without manually checking but A. im dumb and B. not worth the time
        //      as this script only runs once and is alr fast enough
        int left = 0, right = 15, step = 10;
        int res = 0;
        while (left != right) {
            int m = (int) Math.ceil((left + right) / 2.0);
            res = m * step;

            int binCount = (int) (maxLocDist / res) + 1;

            double[] d_count_blink = new double[binCount];
            double[] d_count_no_blink = new double[binCount];
            for (int i = 0; i < expectedSize[1]; i++) {
                determineBinThread thread = threads.get(i);

                double[] binsBlink = sortProbBins(thread.binsBlink, res, determineBinThread.locQuantization, binCount, thread.countBlink);
                double[] binsNoBlink = sortProbBins(thread.binsNoBlink, res, determineBinThread.locQuantization, binCount, thread.countNoBlink);

                for (int j = 0; j < binCount; j++) {
                    d_count_blink[j] += binsBlink[j] / (double) expectedSize[1];
                    d_count_no_blink[j] += binsNoBlink[j] / (double) expectedSize[1];
                }
            }

            // math!
            int lsne = (int) Math.ceil(1000 / (double) res) - 1; // -1 bc matlab arr starts at 1
            double d_scale = util.sumArr(d_count_blink, lsne, d_count_blink.length) / util.sumArr(d_count_no_blink, lsne, d_count_no_blink.length);
            double[] d_count_3 = new double[d_count_blink.length];
            for (int i = 0; i < d_count_blink.length; i++) d_count_3[i] = d_count_blink[i] - d_count_no_blink[i] * d_scale;
            double d_count_3_sum = util.sumArr(d_count_3, 0, d_count_3.length);
            for (int i = 0; i < d_count_3.length; i++) d_count_3[i] /= d_count_3_sum;

            for (int i = 3; i < d_count_3.length - 1; i++) {
                if (!(d_count_3[i] > 0 && d_count_3[i + 1] < d_count_3[i])) {
                    d_count_3[i] = 0;
                    break;
                }
            }

            // shush ill write my own vect wrapper eventually (or steal one)
            for (int i = 0; i < d_count_3.length; i++) if (d_count_3[i] < 0) d_count_3[i] = 0;
            double s1 = util.sumArr(d_count_3, 0, d_count_3.length);
            for (int i = 0; i < d_count_3.length; i++) d_count_3[i] /= s1;
            double s2 = util.sumArr(d_count_3, 0, d_count_3.length);
            for (int i = 0; i < d_count_3.length; i++) d_count_3[i] /= s2;

            int failCount = 0;
            for (int i = 7; i < d_count_3.length; i++) {
                if (d_count_3[i] > 0) failCount++;

                if (failCount > 1) {
                    System.out.println("Warning: Eliminating noise for higher bins");
                    System.arraycopy(new double[d_count_3.length - 7], 0, d_count_3, 7, d_count_3.length - 7);
                    double s3 = util.sumArr(d_count_3, 0, d_count_3.length);
                    for (int j = 0; j < d_count_3.length; j++) d_count_3[j] /= s3;
                    break;
                }
            }

            double max = 0;
            int maxIndex = 0;
            for (int i = 0; i < d_count_3.length; i++) {
                if (d_count_3[i] > max) {
                    max = d_count_3[i];
                    maxIndex = i;
                }
            }

            if (maxIndex == 0) right = m - 1;
            else left = m;
        }

        IJ.showMessage("The predicted resolution is " + res + "\nMax Localization Distance is " + maxLocDist);
    }

    private double[] sortProbBins(int[] data, int binStep, int dataQuant, int binCount, double dataN) {
        double[] bins = new double[binCount];
        for (int i = 0; i < data.length; i++) {
            int dataPos = i * dataQuant;
            int index = dataPos / binStep;

            double v = data[i] / dataN;

            if (index >= binCount) bins[binCount - 1] += v;
            else if (index < 0) bins[0] += v;
            else bins[index] += v;
        }

        return bins;
    }

    public static void main(String[] args) throws Exception {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		// see: https://stackoverflow.com/a/7060464/1207769
		Class<?> clazz = determine_bins.class;
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
