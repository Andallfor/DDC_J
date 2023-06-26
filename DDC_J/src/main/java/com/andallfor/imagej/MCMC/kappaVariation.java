package com.andallfor.imagej.MCMC;

import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.math3.distribution.NormalDistribution;

public class kappaVariation {
    class kappaFrameSortWrapper {
        public double v;
        public int index;
        public kappaFrameSortWrapper(double v, int index) {
            this.v = v;
            this.index = index;
        }
    }

    private NormalDistribution distribution;
    private double[] kappa, kappaBackup;
    private int[] redirect;
    private boolean increasing;

    public kappaVariation(double[] v, boolean increasing) {
        // this isnt great, there is a faster way to do this (via only storing the places where the arr changes value)
        // but for the life of me i cannot seem to get it working and i give up so im just sorting this array then adding
        // a redirect array on top of it that stores the original indices (since the rest of the program assumes the data
        // is not sorted)
        // it also works out nicely with sorting densities
        this.redirect = new int[v.length];

        kappaFrameSortWrapper[] kfsw = new kappaFrameSortWrapper[v.length];
        for (int i = 0; i < v.length; i++) kfsw[i] = new kappaFrameSortWrapper(v[i], i);
        Arrays.sort(kfsw, (a, b)->Double.compare(a.v, b.v));
        for (int i = 0; i < v.length; i++) redirect[kfsw[i].index] = i;

        this.increasing = increasing;

        distribution = new NormalDistribution(0, 1);
        kappa = new double[v.length];
        kappaBackup = new double[v.length];
    }

    public double get(int index) {return kappa[redirect[index]];}

    public void backup() {
        for (int i = 0; i < kappa.length; i++) kappaBackup[i] = kappa[i];
    }

    public void restore() {
        for (int i = 0; i < kappa.length; i++) kappa[i] = kappaBackup[i];
    }

    public void vary() {
        double change = distribution.sample() * 0.1;
        int index = (int) (Math.random() * kappa.length);

        kappa[index] += change;

        if (increasing) forceIncreasing();
        else forceDecreasing();

        double avg = 0;
        for (int i = 0; i < kappa.length; i++) {
            if (kappa[i] < -0.9) kappa[i] = -0.9;

            avg += kappa[i];
        }

        avg /= (double) kappa.length;

        if (avg < 0) {
            for (int i = 0; i < kappa.length; i++) {
                kappa[i] += Math.abs(avg);
            }
        } else {
            for (int i = 0; i < kappa.length; i++) {
                kappa[i] -= Math.abs(avg);
            }
        }
    }

    private void forceIncreasing() {
        if (Math.random() > 0.5) {
            for (int i = 1; i < kappa.length; i++) {
                if (kappa[i - 1] > kappa[i]) {
                    kappa[i] = kappa[i - 1];
                }
            }
        } else {
            for (int i = kappa.length - 1; i > 0; i--) {
                if (kappa[i - 1] > kappa[i]) {
                    kappa[i - 1] = kappa[i];
                }
            }
        }
    }

    private void forceDecreasing() {
        if (Math.random() > 0.5) {
            for (int i = 1; i < kappa.length; i++) {
                if (kappa[i - 1] < kappa[i]) {
                    kappa[i] = kappa[i - 1];
                }
            }
        } else {
            for (int i = kappa.length - 1; i > 0; i--) {
                if (kappa[i - 1] < kappa[i]) {
                    kappa[i - 1] = kappa[i];
                }
            }
        }
    }

    public String toString() {
        HashSet<Double> seen = new HashSet<Double>();
        String s = "";

        double sum = 0;
        for (int i = 0; i < kappa.length; i++) {
            sum += kappa[i];
            if (seen.contains(kappa[i])) continue;

            seen.add(kappa[i]);
            s += kappa[i] + " at " + i + "\n";
        }

        s += "\nSize: " + seen.size() + "\nAvg: " + sum / kappa.length;

        return s;
    }

    public int unique() {
        HashSet<Double> seen = new HashSet<Double>();
        
        for (int i = 0; i < kappa.length; i++) {
            if (seen.contains(kappa[i])) continue;

            seen.add(kappa[i]);
        }

        return seen.size();
    }
}
