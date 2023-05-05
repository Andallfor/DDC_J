package com.andallfor.imagej;

import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;

import java.io.IOException;

import com.andallfor.imagej.MCMC.MCMCThread;
import com.andallfor.imagej.passes.first.primaryPass;
import com.andallfor.imagej.passes.second.secondaryPass;
import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLCell;
import com.jmatio.types.MLDouble;

/*
 * Main ddc alg
 * Currently quite a few things are not supported:
 * Photon weighted correction
 * error checking
 * 		sub arr length of frame and loc should be the same
 * maybe copy over comments from matlab code
 * sort out licenses
 * replace threads[0].binsBlink.length with a constant/predefined
 * trying replacing matrixes with arr, might be faster
 * pre-store end points in for loops to stop java from calc them every single time
 * may be worth it to convert FRAME_INFO into an int array before hand
 * usage of true_loc to calc acc in simulated data
 * maybe throw res/N/related things into a global class?
 * error comes from least square solvers -> matlab uses trust region reflection alg
 */

public class DDC_ implements PlugIn {
	//private String filePath = "C:/Users/leozw/Desktop/code/matlab/ddc/Main_DDC_Folder/User_Guide_Files/Simulation_2_dark_state_sparse_Clusters_10per_3_per_ROI.mat";
	//private double maxLocDist = 2916.1458332736643;
	//private double maxFrameDist = 40114;
	//private double maxFrameValue = 40117;

	//private String filePath = "C:/Users/leozw/Desktop/code/matlab/ddc/Main_DDC_Folder/Figure_1_and_2_data_and_analyzed_data/Split_Simulation_2_dark_state_dense_Clusters_50per_3_per_ROI.mat";
	//private double maxLocDist = 2885.857334407272;
	//private double maxFrameDist = 45846;
	//private double maxFrameValue = 45853;

	private String filePath = "C:/Users/leozw/Desktop/code/matlab/ddc/Main_DDC_Folder/Figure_1_and_2_data_and_analyzed_data/Split_Simulation_2_dark_state_No_Clusters_0per_0_per_ROI.mat";
	private double maxLocDist = 2935.765956325998;
	private double maxFrameDist = 41717;
	private double maxFrameValue = 41725;

	private int N = 140;
	private int res = 60;

	private MLCell LOC_FINAL, FRAME_INFO;

    public void run(String arg) {
		long trueS1 = System.currentTimeMillis();

		MatFileReader mfr = null;
        try {mfr = new MatFileReader(filePath);}
        catch (IOException e) {
            IJ.showMessage("Invalid File Path");
            return;
        }

		LOC_FINAL = (MLCell) mfr.getMLArray("LocalizationsFinal");
		FRAME_INFO = (MLCell) mfr.getMLArray("Frame_Information");
		int numImages = FRAME_INFO.getDimensions()[1];

		System.out.println("Mat file reading time: " + (System.currentTimeMillis() - trueS1) + "\n");

		primaryPass firstPass = new primaryPass(LOC_FINAL, FRAME_INFO, N, res, maxLocDist, maxFrameDist, maxFrameValue);
		firstPass.run();

		blinkingDistribution blinkDist = new blinkingDistribution(N, numImages);
		blinkDist.run(firstPass.processedData);

		secondaryPass secondPass = new secondaryPass(LOC_FINAL, FRAME_INFO, N, res, maxLocDist, firstPass.processedData, blinkDist);
		secondPass.run();

		String fancyString = "+ Starting on main MCMC algorithm after " + (System.currentTimeMillis() - trueS1) + " ms +";
		System.out.println(new String(new char[fancyString.length()]).replace('\0', '='));
		System.out.println(fancyString);
		System.out.println(new String(new char[fancyString.length()]).replace('\0', '='));

		MCMCThread mcmc = new MCMCThread(
			((MLDouble) FRAME_INFO.get(0)).getArray()[0], 
			(((MLDouble) LOC_FINAL.get(0))).getArray(), res, N, firstPass.processedData[0], secondPass.processedData[0]);
		
		mcmc.MCMC();

		System.out.println("Total time: " + (System.currentTimeMillis() - trueS1));
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
