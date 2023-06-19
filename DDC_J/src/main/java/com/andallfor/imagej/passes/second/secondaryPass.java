package com.andallfor.imagej.passes.second;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.andallfor.imagej.DDC_;
import com.andallfor.imagej.util;
import com.andallfor.imagej.imagePass.imagePDistExecutor;
import com.jmatio.types.MLDouble;

public class secondaryPass {
    public secondaryPassCollector[] processedData;

    public void run() {
        long s1 = System.currentTimeMillis();
        ExecutorService es = Executors.newCachedThreadPool();

        processedData = new secondaryPassCollector[DDC_.expectedSize[1]];
        secondaryPassAction act = new secondaryPassAction(0, 0, 0, 0);
        for (int i = 0; i < DDC_.expectedSize[1]; i++) {
            double[][] loc = ((MLDouble) DDC_.LOC_FINAL.get(i)).getArray();
			double[] fInfo = ((MLDouble) DDC_.FRAME_INFO.get(i)).getArray()[0];

            secondaryPassCollector collector = new secondaryPassCollector(DDC_.maxLocDist, DDC_.res, DDC_.N);
            imagePDistExecutor executor = new imagePDistExecutor(fInfo, loc, util.sumFactorial(fInfo.length) + 1); // we want only one thread per iter here (for eliminate blinking)

            executor.setParameters(act, collector, DDC_.maxLocDist, DDC_.res, DDC_.N, i);

            processedData[i] = collector;
            es.execute(executor);
        }

        es.shutdown();

        try {es.awaitTermination(10_000, TimeUnit.MINUTES);}
		catch (InterruptedException e) {return;}

        System.out.println("Secondary pass time: " + (System.currentTimeMillis() - s1) + "\n");
    }
}
