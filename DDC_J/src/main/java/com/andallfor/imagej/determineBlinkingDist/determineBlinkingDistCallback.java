package com.andallfor.imagej.determineBlinkingDist;

import java.util.stream.IntStream;

import com.andallfor.imagej.imagePass.imagePassAction;
import com.andallfor.imagej.imagePass.imagePassCallback;

public class determineBlinkingDistCallback implements imagePassCallback {
    private double max;
    private int res, N;
    
    public double[] binsBlink, binsNoBlink;
    public double[][] binsFittingBlink;
    public int[] sortedIndex;

    public determineBlinkingDistCallback(double max, int res, int N) {
        this.max = max;
        this.res = res;
        this.N = N;
    }

    public void callback(imagePassAction[] threads) {
        int[] _binsBlink = new int[(int) Math.floor(max / res) + 1];
        int[] _binsNoBlink = new int[(int) Math.floor(max / res) + 1];
        int[][] _binsFittingBlink = new int[N][(int) Math.floor(max / res) + 1];

        // combine results from children
        for (int i = 0; i < threads.length; i++) {
            determineBlinkingDistAction child = (determineBlinkingDistAction) threads[i];
            for (int j = 0; j < child.binsBlink.length; j++) {
                _binsBlink[j] += child.binsBlink[j];
                _binsNoBlink[j] += child.binsNoBlink[j];
            }

            for (int j = 0; j < N; j++) {
                for (int k = 0; k < child.binsBlink.length; k++) {
                    _binsFittingBlink[j][k] += child.binsFittingBlink[j][k];
                }
            }
        }

        int blinkCount = IntStream.of(_binsBlink).sum();
        int noBlinkCount = IntStream.of(_binsNoBlink).sum();

        binsBlink = new double[_binsBlink.length];
        binsNoBlink = new double[_binsNoBlink.length];
        for (int i = 0; i < binsBlink.length; i++) {
            binsBlink[i] = (double) _binsBlink[i] / blinkCount;
            binsNoBlink[i] = (double) _binsNoBlink[i] / noBlinkCount;
        }

        binsFittingBlink = new double[N][_binsBlink.length];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < _binsBlink.length; j++)  {
                double size = IntStream.of(_binsFittingBlink[i]).sum();
                binsFittingBlink[i][j] = (double) _binsFittingBlink[i][j] / size;
            }
        }
    }
}
