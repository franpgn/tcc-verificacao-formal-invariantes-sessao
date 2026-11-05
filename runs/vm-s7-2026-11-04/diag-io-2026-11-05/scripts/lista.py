import sys
sys.path.insert(0, sys.argv[3])
from h4 import load,wins,hh
rows=load(sys.argv[1],int(sys.argv[2])); w=wins(rows)
prev=None
for (a,t) in w:
    if a>=float(sys.argv[4]): print(hh(t), round(a,2))
