package com.andallfor.imagej;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.ojalgo.array.SparseArray;
import org.ojalgo.array.SparseArray.NonzeroView;
import org.ojalgo.function.PrimitiveFunction;
import org.ojalgo.function.constant.PrimitiveMath;
import org.ojalgo.matrix.Primitive64Matrix;
import org.ojalgo.matrix.store.ElementsSupplier;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.SparseStore;
import org.ojalgo.structure.Access1D;

import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLCell;
import com.jmatio.types.MLDouble;

import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;

public class determine_n implements PlugIn {
    static final int NFTP = 1000, GAP = 10;
    static final String testMat = "C:/Users/leozw/Desktop/code/matlab/ddc/Main_DDC_Folder/User_Guide_Files/Simulation_2_dark_state_sparse_Clusters_10per_3_per_ROI.mat";

    public void run(String arg) {
        MatFileReader mfr = null;
        try {mfr = new MatFileReader(testMat);}
        catch (IOException e) {return;}

        MLCell FRAME_INFO = (MLCell) mfr.getMLArray("Frame_Information");
        MLCell LOC_FINAL = (MLCell) mfr.getMLArray("LocalizationsFinal");

        int[] expectedSize = FRAME_INFO.getDimensions();
        assert Arrays.equals(LOC_FINAL.getDimensions(), expectedSize);

        final int nIter = (int) Math.ceil((double) NFTP / GAP);
        final int nBins = (5000 / 250) + 1 + 1; // +1 to include end, +1 to end with inf
        int[] bins = new int[nBins];
        for (int i = 0; i < nBins - 1; i++) bins[i] = i * 250;
        bins[nBins - 1] = Integer.MAX_VALUE;

        double[][] cum_sum_store = new double[nIter][nBins - 1];
        int[] frame_store = new int[nIter];

        /**
         * this is a terrible hack, please find a workaround
         * we need a way to track all values that satisfy ((x - 1) % GAP) == 0
         * .nonzeros() does not actually track nonzero values, but instead null values that are
         *  instantiated at init of the sparse data structure. I seem to be unable to reinsert empty values
         *  so .nonzeros() will always return the entire array, defeating the entire point of using sparse
         *  however the problem still remains- we need to track the index of all values that satisfy the above condition
         *  (this is represented in the array by non-zero values). Rather than iterate through the entire array
         *  we just record them whenever we process a valid value. This is done through the below function.
         *  However here too we have to use a hack. We are not allowed to modify exterior variables from within a lambda,
         *  specifically that they must be final. So, to track our current index we use a custom class which very literally
         *  has only one int parameter. Because we can modify the instance values of a final class, we are now able to track the
         *  index through incrementing the instance variable every iteration.
         * Of course this raises the issue of if .onAll (which is used to apply the function) is predictable or not.
         *  At least from my basic tests, it seems to be so. (which raises another issue- wouldn't that mean .onAll is not
         *  threaded???? and therefore would be better to just write my own implementation????)
         * Perhaps it would be worth it to look into creating a custom matrix store
         **/
        final incrementor inc = new incrementor();
        final ArrayList<Integer> indexCount = new ArrayList<>();
        PrimitiveFunction.Unary valid = (x) -> {
            inc.i++;
            //if ((x - 1) % GAP == 0) {
            if (x == 1) {
                indexCount.add(inc.i);
                return x;
            }
            return 0;
        };

        for (int i = 0; i < expectedSize[1]; i++) {
            int size = FRAME_INFO.get(i).getSize();
            SparseStore<Double> matrix = SparseStore.PRIMITIVE64.make(size, 1);
            { // allow gc to collect and remove md since its just here to make code look pretty
                double[][] md = ((MLDouble) FRAME_INFO.get(i)).getArray();
                matrix.fillColumn(0, Access1D.wrap(md[0]));
            }

            double[][] cache = ((MLDouble) LOC_FINAL.get(0, i)).getArray();

            ArrayList<Double> total_blink = new ArrayList<Double>();
            for (int j = 0; j < size - 1; j++) {
                matrix
                    .offsets(j + 1, 0)
                    .subtract(matrix.get(j, 0))
                    .onAll(PrimitiveMath.ABS)
                    .onAll(valid)
                    .copy(); // trigger calculations to actually run, not exactly good
                             // but the only other way ik is via supplyTo which requires me
                             // to allocate a new matrix every time which may be worse idk
                
                for (Integer k : indexCount) total_blink.add(dist(cache[j], cache[j + k]));

                inc.i = 0;
                indexCount.clear();
            }
            // TODO: hist counts
            // maybe throw this into the above unary function?
            // TODO: codes not organized correctly rn lol fix!
            break;
        }

        for (int i = 0; i < nIter; i++) {
            //System.out.println("Progress: " + i / (double) nIter);
            for (int j = 0; j < expectedSize[1]; j++) {
                
            }
        }
    }

    private double dist(double[] a, double[] b) {
        double interior = 0;
        for (int i = 0; i < a.length; i++) {
            interior += (a[i] - b[i]) * (a[i] - b[i]);
        }

        return Math.sqrt(interior);
    }

    private int sumFactorial(int x) {
        if (x % 2 == 0) return (x + 1) * (x / 2);
        return (x + 1) * ((x - 1) / 2) + ((x + 1) / 2);
    }

    public static void main(String[] args) throws Exception {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		// see: https://stackoverflow.com/a/7060464/1207769
		Class<?> clazz = determine_n.class;
		java.net.URL url = clazz.getProtectionDomain().getCodeSource().getLocation();
		java.io.File file = new java.io.File(url.toURI());
		System.setProperty("plugins.dir", file.getAbsolutePath());

		// start ImageJ
		new ImageJ();

		// open the Clown sample
		//ImagePlus image = IJ.openImage("http://imagej.net/images/clown.jpg");
		//image.show();

		// run the plugin
		IJ.runPlugIn(clazz.getName(), "");
	}
}
