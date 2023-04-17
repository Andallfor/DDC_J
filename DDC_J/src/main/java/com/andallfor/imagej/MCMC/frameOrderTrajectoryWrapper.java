package com.andallfor.imagej.MCMC;

import java.util.ArrayList;
import java.util.HashSet;

public class frameOrderTrajectoryWrapper {
    public ArrayList<Integer> indices, hits, blinks;
    public HashSet<Integer> framesWithMultiIntersections;
    public int trajectoryHash;

    public frameOrderTrajectoryWrapper(int hash, ArrayList<Integer> idx, HashSet<Integer> intersections, ArrayList<Integer> hits, ArrayList<Integer> blinks) {
        this.trajectoryHash = hash;
        this.indices = idx;
        this.hits = hits;
        this.blinks = blinks;
        this.framesWithMultiIntersections = intersections;
    }

    public String toString() {
        return "trajectory " + trajectoryHash + " (idx: " + indices.toString() + ") intersects with (idx) " + framesWithMultiIntersections.toString() + " at " + hits.toString() + ". intersected blinks are " + blinks.toString();
    }
}
