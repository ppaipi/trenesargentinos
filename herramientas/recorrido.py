# Regenera una polilínea de recorridos.json sobre vías OSM.
# Uso: python3 -I recorrido.py vias.json "Línea|||Ramal" PalabraDeLaLínea id1,id2,... RAIZ_REPO
# vias.json = Overpass `out geom` con railway~rail|light_rail|disused|abandoned|razed y "disused:railway".
# Ids de estación en orden de punta a punta. Vías sin la palabra pesan x3. Imprime retrocesos y
# distancia máxima estación-polilínea (validar: 0 y <300 m). Después subir recorridos.json?v=N.
import json,math,heapq,sys
ov,clave,palabra,ids,repo=sys.argv[1],sys.argv[2],sys.argv[3],[int(x) for x in sys.argv[4].split(',')],sys.argv[5]
C=json.load(open(repo+'/estaciones-coords.json'))
def xy(p):return (p[1]*92000.0*math.cos(0)/1, p[0]*111000.0)
def d(a,b):ax,ay=xy(a);bx,by=xy(b);return math.hypot(ax-bx,ay-by)
G={}
for w in json.load(open(ov))['elements']:
  t=w.get('tags',{});s=json.dumps(t,ensure_ascii=False)
  f=1 if (palabra in s or (palabra=='Costa' and t.get('railway')=='light_rail')) else 3
  g=[(round(p['lat'],7),round(p['lon'],7)) for p in w['geometry']]
  for a,b in zip(g,g[1:]):
    L=d(a,b)*f;G.setdefault(a,[]).append((b,L));G.setdefault(b,[]).append((a,L))
nodos=list(G)
sueltos=[n for n in nodos if len(G[n])==1]
for i,a in enumerate(sueltos):
  for b in nodos:
    if b!=a and b not in [x for x,_ in G[a]] and d(a,b)<30:L=d(a,b);G[a].append((b,L));G[b].append((a,L))
def snap(p):return min(nodos,key=lambda n:d(n,p))
def dij(s,t):
  D={s:0};P={};q=[(0,s)]
  while q:
    c,u=heapq.heappop(q)
    if u==t:break
    if c>D[u]:continue
    for v,L in G[u]:
      if c+L<D.get(v,1e18):D[v]=c+L;P[v]=u;heapq.heappush(q,(c+L,v))
  if t not in P:print('sin camino',s,t);return [s,t]
  r=[t]
  while r[-1]!=s:r.append(P[r[-1]])
  return r[::-1]
est=[tuple(C[str(i)]) for i in ids];sn=[snap(p) for p in est]
for i,(p,n) in enumerate(zip(est,sn)):
  if d(p,n)>300:print('lejos',ids[i],round(d(p,n)))
pts=[sn[0]]
for a,b in zip(sn,sn[1:]):pts+=dij(a,b)[1:]
# sacar lazos: si volvemos a <40 m de un punto anterior reciente, cortar el tramo
lim=[]
for p in pts:
  for j in range(len(lim)-1,max(-1,len(lim)-200),-1):
    if j<len(lim)-2 and d(lim[j],p)<40:del lim[j+1:];break
  lim.append(p)
def dp(P,e):
  if len(P)<3:return P
  a,b=P[0],P[-1];ax,ay=xy(a);bx,by=xy(b);dx,dy=bx-ax,by-ay;L=math.hypot(dx,dy) or 1
  m,k=0,0
  for i in range(1,len(P)-1):
    px,py=xy(P[i]);h=abs(dy*(px-ax)-dx*(py-ay))/L
    if h>m:m,k=h,i
  return dp(P[:k+1],e)[:-1]+dp(P[k:],e) if m>e else [a,b]
out=dp(lim,15)
ret=0
for a,b,c in zip(out,out[1:],out[2:]):
  ax,ay=xy(a);bx,by=xy(b);cx,cy=xy(c);u=(bx-ax,by-ay);v=(cx-bx,cy-by)
  n=math.hypot(*u)*math.hypot(*v)
  if n and math.degrees(math.acos(max(-1,min(1,(u[0]*v[0]+u[1]*v[1])/n))))>150:ret+=1
def seg(p,a,b):
  px,py=xy(p);ax,ay=xy(a);bx,by=xy(b);dx,dy=bx-ax,by-ay;L=dx*dx+dy*dy
  t=0 if L==0 else max(0,min(1,((px-ax)*dx+(py-ay)*dy)/L));return math.hypot(px-ax-t*dx,py-ay-t*dy)
mx=max(min(seg(p,out[i],out[i+1]) for i in range(len(out)-1)) for p in est)
print(clave,'puntos',len(out),'retrocesos',ret,'max_est_m',round(mx))
R=json.load(open(repo+'/recorridos.json'));R[clave]=[[round(a,5),round(b,5)] for a,b in out]
json.dump(R,open(repo+'/recorridos.json','w'),ensure_ascii=False,separators=(',',':'))
