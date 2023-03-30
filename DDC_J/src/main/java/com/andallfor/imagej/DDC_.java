package com.andallfor.imagej;

import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;

import java.io.IOException;

import com.andallfor.imagej.determineBlinkingDist.blinkingDistribution;
import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLCell;

/*
 * Main ddc alg
 * Currently quite a few things are not supported:
 * Photon weighted correction
 * error checking
 * 		sub arr of frame and loc should be the same
 * maybe copy over comments from matlab code
 * sort out licenses
 * replace threads[0].binsBlink.length with a constant/predefined
 * rename blinkingDistChild and parent to better reflect their roles- the first pass over the data
 */

public class DDC_ implements PlugIn {
	private String filePath = "C:/Users/leozw/Desktop/code/matlab/ddc/Main_DDC_Folder/User_Guide_Files/Simulation_2_dark_state_sparse_Clusters_10per_3_per_ROI.mat";

	private int N = 200;
	private int res = 80;
	private double maxLocDist = 2916.1458332736643;
	private double maxFrameDist = 40114;

	private MLCell LOC_FINAL, FRAME_INFO;

    public void run(String arg) {
		MatFileReader mfr = null;
        try {mfr = new MatFileReader(filePath);}
        catch (IOException e) {
            IJ.showMessage("Invalid File Path");
            return;
        }

		LOC_FINAL = (MLCell) mfr.getMLArray("LocalizationsFinal");
		FRAME_INFO = (MLCell) mfr.getMLArray("Frame_Information");
		int numImages = FRAME_INFO.getDimensions()[1];

		initialPass pass1 = new initialPass(LOC_FINAL, FRAME_INFO, N, res, maxLocDist);
		pass1.run();

		blinkingDistribution blinkDist = new blinkingDistribution(N, numImages);
		blinkDist.run(pass1.processedData);
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
