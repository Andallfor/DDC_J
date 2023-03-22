import math
import matplotlib.pyplot as plt
import scipy.io
import scipy.spatial.distance
import defs
import numpy as np
import time

NFTP = 1000
GAP = 10

mat = scipy.io.loadmat(defs.EXAMPLE_MAT)
frame_info = mat['Frame_Information'][0]
true_loc = mat['TrueLocalizations']
loc_final = mat['LocalizationsFinal'][0]

cum_sum_store = []
frameStore = []
bins = []
for i in range(int((5000 / 250) + 1)):
    bins.append(i * 250)
bins.append(math.inf)
bins = np.array(bins)

startTime = time.time()
z2_list = []
d1_list = []
for ii in range(20):
    for i in range(len(loc_final)):
        fInfo = frame_info[i]
        X = np.hstack((np.zeros((fInfo.shape[1], 1)), fInfo.reshape(fInfo.shape[1], 1)))
        z2 = scipy.spatial.distance.pdist(X)
        d = scipy.spatial.distance.pdist(loc_final[i])
        
        z2_list.append(z2)
        d1_list.append(d)
        d = d[z2 == 1]

print(time.time() - startTime)
for i in range(1, NFTP, GAP):
    print(f'Progress: {i / NFTP}')
    total_blink = np.array([])
    
    for j in range(len(loc_final)):
        #fInfo = frame_info[j]
        #X = np.hstack((np.zeros((fInfo.shape[1], 1)), fInfo.reshape(fInfo.shape[1], 1)))
        #z2 = scipy.spatial.distance.pdist(X)
        #d = scipy.spatial.distance.pdist(loc_final[j])
        d = d1_list[j][z2_list[j] == i]
        #d = d[z2 == i]
        
        total_blink = np.concatenate([total_blink, d], axis=None)
    hist, _ = np.histogram(total_blink, bins)

    cdf = np.ndarray(shape=(hist.shape[0]))
    prev = 0
    num = total_blink.shape[0]
    for j in range(cdf.shape[0]):
        cdf[j] = prev + hist[j] / num
        prev = cdf[j]
    cum_sum_store.append(cdf)
    frameStore.append(i)

Z = []
for i in range(len(cum_sum_store)):
    Z.append(np.sum(np.absolute(cum_sum_store[0] - cum_sum_store[i])))

plt.plot(frameStore, Z)
plt.show()
print(Z)

# matlab: 1.20m
# python 2.20m (no multiprocessing)
# python without redundancy: very fast (not plugged in)

# java 1. 239520
# python 1. 88
# java 2. 7