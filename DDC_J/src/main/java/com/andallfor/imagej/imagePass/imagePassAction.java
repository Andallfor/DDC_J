package com.andallfor.imagej.imagePass;

// i abs fucking hate java so goddamn much
// to prove a point i will refuse to learn the factory pattern and instead make the most shit code imaginable
// fuck you java it is actually such a terrible fucking language
public abstract class imagePassAction implements Runnable {
    public double[] frame = {};
    public double[][] loc = {{}};
    public int start, end;

    public void setRange(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public abstract imagePassAction createSelf(double[] frame, double[][] loc, double ...parameters);
}
