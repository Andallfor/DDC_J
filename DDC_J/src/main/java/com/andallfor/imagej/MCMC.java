package com.andallfor.imagej;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.andallfor.imagej.passes.second.secondaryPassCollector;

public class MCMC {
    private secondaryPassCollector[] passes2;
    public MCMC(secondaryPassCollector[] pass2) {
        passes2 = pass2;
    }

    public void run() {
        ExecutorService es = Executors.newCachedThreadPool();
        for (int i = 0; i < passes2.length; i++) {
            
        }

    }
}
