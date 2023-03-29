package com.andallfor.imagej;

import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.orangepalantir.leastsquares.fitters.NonLinearSolver;
import org.ujmp.core.Matrix;
import org.ujmp.core.calculation.Calculation.Ret;
import org.ujmp.core.doublematrix.DenseDoubleMatrix2D;
import org.ujmp.core.doublematrix.DoubleMatrix2D;

import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLCell;
import com.jmatio.types.MLDouble;

import org.orangepalantir.leastsquares.Fitter;
import org.orangepalantir.leastsquares.Function;

/*
 * Main ddc alg
 * Currently quite a few things are not supported:
 * Photon weighted correction
 * error checking
 * 		sub arr of frame and loc should be the same
 * maybe copy over comments from matlab code
 * sort out licenses
 * replace threads[0].binsBlink.length with a constant/predefined
 */

public class DDC_ implements PlugIn {
	private String filePath = "C:/Users/leozw/Desktop/code/matlab/ddc/Main_DDC_Folder/User_Guide_Files/Simulation_2_dark_state_sparse_Clusters_10per_3_per_ROI.mat";

	private int N = 200;
	private int res = 80;
	private double maxDist = 2916.1458332736643;

	private MLCell LOC_FINAL, FRAME_INFO;
	private int[] expectedSize;

    public void run(String arg) {
		MatFileReader mfr = null;
        try {mfr = new MatFileReader(filePath);}
        catch (IOException e) {
            IJ.showMessage("Invalid File Path");
            return;
        }

		LOC_FINAL = (MLCell) mfr.getMLArray("LocalizationsFinal");
		FRAME_INFO = (MLCell) mfr.getMLArray("Frame_Information");
		expectedSize = LOC_FINAL.getDimensions();

		determineBlinkingRes();
    }

	private void determineBlinkingRes() {
		long s1 = System.currentTimeMillis();
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

		double[] d_count_blink = new double[threads[0].binsBlink.length];
		double[] d_count_no_blink = new double[d_count_blink.length]; // they have the same length

		for (int j = 0; j < d_count_blink.length; j++) {
			for (int i = 0; i < threads.length; i++) {
				d_count_blink[j] += threads[i].binsBlink[j];
				d_count_no_blink[j] += threads[i].binsNoBlink[j];
			}

			d_count_blink[j] /= (double) threads.length;
			d_count_no_blink[j] /= (double) threads.length;
		}

		// this can def be cleaned up
		double d_scale = util.sumArr(d_count_blink, 9, d_count_blink.length) / util.sumArr(d_count_no_blink, 9, d_count_no_blink.length);
		Matrix distribution_for_blink = Matrix.Factory.importFromArray(d_count_blink);
		distribution_for_blink = distribution_for_blink.minus(Matrix.Factory.importFromArray(d_count_no_blink).times(d_scale));
		distribution_for_blink = distribution_for_blink.divide(distribution_for_blink.getValueSum());

		boolean distCleanUpRan = false;
		for (int i = 3; i < distribution_for_blink.getSize(1) - 1; i++) {
			double c = distribution_for_blink.getAsDouble(0, i);
			if (c > 0 && distribution_for_blink.getAsDouble(0, i + 1) < c && !distCleanUpRan) continue;

			distCleanUpRan = true;
			distribution_for_blink.setAsDouble(0, 0, i);
		}

		int noiseFailCount = 0;
		for (int i = 0; i < distribution_for_blink.getSize(1); i++) {
			if (distribution_for_blink.getAsDouble(0, i) < 0) distribution_for_blink.setAsDouble(0, 0, i);

			if (i >= 7 && distribution_for_blink.getAsDouble(0, i) > 0) noiseFailCount++;
		}
		distribution_for_blink = distribution_for_blink.divide(distribution_for_blink.getValueSum());
		distribution_for_blink = distribution_for_blink.divide(distribution_for_blink.getValueSum());

		if (noiseFailCount > 1) {
			System.out.println("Eliminating noise for higher bins. Consider using a lower resolution.");
			for (int i = 7; i < distribution_for_blink.getSize(1); i++) distribution_for_blink.setAsDouble(0, 0, i);
			distribution_for_blink = distribution_for_blink.divide(distribution_for_blink.getValueSum());
		}

		// calc d_scale_store
		Function fun = new Function() {
            @Override
            public double evaluate(double[] values, double[] parameters) {
                double x = parameters[0];
                double a = values[0];
                double b = values[1];

                return x * a + (1 - x) * b;
            }

            @Override
            public int getNParameters() {
                return 1;
            }

            @Override
            public int getNInputs() {
                return 2;
            }
        };

		Fitter solver = new NonLinearSolver(fun);
		double[] initialPara = new double[] {1};
		double[][] _d_scale_store = new double[expectedSize[1]][N];
		for (int i = 0; i < expectedSize[1]; i++) {
			determineBlinkingResParent thread = threads[i];
			double[][] xs = new double[thread.binsNoBlink.length][2];
			for (int j = 0; j < xs.length; j++) xs[j] = new double[] {thread.binsNoBlink[j], distribution_for_blink.getAsDouble(0, j)};

			for (int j = 0; j < N; j++) {
				solver.setData(xs, thread.binsFittingBlink[j]);
				solver.setParameters(initialPara);
				solver.fitData();
				_d_scale_store[i][j] = solver.getParameters()[0];
			}
		}

		double[] d_scale_store = new double[N];
		double[] x_overall = new double[N]; // we need a copy of d_scale_store since we continue to modify this later
		for (int i = 0; i < N; i++) {
			double sum = 0;
			for (int j = 0; j < expectedSize[1]; j++) sum += _d_scale_store[j][i];
			sum /= (double) expectedSize[1];

			x_overall[i] = sum;
			if (sum > 1) sum = 1;
			else if (sum < 0) sum = 0.0000001;
			d_scale_store[i] = sum;
		}

		d_scale_store[d_scale_store.length - 1] = 1;

		Matrix[] imageStack = new Matrix[expectedSize[1]];
		for (int i = 0; i < expectedSize[1]; i++) {
			determineBlinkingResParent thread = threads[i];
			Matrix img = DoubleMatrix2D.Factory.zeros(N, threads[0].binsBlink.length);
			for (int j = 0; j < N; j++) {
				double d = d_scale_store[j];
				Matrix m = DoubleMatrix2D.Factory.importFromArray(thread.binsNoBlink);
				Matrix t = m.times(d).plus(distribution_for_blink.times(1.0 - d));
				Matrix combined = t.minus(m.times(d));
				combined = combined.divide(t);

				img.setContent(Ret.ORIG, combined, j, 0);
			}

			imageStack[i] = img;
		}

		double[][] m_mat = new double[N][threads[0].binsBlink.length];
		for (int i = 0; i < N; i++) {
			for (int j = 0; j < threads[0].binsBlink.length; j++) {
				double sum = 0;
				for (int k = 0; k < expectedSize[1]; k++) sum += imageStack[k].getAsDouble(i, j);
				sum /= (double) expectedSize[1];

				m_mat[i][j] = sum;
			}
		}

		System.out.println("Blinking Res Time: " + (int) (System.currentTimeMillis() - s1));
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
