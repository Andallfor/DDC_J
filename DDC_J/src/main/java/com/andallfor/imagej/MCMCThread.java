package com.andallfor.imagej;

public class MCMCThread implements Runnable {
    private double[] probDist, dOverall, framesWithMulti, loc, frame;
    private double res;


    public MCMCThread(double res, double[] probDist, double[] dOverall, double[] framesWithMulti, double[] loc, double[] frame) {
        this.probDist = probDist;
        this.dOverall = dOverall;
        this.framesWithMulti = framesWithMulti;
        this.loc = loc;
        this.frame = frame;
        this.res = res;
    }

    public void run() {
        // figure out how frames with multi
    }

    private void calcOptimalFrameOrder() {
        // frames with multi represents points that are on the same and relatively close to each other (res * 4)
        // we are allowed to switch these points interchangeably with each other (array order-wise not their actual values)
        //      "these points" are all frame values == to frameWithMulti
        // which will cause the trajectory linking alg to change the order of which they are linked, thus changing which
        // points are considered to be a blink and which are a link. we will then simulate the change in linking via calculating
        // how the calculated score would change depending on which position is blink/link
        // valid positions are the intersection of frameWithMulti ("these points" from above) and the lowest frame of each trajectory
        // (as each trajectory's blink will always be the lowest frame available)
        // rather than actually calculate the new score for each possible variation, we will estimate to save time
        // this is done by in a previous pass that rounds each axis of each point to the nearest res, then counting the number with
        // that res. we round to the nearest res because we have to round it to the nearest res anyways in order to get the probDist index
    }
}
