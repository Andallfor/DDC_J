package com.andallfor.imagej;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.andallfor.imagej.determineBlinkingDist.primaryPassAction;
import com.andallfor.imagej.determineBlinkingDist.primaryPassCollector;
import com.andallfor.imagej.imagePass.imagePDistExecutor;
import com.jmatio.types.MLCell;
import com.jmatio.types.MLDouble;

public class initialPass {
    private int N, res;
    private int[] expectedSize;
    private double maxDist;
    private MLCell LOC_FINAL, FRAME_INFO;

    public primaryPassCollector[] processedData;

    public initialPass(MLCell LOC_FINAL, MLCell FRAME_INFO, int N, int res, double maxDist) {
        this.N = N;
        this.res = res;
        this.maxDist = maxDist;
        this.LOC_FINAL = LOC_FINAL;
        this.FRAME_INFO = FRAME_INFO;
        this.expectedSize = LOC_FINAL.getDimensions();
    }

    public void run() {
        ExecutorService es = Executors.newCachedThreadPool();

        processedData = new primaryPassCollector[expectedSize[1]];
        primaryPassAction act = new primaryPassAction(0, 0, 0); // this is just a holder bc fuck java!
        for (int i = 0; i < expectedSize[1]; i++) {
            double[][] loc = ((MLDouble) LOC_FINAL.get(i)).getArray();
			double[] fInfo = ((MLDouble) FRAME_INFO.get(i)).getArray()[0];

            primaryPassCollector collector = new primaryPassCollector(maxDist, res, N);
            imagePDistExecutor executor = new imagePDistExecutor(fInfo, loc, 10_000_00);

            executor.setParameters(act, collector, maxDist, res, N);

            processedData[i] = collector;
            es.execute(executor);
        }

        es.shutdown();

        try {es.awaitTermination(10_000, TimeUnit.MINUTES);}
		catch (InterruptedException e) {return;}
    }
}
