package com.andallfor.imagej;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ij.IJ;
import ij.ImageJ;
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
    private final String filePath = "C:/Users/leozw/Desktop/code/matlab/ddc/Main_DDC_Folder/User_Guide_Files/Simulation_2_dark_state_sparse_Clusters_10per_3_per_ROI.mat";
    private final int N = 200;

    public void run(String arg) {
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
        // also find D_blink and D_no_blink (from src) as the only that changes each iteration is the histogram, not the data
        // TODO: would it be faster to have two arrays of sumFac size? would 2x memory but no need to reallocate space for array
        ArrayList<ArrayList<Double>> D_blink = new ArrayList<ArrayList<Double>>();
        ArrayList<ArrayList<Double>> D_no_blink = new ArrayList<ArrayList<Double>>();
        ArrayList<determineBinThread> threads = new ArrayList<determineBinThread>();
        // setup arrayLists (java is really bad and cant fix arrays and lists)
        for (int i = 0; i < expectedSize[1]; i++) {
            D_blink.add(new ArrayList<Double>());
            D_no_blink.add(new ArrayList<Double>());
        }

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
            IJ.showMessage("Unable to run initial threads.");
            return;
        }

        for (determineBinThread thread : threads) {
            D_blink.set(thread.iter, thread.blink);
            D_no_blink.set(thread.iter, thread.noBlink);
            if (thread.maxLocDist > maxLocDist) maxLocDist = thread.maxLocDist;
        }

        // utilize binary search-esq alg to determine correct bin size
        // 0 -> 150 step size of 10
        // this works because the change is monotonic [111111111110000000000000] always
        //int left = 0, right = 15, step = 10;
        //int res = 0;
        //while (left != right) {
        //    int m = (int) Math.ceil((left + right) / 2.0);
        //    res = m * step;
//
        //    int[] bins = util.makeBins(maxLocDist, res);
//
        //    if (false) right = m - 1;
        //    else left = m;
        //}
//
        //System.out.println(res);

        for (int i = 0; i < 24; i++) {
            System.out.println(i);
            System.out.println(D_blink.get(i).size() + D_no_blink.get(i).size());
            System.out.println(D_blink.get(i).size());
            System.out.println(D_no_blink.get(i).size());
            System.out.println("====");
        }
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
