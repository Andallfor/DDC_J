package com.andallfor.imagej.MCMC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.commons.statistics.distribution.NormalDistribution;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.api.tuple.primitive.IntDoublePair;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.IntDoubleHashMap;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import com.andallfor.imagej.util;
import com.andallfor.imagej.passes.first.primaryPassCollector;
import com.andallfor.imagej.passes.second.secondaryPass;
import com.andallfor.imagej.passes.second.secondaryPassCollector;

public class MCMCThread implements Runnable {
    private int[] framesWithMulti;
    private double[] frame;
    private double[][] loc;

    // this is not efficient, but as this is just a proof of concept it is fine
    // replace this with a true caching system later
    private MutableIntSet truthIndexes; // TODO: try implementing without set (self impl) (for speed)
    HashMap<Integer, IntArrayList> trajectories;
    private boolean[] repeatMask; // false = repeat true = truth

    private int res, N, probDistCols, probDistRows;

    private primaryPassCollector p1;
    private secondaryPassCollector p2;

    public MCMCThread(double[] frame, double[][] loc, int res, int N, primaryPassCollector p1, secondaryPassCollector p2) {
        this.p1 = p1;
        this.p2 = p2;
        this.frame = frame;
        this.loc = loc;
        this.res = res;
        this.N = N;

        framesWithMulti = new int[p1.framesWithMulti.size()]; // copy values to arr bc java bad or something
        int c = 0;
        for (Integer i : p1.framesWithMulti) {
            framesWithMulti[c] = i;
            c++;
        }

        probDistCols = p2.probDist[0].length;
        probDistRows = p2.probDist.length;
    }

    private double prepare() {
        truthIndexes = new IntHashSet((int) (p2.numTruth * 1.25));
        repeatMask = new boolean[frame.length];
        
        // use trajectories as our starting point
        for (int i = 0; i < p2.blinksMask.length; i++) {
            repeatMask[i] = !p2.blinksMask[i];
            if (repeatMask[i]) truthIndexes.add(i);
        }

        return getTrueScore(truthIndexes.toArray(), repeatMask);
    }

    private double checkAddT(int index, int[] indices) {
        double s = 0;

        for (int i = 0; i < indices.length; i++) {
            int dFra = (int) Math.abs(frame[indices[i]] - frame[index]);
            if (dFra >= N || dFra == 0) continue;

            int dLoc = (int) (util.dist(loc[indices[i]], loc[index]) / res);
            if (dLoc >= probDistCols) dLoc = probDistCols - 1;

            s -= p2.probDist[dFra - 1][dLoc]; // P_r1
            s += p2.probDist[probDistRows - 1][dLoc]; // P_t
        }

        return s;
    }

    private double checkRemoveT(int index, int[] indices) {
        double s = 0;

        for (int i = 0; i < indices.length; i++) {
            int dFra = (int) Math.abs(frame[indices[i]] - frame[index]);
            if (dFra >= N || dFra == 0) continue;

            int dLoc = (int) (util.dist(loc[indices[i]], loc[index]) / res);
            if (dLoc >= probDistCols) dLoc = probDistCols - 1;

            s += p2.probDist[dFra - 1][dLoc]; // P_r1
            s -= p2.probDist[probDistRows - 1][dLoc]; // P_t
        }

        return s;
    }

    // TODO: have this accept in repeatMask too
    private double getTrueScore(int[] indices, boolean[] mask) { 
        double P_t = 0;
        double P_r1 = 0;

        // T -> T
        for (int i = 0; i < indices.length; i++) {
            for (int j = i + 1; j < indices.length; j++) {
                int dFra = (int) Math.abs(frame[indices[i]] - frame[indices[j]]);
                if (dFra >= N || dFra == 0) continue;

                int dLoc = (int) (util.dist(loc[indices[i]], loc[indices[j]]) / res);
                if (dLoc >= probDistCols) dLoc = probDistCols - 1;

                P_t += p2.probDist[probDistRows - 1][dLoc];
            }
        }

        // R -> R
        for (int i = 0 ; i < frame.length; i++) {
            if (mask[i]) continue;

            for (int j = i + 1; j < frame.length; j++) {
                if (mask[j]) continue;

                int dFra = (int) Math.abs(frame[i] - frame[j]);
                if (dFra >= N || dFra == 0) continue;

                int dLoc = (int) (util.dist(loc[i], loc[j]) / res);
                if (dLoc >= probDistCols) dLoc = probDistCols - 1;

                P_r1 += p2.probDist[dFra - 1][dLoc];
            }
        }

        // R -> T
        for (int i = 0 ; i < frame.length; i++) {
            if (mask[i]) continue;

            for (int j = 0; j < indices.length; j++) {
                int dFra = (int) Math.abs(frame[i] - frame[indices[j]]);
                if (dFra >= N || dFra == 0) continue;

                int dLoc = (int) (util.dist(loc[i], loc[indices[j]]) / res);
                if (dLoc >= probDistCols) dLoc = probDistCols - 1;

                P_r1 += p2.probDist[dFra - 1][dLoc];
            }
        }

        return P_t + P_r1;
    }

