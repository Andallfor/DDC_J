package com.andallfor.imagej.MCMC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class frameOrderTrajectoryWrapper {
    public ArrayList<Integer> indices;
    public HashSet<Integer> framesWithMultiIntersections;
    public int trajectoryHash;

    public frameOrderTrajectoryWrapper(int hash, ArrayList<Integer> idx, HashSet<Integer> intersections) {
        this.trajectoryHash = hash;
        this.indices = idx;
        this.framesWithMultiIntersections = intersections;
    }

    public String toString() {
        return "trajectory " + trajectoryHash + " (idx: " + indices.toString() + ") intersects with (idx) " + framesWithMultiIntersections.toString();
    }
}
