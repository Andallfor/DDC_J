import scipy.io
import defs

mat = scipy.io.loadmat(defs.EXAMPLE_MAT)
print(mat.keys())
print(mat['TrueLocalizations'][0, 0].shape)
print(mat['TrueLocalizations'])