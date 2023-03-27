package com.andallfor.imagej;

import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLCell;
import com.jmatio.types.MLDouble;

/*
 * Main ddc alg
 * Currently quite a few things are not supported:
 * Photon weighted correction
 * error checking
 * 		sub arr of frame and loc should be the same
 */

public class DDC_ implements PlugIn {
	private String filePath = "C:/Users/leozw/Desktop/code/matlab/ddc/Main_DDC_Folder/User_Guide_Files/Simulation_2_dark_state_sparse_Clusters_10per_3_per_ROI.mat";

	private int N = 200;
	private int res = 80;
	private double maxDist = 2916.1458332736643;

    public void run(String arg) {
		MatFileReader mfr = null;
        try {mfr = new MatFileReader(filePath);}
        catch (IOException e) {
            IJ.showMessage("Invalid File Path");
            return;
        }

		MLCell LOC_FINAL = (MLCell) mfr.getMLArray("LocalizationsFinal");
		MLCell FRAME_INFO = (MLCell) mfr.getMLArray("Frame_Information");
		int[] expectedSize = LOC_FINAL.getDimensions();

		ExecutorService es = Executors.newCachedThreadPool();
		determineBlinkingResParent[] threads = new determineBlinkingResParent[expectedSize[1]];
		for (int i = 0; i < expectedSize[1]; i++) {
			// not using .parallel/parallelStream since that requires Integer which is so much slower and larger than int
			double[][] loc = ((MLDouble) LOC_FINAL.get(i)).getArray();
			double[] fInfo = ((MLDouble) FRAME_INFO.get(i)).getArray()[0];
			determineBlinkingResParent thread = new determineBlinkingResParent(fInfo, loc, maxDist, res, N);
			threads[i] = thread;

			es.execute(thread);
		}

		es.shutdown();

		try {es.awaitTermination(10_000, TimeUnit.MINUTES);}
		catch (InterruptedException e) {return;}

		for (int i = 0; i < expectedSize[1]; i++) {
			System.out.println("" + i + ": " + Arrays.toString(threads[i].binsNoBlink));
		}
    }

    public static void main(String[] args) throws Exception {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		// see: https://stackoverflow.com/a/7060464/1207769
		Class<?> clazz = DDC_.class;
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
