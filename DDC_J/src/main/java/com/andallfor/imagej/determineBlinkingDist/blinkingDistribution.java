package com.andallfor.imagej.determineBlinkingDist;

import org.orangepalantir.leastsquares.fitters.NonLinearSolver;
import org.ujmp.core.Matrix;
import org.ujmp.core.calculation.Calculation.Ret;
import org.ujmp.core.doublematrix.DoubleMatrix2D;

import com.andallfor.imagej.util;

import java.util.Arrays;

import org.orangepalantir.leastsquares.Fitter;
import org.orangepalantir.leastsquares.Function;

public class blinkingDistribution {
    private int N, numImages;

    public double[] x_overall, distribution_for_blink;
    public double[][] m_mat;

    public blinkingDistribution(int N, int numImages) {
        this.N = N;
		this.numImages = numImages;
    }

    public void run(primaryPassCollector[] threads) {
		long s1 = System.currentTimeMillis();

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
		Matrix _distribution_for_blink = Matrix.Factory.importFromArray(d_count_blink);
		_distribution_for_blink = _distribution_for_blink.minus(Matrix.Factory.importFromArray(d_count_no_blink).times(d_scale));
		_distribution_for_blink = _distribution_for_blink.divide(_distribution_for_blink.getValueSum());

		boolean distCleanUpRan = false;
		for (int i = 3; i < _distribution_for_blink.getSize(1) - 1; i++) {
			double c = _distribution_for_blink.getAsDouble(0, i);
			if (c > 0 && _distribution_for_blink.getAsDouble(0, i + 1) < c && !distCleanUpRan) continue;

			distCleanUpRan = true;
			_distribution_for_blink.setAsDouble(0, 0, i);
		}

		int noiseFailCount = 0;
		for (int i = 0; i < _distribution_for_blink.getSize(1); i++) {
			if (_distribution_for_blink.getAsDouble(0, i) < 0) _distribution_for_blink.setAsDouble(0, 0, i);

			if (i >= 7 && _distribution_for_blink.getAsDouble(0, i) > 0) noiseFailCount++;
		}
		_distribution_for_blink = _distribution_for_blink.divide(_distribution_for_blink.getValueSum());
		_distribution_for_blink = _distribution_for_blink.divide(_distribution_for_blink.getValueSum());

		if (noiseFailCount > 1) {
			System.out.println("Eliminating noise for higher bins. Consider using a lower resolution.");
			for (int i = 7; i < _distribution_for_blink.getSize(1); i++) _distribution_for_blink.setAsDouble(0, 0, i);
			_distribution_for_blink = _distribution_for_blink.divide(_distribution_for_blink.getValueSum());
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
		double[][] _d_scale_store = new double[numImages][N];
		for (int i = 0; i < numImages; i++) {
			//determineBlinkingDistParent thread = threads[i];
			primaryPassCollector thread = threads[i];
			double[][] xs = new double[thread.binsNoBlink.length][2];
			for (int j = 0; j < xs.length; j++) xs[j] = new double[] {thread.binsNoBlink[j], _distribution_for_blink.getAsDouble(0, j)};

			for (int j = 0; j < N; j++) {
				solver.setData(xs, thread.binsFittingBlink[j]);
				solver.setParameters(initialPara);
				solver.fitData();
				_d_scale_store[i][j] = solver.getParameters()[0];
			}
		}

		double[] d_scale_store = new double[N];
		x_overall = new double[N]; // we need a copy of d_scale_store since we continue to modify this later
		for (int i = 0; i < N; i++) {
			double sum = 0;
			for (int j = 0; j < numImages; j++) sum += _d_scale_store[j][i];
			sum /= (double) numImages;

			x_overall[i] = sum;
			if (sum > 1) sum = 1;
			else if (sum < 0) sum = 0.0000001;
			d_scale_store[i] = sum;
		}

		d_scale_store[d_scale_store.length - 1] = 1;

		Matrix[] imageStack = new Matrix[numImages];
		for (int i = 0; i < numImages; i++) {
			//determineBlinkingDistParent thread = threads[i];
			primaryPassCollector thread = threads[i];
			Matrix img = DoubleMatrix2D.Factory.zeros(N, threads[0].binsBlink.length);
			for (int j = 0; j < N; j++) {
				double d = d_scale_store[j];
				Matrix m = DoubleMatrix2D.Factory.importFromArray(thread.binsNoBlink);
				Matrix t = m.times(d).plus(_distribution_for_blink.times(1.0 - d));
				Matrix combined = t.minus(m.times(d));
				combined = combined.divide(t);

				img.setContent(Ret.ORIG, combined, j, 0);
			}

			imageStack[i] = img;
		}

		m_mat = new double[N][threads[0].binsBlink.length];
		for (int i = 0; i < N; i++) {
			for (int j = 0; j < threads[0].binsBlink.length; j++) {
				double sum = 0;
				for (int k = 0; k < numImages; k++) sum += imageStack[k].getAsDouble(i, j);
				sum /= (double) numImages;

				m_mat[i][j] = sum;
			}
		}

        distribution_for_blink = _distribution_for_blink.toDoubleArray()[0];

		System.out.println(Arrays.toString(distribution_for_blink));

		System.out.println("Blinking Res Time: " + (int) (System.currentTimeMillis() - s1));
	}
}
