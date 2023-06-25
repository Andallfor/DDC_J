import matplotlib.pyplot as plt
import numpy as np
import random

c1 = np.zeros(4000)
c2 = np.zeros(4000)

for i in range(10000):
    if True:
        r = random.randint(0, c1.shape[0] - 1)
        
        c1[r] += np.random.randn() * 0.1
        
        if random.random() < 0.5:
            for j in range(1, len(c1)):
                if c1[j - 1] > c1[j]:
                    c1[j] = c1[j - 1]
        else:
            for j in range(len(c1) - 1, 0, -1):
                if c1[j - 1] > c1[j]:
                    c1[j - 1] = c1[j]
        
        c1[c1 < -0.9] = -0.9
        
        #avg = np.mean(c1)
        #if avg < 0:
        #    c1 += abs(avg)
        #else:
        #    c1 -= abs(avg)
    
    if True: # python brackets moment
        continue
        r = random.randint(0, c2.shape[0] - 1)
        
        c2[r] += np.random.randn() * 0.1
        
        if random.random() < 0.5:
            for j in range(1, len(c2)):
                if c2[j - 1] < c2[j]:
                    c2[j] = c2[j - 1]
        else:
            for j in range(len(c2) - 1, 0, -1):
                if c2[j - 1] < c2[j]:
                    c2[j - 1] = c2[j]

        c2[c2 < -0.9] = -0.9
        
        avg = np.mean(c2)
        if avg < 0:
            c2 += abs(avg)
        else:
            c2 -= abs(avg)

kappa = c1 + c2
kappa[kappa < -0.9999] = -0.9999

print(len(np.unique(c1)))
print(len(np.unique(c2)))

fig, axes = plt.subplots(2, 1)

axes[0].plot(c1)
axes[0].plot(c2)
axes[0].plot(kappa)

axes[1].hist(kappa, bins='auto')

print(np.mean(c1))
print(np.mean(c2))
print(np.mean(kappa))

plt.show()