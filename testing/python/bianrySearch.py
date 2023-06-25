def find(target, start, end, data):
    if (start > end):
        return start
    
    m = int(start + (end - start) / 2)
    
    if (data[m] < target):
        return find(target, m + 1, end, data)
    
    if (data[m] > target):
        return find(target, start, m - 1, data)

    return m

d = [5, 10]
print(find(7, 0, len(d) - 1, d))
    