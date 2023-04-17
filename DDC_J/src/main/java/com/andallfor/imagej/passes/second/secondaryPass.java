package com.andallfor.imagej.passes.second;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.andallfor.imagej.blinkingDistribution;
import com.andallfor.imagej.util;
import com.andallfor.imagej.imagePass.imagePDistExecutor;
import com.andallfor.imagej.passes.first.primaryPassCollector;
import com.jmatio.types.MLCell;
import com.jmatio.types.MLDouble;

public class secondaryPass {
    private int N, res;
    private double maxLocDist;
    private int[] expectedSize;
    private MLCell LOC_FINAL, FRAME_INFO;

    public static primaryPassCollector[] primaryData;
    public static blinkingDistribution blinkDist;

    public static double[] deviation_in_prob_mean;

    public secondaryPassCollector[] processedData;

    public secondaryPass(MLCell LOC_FINAL, MLCell FRAME_INFO, int N, int res, double maxLocDist, primaryPassCollector[] primaryData, blinkingDistribution blinkDist) {
        this.LOC_FINAL = LOC_FINAL;
        this.FRAME_INFO = FRAME_INFO;
        this.N = N;
        this.res = res;
        this.maxLocDist = maxLocDist;
        this.expectedSize = LOC_FINAL.getDimensions();

        secondaryPass.primaryData = primaryData;
        secondaryPass.blinkDist = blinkDist;

        // TODO: should prob move this into blinkDist
        deviation_in_prob_mean = new double[blinkDist.m_mat.length];
        for (int i = 0; i < blinkDist.m_mat.length; i++) {
            for (int j = 0; j < blinkDist.m_mat[0].length; j++) deviation_in_prob_mean[i] += blinkDist.m_mat[i][j];
            deviation_in_prob_mean[i] /= (double) blinkDist.m_mat[0].length;
        }
    }

    public void run() {
        long s1 = System.currentTimeMillis();
        ExecutorService es = Executors.newCachedThreadPool();

        processedData = new secondaryPassCollector[expectedSize[1]];
        secondaryPassAction act = new secondaryPassAction(0, 0, 0, 0);
        for (int i = 0; i < expectedSize[1]; i++) {
            double[][] loc = ((MLDouble) LOC_FINAL.get(i)).getArray();
			double[] fInfo = ((MLDouble) FRAME_INFO.get(i)).getArray()[0];

            secondaryPassCollector collector = new secondaryPassCollector(maxLocDist, res, N);
            imagePDistExecutor executor = new imagePDistExecutor(fInfo, loc, util.sumFactorial(fInfo.length) + 1); // we want only one thread per iter here (for eliminate blinking)

            executor.setParameters(act, collector, maxLocDist, res, N, i);

            processedData[i] = collector;
            es.execute(executor);
        }

        es.shutdown();

        try {es.awaitTermination(10_000, TimeUnit.MINUTES);}
		catch (InterruptedException e) {return;}

        System.out.println("Secondary pass time: " + (System.currentTimeMillis() - s1) + "\n");
    }
}
