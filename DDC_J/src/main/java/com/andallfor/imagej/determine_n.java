package com.andallfor.imagej;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.ojalgo.matrix.store.RawStore;

import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLCell;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.plugin.PlugIn;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

public class determine_n implements PlugIn {
    static final int NFTP = 1000, GAP = 10;
    static final String testMat = "C:/Users/leozw/Desktop/code/matlab/ddc/Main_DDC_Folder/User_Guide_Files/Simulation_2_dark_state_sparse_Clusters_10per_3_per_ROI.mat";

    public void run(String arg) {
        MatFileReader mfr = null;
        try {mfr = new MatFileReader(testMat);}
        catch (IOException e) {return;}
        MLCell FRAME_INFO = (MLCell) mfr.getMLArray("Frame_Information");
        MLCell LOC_FINAL = (MLCell) mfr.getMLArray("LocalizationsFinal");

        int[] expectedSize = FRAME_INFO.getDimensions();
        assert Arrays.equals(LOC_FINAL.getDimensions(), expectedSize);

        IJ.showMessage(Arrays.toString(FRAME_INFO.cells().get(0).getDimensions()));

        /*

        final int nIter = (int) Math.ceil((double) NFTP / GAP);
        final int nBins = (5000 / 250) + 1 + 1; // +1 to include end, +1 to end with inf
        int[] bins = new int[nBins];
        for (int i = 0; i < nBins - 1; i++) bins[i] = i * 250;
        bins[nBins - 1] = Integer.MAX_VALUE;

        double[][] cum_sum_store = new double[nIter][nBins - 1];
        int[] frame_store = new int[nIter];

        double[][] z2_arr = new double[expectedSize[1]][];
        double[][] d1_arr = new double[expectedSize[1]][];

        for (int i = 0; i < expectedSize[1]; i++) {
            // frame_info and loc_final sub elements are same length (or should be)
            int length = sumFactorial(FRAME_INFO.get(0).getNumElements() - 1);
            double[] z = new double[length];
            double[] d = new double[length];
            FRAME_INFO.getMatrix(0, 0).
        }

        for (int i = 0; i < nIter; i++) {
            System.out.println("Progress: " + i / (double) nIter);
            for (int j = 0; j < expectedSize[1]; j++) {
                
            }
        }*/
    }

    private int sumFactorial(int x) {
        if (x % 2 == 0) return (x + 1) * (x / 2);
        return (x + 1) * ((x - 1) / 2) + ((x + 1) / 2);
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
