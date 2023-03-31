package com.andallfor.imagej.passes.second;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.andallfor.imagej.imagePass.imagePDistExecutor;
import com.andallfor.imagej.passes.first.primaryPassCollector;
import com.jmatio.types.MLCell;
import com.jmatio.types.MLDouble;

public class secondaryPass {
    private int N, res;
    private double maxLocDist, maxFrameValue;
    private int[] expectedSize;
    private MLCell LOC_FINAL, FRAME_INFO;

    public static boolean[][] distMatrixValidator;

    public secondaryPassCollector[] processedData;

    public secondaryPass(MLCell LOC_FINAL, MLCell FRAME_INFO, int N, int res, double maxLocDist, double maxFrameValue, primaryPassCollector[] primaryData) {
        this.LOC_FINAL = LOC_FINAL;
        this.FRAME_INFO = FRAME_INFO;
        this.N = N;
        this.res = res;
        this.maxLocDist = maxLocDist;
        this.maxFrameValue = maxFrameValue;
        this.expectedSize = LOC_FINAL.getDimensions();
        
        distMatrixValidator = new boolean[expectedSize[1]][primaryData[0].distMatrixValidator.length];
        for (int i = 0; i < primaryData.length; i++) distMatrixValidator[i] = primaryData[i].distMatrixValidator;
    }

    public void run() {
        long s1 = System.currentTimeMillis();
        ExecutorService es = Executors.newCachedThreadPool();

        processedData = new secondaryPassCollector[expectedSize[1]];
        secondaryPassAction act = new secondaryPassAction(0, 0, 0, 0, 0);
        for (int i = 0; i < expectedSize[1]; i++) {
            double[][] loc = ((MLDouble) LOC_FINAL.get(i)).getArray();
			double[] fInfo = ((MLDouble) FRAME_INFO.get(i)).getArray()[0];

            secondaryPassCollector collector = new secondaryPassCollector(maxLocDist, res, N);
            imagePDistExecutor executor = new imagePDistExecutor(fInfo, loc, 10_000_00);

            executor.setParameters(act, collector, maxLocDist, maxFrameValue, res, N, i);

            processedData[i] = collector;
            es.execute(executor);
        }

        es.shutdown();

        try {es.awaitTermination(10_000, TimeUnit.MINUTES);}
		catch (InterruptedException e) {return;}

        System.out.println("Secondary pass time: " + (System.currentTimeMillis() - s1) + "\n");
    }
}
