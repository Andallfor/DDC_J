package com.andallfor.imagej.passes.first;

import java.util.HashSet;
import java.util.stream.IntStream;

import com.andallfor.imagej.util;
import com.andallfor.imagej.imagePass.imagePassAction;
import com.andallfor.imagej.imagePass.imagePassCallback;

public class primaryPassCollector implements imagePassCallback {
    private double max;
    private int res, N;
    
    public double[] binsBlink, binsNoBlink, density;
    public double[][] binsFittingBlink;
    public int[] sortedIndex;
    public boolean[] distMatrixValidator;
    public HashSet<Integer> framesWithMulti;

    public primaryPassCollector(double max, int res, int N) {
        this.max = max;
        this.res = res;
        this.N = N;
    }

    public void callback(imagePassAction[] threads) {
        int[] _binsBlink =          new int   [(int) Math.floor(max / res) + 1];
        int[] _binsNoBlink =        new int   [(int) Math.floor(max / res) + 1];
        int[][] _binsFittingBlink = new int[N][(int) Math.floor(max / res) + 1];
        density =          new double[((primaryPassAction) threads[0]).frame.length];
        byte[] _distMatrixValidator = new byte[((primaryPassAction) threads[0]).distMatrixValidator.length];
        framesWithMulti = new HashSet<Integer>();

        // combine results from children
        for (int i = 0; i < threads.length; i++) {
            primaryPassAction child = (primaryPassAction) threads[i];

            for (int j = 0; j < child.binsBlink.length; j++) {
                _binsBlink[j] += child.binsBlink[j];
                _binsNoBlink[j] += child.binsNoBlink[j];
            }

            for (int j = 0; j < N; j++) {
                for (int k = 0; k < child.binsBlink.length; k++) {
                    _binsFittingBlink[j][k] += child.binsFittingBlink[j][k];
                }
            }

            for (int j = 0; j < child.distMatrixValidator.length; j++) {
                if (_distMatrixValidator[j] == 100) continue; // < 0 due to overflow

                // byte operations are auto converted to int, so we can just check them here without worry of overflow
                byte cdmv = child.distMatrixValidator[j];
                if (cdmv == -128) _distMatrixValidator[j] = 100; // overflow in child
                else if (_distMatrixValidator[j] + cdmv >= 100) _distMatrixValidator[j] = 100;
                else _distMatrixValidator[j] += cdmv;
            }

            // Frames_W_Multi
            // we use arrayList in primaryPassAction.java as we prioritize adding speed
            // however when processing the results we care more about search speed, so use hashSet
            for (int j = 0; j < child.framesWithMulti.size(); j++) framesWithMulti.add(child.framesWithMulti.get(j));

            for (int j = 0; j < child.density.length; j++) density[j] += child.density[j];
        }

        // assemble collected data into desired formats

        /*
         * Note that although the overall code "flow" is persevered, a lot of the one-to-one variables have been
         * changed or renamed mostly due to improvements that can be made (ie they can be calculated more efficiently/cached)
         * I have tried to match them up, but note that they are not one-to-one and may serve multiple purposes
         */

        //---------------------------------------------------//
        // determine_blinking_distribution5.m                //
        //---------------------------------------------------//

        int blinkCount = IntStream.of(_binsBlink).sum();
        int noBlinkCount = IntStream.of(_binsNoBlink).sum();

        // d_count
        binsBlink = new double[_binsBlink.length];
        binsNoBlink = new double[_binsNoBlink.length];
        for (int i = 0; i < binsBlink.length; i++) {
            binsBlink[i] = (double) _binsBlink[i] / blinkCount;
            binsNoBlink[i] = (double) _binsNoBlink[i] / noBlinkCount;
        }

        // d_scale_store
        binsFittingBlink = new double[N][_binsBlink.length];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < _binsBlink.length; j++)  {
                double size = IntStream.of(_binsFittingBlink[i]).sum();
                binsFittingBlink[i][j] = (double) _binsFittingBlink[i][j] / size;
            }
        }

        //---------------------------------------------------//
        // DDC_MCMC.m and Make_Distance_Matrix_Individual.m  //
        //---------------------------------------------------//

        // DistanceControl2
        distMatrixValidator = new boolean[_distMatrixValidator.length]; // default value is false
        for (int i = 0; i < distMatrixValidator.length; i++) {
            if (_distMatrixValidator[i] == 100) distMatrixValidator[i] = true;
        }

        //---------------------------------------------------//
        // Density_Calc.m                                    //
        //---------------------------------------------------//

        // density
        double densityMin = 1000000, densityMax = 0;
        for (int i = 0; i < density.length; i++) {
            if (density[i] < densityMin) densityMin = density[i];
            if (density[i] > densityMax) densityMax = density[i];
        }
        density = util.arrDivOut(util.arrSubOut(density, densityMin), densityMax - densityMin);
    }
}