    public void MCMC() {
        /*
         * the main algorithm works by changing the "weights" of each trajectory, thus making them less or more likely. This is done through modifying two monotonic functions,
         *   k(density) (increasing)* and k(frame) (decreasing). The resulting kappa array is additionally of bounded variation (as far as I understand it). Therefore, Jordan's theorem
         *   applies- functions of bounded variation can be written as the difference of two increasing functions (or the sum of an increasing and decreasing function).
         *   This means that the adjustments to k(density) and k(frame) can represent any kappa function/array. What this then means is that we can basically assign any trajectory and there
         *   will be some variation of k(density), k(frame) and order of the array that will create the kappa array that would allow such a trajectory to exist.** This would mean that it is
         *   unnecessary to actually perturb k(density) and k(frame) until we get a good result, we can instead generate a good result and assume that a k(density) and k(frame) exists.
         * 
         * There are a few restrictions. -0.9999 <= kappa <= +inf, avg(k(density)) = 0, avg(k(frame)) = 0, -0.9 <= k(density) <= +inf, -0.9 <= k(frame) <= +inf
         *   Note that even the restrictions are applied after each array is generated, so although kappa = k(frame) + k(density) the lower bound on kappa is not -1.8 but instead -0.9999, which
         *   is enforced by setting all values <-0.9999 to -0.9999. However after doing a lot of manual testing, the values of kappa usually range from -0.6 to 1.25 and avg is basically 0.
         * 
         * TODO: update how we describe we calc scores, its not up to date
         * This then changes the problem to trying to figure out the best set of trajectories. In general, there are two types of actions the algorithm can take- merge a point into another trajectory,
         *   or take a point and form its own trajectory. In both actions, we are concerned only with how they affect the true localizations- changing the ownership of repeats will not change the
         *   likelihood. The score of each action is calculated: change in likelihood * sqrt(removal score * addition score, if applicable). change in likelihood is given by the new likelihood - old likelihood
         *   as per the likelihood function. removal/addition score is the amount the trajectory score needs to be divided/multiplied at be able to accept/reject the point***, then fed into
         *   the normal distribution function
         * 
         * *note on k(density): in the src, the actual k(density) array is not monotonic, but rather is sorted beforehand. This is actually to ensure that the function is monotonic.
         *   This is the trajectory linking code, density is referenced by Constants(ii+investigate), where ii+investigate is an index value. If the k(density) array was not sorted,
         *   this would mean that the function would be monotonic in relation to the index value, not its density (note that this is fine for frame as frame is sorted beforehand). Therefore,
         *   it is necessary to sort density so that ii+investigate would reference the correct density, then reference the correct value of the underlying monotonic function. At least that
         *   is as far as I understand it.
         * 
         * **this technically does not account for the order of which the localizations are passed in. This is important because the alg will always try to link points that are close in frame
         *   value and index value first, which may end up excluding later points. However, this behavior can be encapsulated within kappa as one could increase the probability of the secondary point
         *   such that it would be included into the trajectory. If this has compounding effects on the other trajectories (for example they may try to include the point within their own bounds), kappa
         *   could decrease their probabilities such that they would be unable to assume the new point. That said, I have not done thorough testing of this, so this may very well be wrong.
         * 
         * ***this isnt a great approximation. please find a better approx
         */

        NormalDistribution distribution = NormalDistribution.of(0, 1);

        // create trajectories
        // cant use trajectoryIndexMap since that contains collisions
        HashMap<Integer, ArrayList<Integer>> _trajectories = new HashMap<Integer, ArrayList<Integer>>(p2.numTruth); // trajectory key: [ind of points...]
        int newTrajectoryHash = p2.trajectories.length + 1;
        for (int i = 0; i < p2.trajectories.length; i++) {
            int t = p2.trajectories[i];
            if (t == 0) t = newTrajectoryHash++;
            if (!_trajectories.containsKey(t)) _trajectories.put(t, new ArrayList<Integer>());

            _trajectories.get(t).add(i);

            p2.trajectories[i] = t; // if t == 0 give it an actual trajectory key
        }

        // sanity check
        // there should no duplicate frames amongst each trajectory, and the truth should be the smallest frame
        // also use this time to copy over everything to IntArrayList to prevent autoboxing (this prob actually costs time but fuck java!!!!!)
        trajectories = new HashMap<Integer, IntArrayList>(p2.numTruth);
        for (Integer key : _trajectories.keySet()) {
            ArrayList<Integer> trajectory = _trajectories.get(key);
            ArrayList<Double> existing = new ArrayList<Double>();

            IntArrayList copy = new IntArrayList(trajectory.size());

            for (int i = 0; i < trajectory.size(); i++) {
                if (existing.contains(frame[trajectory.get(i)])) System.out.println("Warning: frame collision detected in trajectories");
                // TODO: add in check for detecting if truth is smallest frame

                copy.add(trajectory.get(i));
                existing.add(frame[trajectory.get(i)]);
            }

            trajectories.put(key, copy);
        }

        double score = prepare();

        int iterations = 0;

        // rather than figure out the score of each movement, first get the top 20 add/removal of a trajectory
        // sort this list by the top score. then for each score, figure out what action would be needed to accomplish it,
        // calculate the odds of it happening, then set that as the new score
        int ddtClamp = secondaryPass.blinkDist.m_mat[0].length;
        int savedValues = 20;
        IntDoubleHashMap scores = new IntDoubleHashMap(savedValues + 1); // ind, score
        while (true) {
            int[] truths = truthIndexes.toArray();

            // get desired changed
            double max = 0;
            double minMax = 0;
            int minKey = -1;

            scores.clear();
            for (int i = 0; i < frame.length; i++) {
                double s = 0;
                if (!repeatMask[i]) s = checkAddT(i, truths);
                else s = checkRemoveT(i, truths);

                if (s > minMax) {
                    scores.addToValue(i, s);

                    // at max values, remove the lowest
                    if (scores.size() >= savedValues) {
                        scores.remove(minKey);

                        // bad, figure out way to store this
                        double currentMin = max;
                        for (IntDoublePair entry : scores.keyValuesView()) {
                            if (entry.getTwo() < currentMin) {
                                minKey = entry.getOne();
                                currentMin = entry.getTwo();
                            }
                        }

                        minMax = currentMin;
                    }

                    if (s > max) max = s;
                }
            }

            if (scores.size() == 0) break;

            // figure out the action needed
            // the goal is to make the move that changes the least
            
            for (IntDoublePair entry : scores.keyValuesView()) {
                /*
                 * Regardless of whether our end goal is to include or exclude a point, we will always have to remove a point from a trajectory. Removing a point is in the name. When adding a 
                 *   point however, because every "action" will always change a truth, adding a point will add a truth, and since each trajectory can only have one truth, we will need to
                 *   create a new trajectory to hold the added truth. Currently, we do not add anything to this new trajectory except for the truth, as the goal is to minimize change. TODO
                 */

                // TODO: dont do this!
                MutableIntSet truthsCopy = new IntHashSet(truthIndexes);
                HashMap<Integer, IntArrayList> trajectoriesCopy = new HashMap<Integer, IntArrayList>(trajectories.size());
                for (Entry<Integer, IntArrayList> e : trajectories.entrySet()) trajectoriesCopy.put(e.getKey(), new IntArrayList(e.getValue().toArray()));
                boolean[] repeatMaskCopy = new boolean[repeatMask.length];
                System.arraycopy(repeatMask, 0, repeatMaskCopy, 0, repeatMask.length);

                double removalScore = 1; // maximize cost [0, 1]
                int stKey = p2.trajectories[entry.getOne()];
                IntArrayList srcTrajectory = trajectories.get(stKey);

                //===============================================================//
                // SOURCE TRAJECTORY                                             //
                //===============================================================//

                // check what would happen to the src trajectory
                if (srcTrajectory.size() != 1) {
                    int[] arr = srcTrajectory.toArray();
                    double minFrame = 1000000;
                    int minFrameIndex = -1; // TODO: need to update src trajectory (set new truth if needed)

                    // get how "strongly" attached the current point is to its trajectory
                    double avg = 0;
                    for (int i = 0; i < arr.length; i++) {
                        if (arr[i] == entry.getOne()) continue;

                        int r = (int) Math.abs(frame[arr[i]] - frame[entry.getOne()]);
                        int c = (int) (util.dist(loc[arr[i]], loc[entry.getOne()]) / res) + 1;
                        if (c > ddtClamp) c = ddtClamp;

                        avg += secondaryPass.blinkDist.m_mat[r - 1][c - 1];

                        if (frame[arr[i]] < minFrame) {
                            minFrame = frame[arr[i]];
                            minFrameIndex = arr[i];
                        }
                    }

                    avg /= arr.length - 1.0;

                    // figure out how much we would need to change kappa by to get it to disconnect
                    if (avg > 0.5) { // sometimes avg will be < 0.5 auto, this is because as we add more points we dont check each and every point again
                        // avg / (1 + kappa) = 0.5, solve for kappa
                        double k = 2 * avg - 1;
                        // in src, constant1 and 2 both are updated, so tech two randn are being called, so rather than *10, *5
                        removalScore = 1 - distribution.cumulativeProbability(5 * k);
                    }

                    // we are removing a point. since we always modify truths, this means that we are removing the truth from the src trajectory and so need to update the trajectory's truth
                    if (repeatMaskCopy[entry.getOne()]) {
                        // set new trajectory truth
                        truthsCopy.add(minFrameIndex);
                        repeatMaskCopy[minFrameIndex] = true;

                        // dont change what we do with the target point, we modify that below
                    }
                } else {// if == 1, then cost is 0
                    // remove source trajectory
                    trajectoriesCopy.remove(stKey);
                }

                //===============================================================//
                // TARGET TRAJECTORY                                             //
                //===============================================================//

                /*
                * if applicable, figure out what would happen to the source trajectory. This step can be ignored if we are adding a truth because the source trajectory will always have at
                *   least two elements, and the element that is changed is a repeat, not a truth. This is because we only call "add a truth" when we see a repeat, and repeats cannot be by
                *   themselves, they have to be contained within a trajectory, and since each trajectory has to contain a truth, the source trajectory will always have at least 2 elements.
                *   And since we take the lazy way (just creating a new trajectory for the truth), and we know that the source trajectory will not have any new truths, we can ignore this step.
                * We only care about this step in order to determine what, if any, new truths will be added as a result of our proposed action. This is estimated by iterating through
                *   trajectories and getting the trajectory that is most likely to accept us, if none are above the 0.5 threshold, just create our own trajectory. 
                *   TODO maybe include the average score of each trajectory or something?
                */
                
                // if we are removing a truth
                if (repeatMaskCopy[entry.getOne()]) {
                    // get trajectory that will accept new point
                    double maxProb = 0;
                    Integer ttKey = 0;
                    for (Entry<Integer, IntArrayList> t : trajectoriesCopy.entrySet()) {
                        if (t.getKey() == stKey) continue;

                        double avg = 0;
                        IntArrayList arr = t.getValue();
                        for (int i = 0; i < arr.size(); i++) {
                            if (frame[arr.get(i)] == frame[entry.getOne()]) { // all frames must be unique within a trajectory
                                avg = 0;
                                break;
                            }

                            int r = (int) Math.abs(frame[arr.get(i)] - frame[entry.getOne()]);

                            if (r >= N) continue;

                            int c = (int) (util.dist(loc[arr.get(i)], loc[entry.getOne()]) / res) + 1;
                            if (c > ddtClamp) c = ddtClamp;

                            avg += secondaryPass.blinkDist.m_mat[r - 1][c - 1];
                        }

                        avg /= (double) arr.size();

                        if (avg > maxProb) {
                            maxProb = avg;
                            ttKey = t.getKey();
                        }
                    }

                    if (maxProb > 0.5) { // add to trajectory
                        IntArrayList arr = trajectoriesCopy.get(ttKey);

                        // check if the new point would become the new truth or not
                        double minFrame = 10000000;
                        int index = -1;
                        for (int i = 0; i < arr.size(); i++) {
                            if (frame[arr.get(i)] < minFrame) {
                                minFrame = frame[arr.get(i)];
                                index = i;
                            }
                        }

                        // check if we need to update that trajectories truth
                        if (frame[entry.getOne()] < minFrame) {
                            // remove the previous truth
                            truthsCopy.remove(index);
                            repeatMaskCopy[index] = false;

                            // set the new point as the new truth, however dont need to actually do anything since the purpose of this segment is to remove the truth, but we want to keep it
                            // so just dont change anything here
                        } else { // the truth of the trajectory doesnt change
                            // but still need to remove entry.getOne() (the entire purpose of this code)
                            truthsCopy.remove(entry.getOne());
                            repeatMaskCopy[entry.getOne()] = false;
                        }

                        arr.add(entry.getOne());
                    } else { // create new trajectory
                        // truth does not change (since each trajectory needs a truth)
                        IntArrayList arr = new IntArrayList();
                        arr.add(entry.getOne());
                        trajectoriesCopy.put(newTrajectoryHash++, arr);
                    }
                }

                double s = getTrueScore(truthsCopy.toArray(), repeatMaskCopy) * removalScore;
            }

            System.out.println(secondaryPass.blinkDist.m_mat[-1]);

            iterations++;

            if (iterations > 10) break;
        }

        System.out.println(truthIndexes.size());
    }

    private void invertPoint(int ind) {
        if (!repeatMask[ind]) truthIndexes.add(ind);
        else truthIndexes.remove(ind);

        repeatMask[ind] = !repeatMask[ind];
    }

    public void run() {}
}
