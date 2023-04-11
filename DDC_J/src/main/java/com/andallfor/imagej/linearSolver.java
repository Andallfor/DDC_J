package com.andallfor.imagej;

import org.orangepalantir.leastsquares.Function;

public class linearSolver {
    public static Function func = new Function() {
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
}
