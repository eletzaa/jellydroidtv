# Erzeugt die Platzhalterbilder in docs/vorschau/bilder/ (keine fremden Filmbilder).
# Aufruf: python3 docs/vorschau/bilder-erzeugen.py docs/vorschau/bilder
import numpy as np, sys
from PIL import Image, ImageFilter
OUT=sys.argv[1]; W,H=1280,720
rng=np.random.default_rng(3)
def lerp(a,b,t): return a+(b-a)*t
def grad(stops,n):
    xs=np.linspace(0,1,n); out=np.zeros((n,3))
    for i in range(3):
        out[:,i]=np.interp(xs,[s[0] for s in stops],[s[1][i] for s in stops])
    return out
def sky(stops):
    g=grad(stops,H); return np.repeat(g[:,None,:],W,axis=1)
def ridge(base,amp,rough,seed,smooth=1):
    r=np.random.default_rng(seed); n=W; y=np.zeros(n)
    for o in range(1,8):
        f=2**o; k=r.normal(0,1,f+2); xs=np.linspace(0,f,n)
        y+=np.interp(xs,np.arange(f+2),k)*amp/(o**rough)
    return base+y
def fill_below(img,y,color,alpha=1.0):
    yy=np.arange(H)[:,None]; m=(yy>=y[None,:]).astype(float)[...,None]*alpha
    return img*(1-m)+np.array(color)*m
def glow(img,cx,cy,r,color,strength):
    yy,xx=np.mgrid[0:H,0:W]; d=np.sqrt((xx-cx)**2+(yy-cy)**2)/r
    a=np.clip(np.exp(-d**2)*strength,0,1)[...,None]
    return img+(np.array(color)-img)*a
def disc(img,cx,cy,r,color):
    yy,xx=np.mgrid[0:H,0:W]; d=np.sqrt((xx-cx)**2+(yy-cy)**2)
    a=np.clip(r-d+1,0,1)[...,None]; return img*(1-a)+np.array(color)*a
def stars(img,n,maxy):
    for _ in range(n):
        x=rng.integers(0,W); y=rng.integers(0,maxy); b=rng.uniform(.3,1)
        img[y,x]=img[y,x]*(1-b)+255*b
    return img
def save(img,name):
    img=img+rng.normal(0,3,img.shape)
    im=Image.fromarray(np.clip(img,0,255).astype('uint8'))
    im.save(f"{OUT}/{name}.jpg",quality=86)

# 1 Bergdämmerung
i=sky([(0,(28,30,62)),(.45,(150,80,100)),(.62,(245,160,110)),(1,(255,200,150))])
i=glow(i,830,400,260,(255,210,160),.8); i=disc(i,830,410,38,(255,236,200))
for k,(b,a,c) in enumerate([(380,70,(110,70,95)),(450,60,(70,45,75)),(520,50,(40,28,52)),(600,40,(18,14,30))]):
    i=fill_below(i,ridge(b,a,1.1,10+k),c)
save(i,"berge")
# 2 Meer
i=sky([(0,(10,40,80)),(.5,(70,140,180)),(.55,(200,220,225)),(.56,(20,70,100)),(1,(5,25,45))])
i=glow(i,400,390,300,(255,240,210),.55); i=disc(i,400,380,26,(255,250,235))
yy,xx=np.mgrid[0:H,0:W]; wav=(np.sin(xx/9+np.sin(yy/5)*3)*np.exp(-((xx-400)/120)**2)*(yy>400))
i+=np.clip(wav,0,1)[...,None]*np.array([90,80,60])
save(i,"meer")
# 3 Wüste
i=sky([(0,(40,90,160)),(.5,(170,190,210)),(.6,(240,200,150)),(1,(240,200,150))])
for k,(b,a,c) in enumerate([(430,30,(210,140,90)),(480,40,(190,110,70)),(560,50,(150,80,50)),(640,30,(100,50,35))]):
    i=fill_below(i,ridge(b,a,1.6,30+k),c)
i=glow(i,1050,150,200,(255,250,230),.6)
save(i,"wueste")
# 4 Nebelwald
i=sky([(0,(150,170,170)),(1,(60,80,80))])
for k in range(6):
    base=300+k*70; c=np.array([120,140,135])*(1-k*.15)
    y=ridge(base,25,0.6,50+k)
    # Bäume als Zacken
    xs=np.arange(W); y=y-np.abs(np.sin(xs/(7+k)+k))*(30+k*10)
    i=fill_below(i,y,c)
    i=i*.94+np.array([170,185,180])*.06
save(i,"wald")
# 5 Stadt bei Nacht
i=sky([(0,(8,10,30)),(.6,(40,30,80)),(.75,(120,60,110)),(1,(20,15,30))])
i=stars(i,300,350)
x=0
while x<W:
    w=rng.integers(40,110); h=rng.integers(150,420); c=rng.uniform(15,35)
    top=H-h; i[top:,x:x+w]=np.array([c,c,c+15])
    for wy in range(top+10,H-10,14):
        for wx in range(x+6,x+w-6,10):
            if rng.random()<.35: i[wy:wy+6,wx:wx+5]=np.array([255,210,140])*rng.uniform(.5,1)
    x+=w+rng.integers(2,10)
i=glow(i,640,720,500,(255,120,160),.25)
save(i,"stadt")
# 6 Polarlicht
i=sky([(0,(4,8,22)),(.7,(10,30,50)),(1,(10,20,30))]); i=stars(i,500,500)
yy,xx=np.mgrid[0:H,0:W]
for k,(col,off) in enumerate([((80,255,170),0),((120,200,255),60),((200,120,255),120)]):
    c=200+off+np.sin(xx/170+k)*60+np.sin(xx/53)*15
    band=np.exp(-((yy-c)/(40+20*k))**2)*(yy<c+30)*.65
    i=i+(np.array(col)-i)*band[...,None]*.7
i=fill_below(i,ridge(560,30,1.2,77),(8,12,18))
save(i,"polarlicht")
# 7 Weltall
i=np.zeros((H,W,3))+np.array([5,6,14]); i=stars(i,900,H)
i=glow(i,300,200,350,(80,40,140),.5); i=glow(i,1000,500,300,(20,80,140),.4)
i=disc(i,880,330,220,(40,90,150)); 
d=np.sqrt((xx-880)**2+(yy-330)**2); m=(d<220)
shade=np.clip((xx-760)/300+(yy-260)/300,0,1)
i[m]=i[m]*(1-shade[m][...,None]*.85)
i=glow(i,700,250,160,(150,200,255),.25)
save(i,"weltall")
# 8 Canyon
i=sky([(0,(60,120,190)),(.4,(200,170,150)),(1,(200,170,150))])
for k,(b,a,c) in enumerate([(300,60,(180,100,70)),(380,70,(150,70,45)),(470,60,(120,50,35)),(580,40,(70,30,25))]):
    i=fill_below(i,ridge(b,a,.9,90+k),c)
save(i,"canyon")
# 9 Schnee
i=sky([(0,(120,150,190)),(.5,(220,230,240)),(1,(240,245,250))])
for k,(b,a,c) in enumerate([(330,90,(170,185,210)),(420,70,(200,210,228)),(520,50,(225,232,242))]):
    i=fill_below(i,ridge(b,a,1.3,110+k),c)
i=glow(i,250,150,250,(255,250,240),.5)
save(i,"schnee")
# 10 Neon
i=sky([(0,(20,5,40)),(.55,(120,20,120)),(.6,(255,100,120)),(.61,(20,5,40)),(1,(10,0,25))])
i=disc(i,640,380,140,(255,160,90)); 
for k in range(8): i[300+k*14:300+k*14+5,500:780]=np.array([120,20,120])
for x in range(-2000,3000,80):
    for yv in range(440,H):
        t=(yv-440)/(H-440); xx2=int(640+(x-640)*(0.2+t))
        if 0<=xx2<W: i[yv,xx2]=np.array([255,80,200])
for yv in [450,465,485,512,550,600,665]: i[yv,:]=np.array([255,80,200])
save(i,"neon")
# 11 Herbst
i=sky([(0,(230,150,80)),(.6,(250,210,150)),(1,(120,60,30))])
for k in range(5):
    y=ridge(330+k*80,40,0.7,130+k); xs=np.arange(W); y=y-np.abs(np.sin(xs/(11+k)))*(40)
    c=np.array([200,90,40])*(1-k*.17)
    i=fill_below(i,y,c)
save(i,"herbst")
# 12 See
i=sky([(0,(30,50,90)),(.48,(250,170,120)),(.5,(250,170,120)),(1,(30,50,90))])
i=fill_below(i,ridge(330,25,1.2,150),(40,40,70)); 
top=i[:360][::-1]; i[360:720]=top*.75
save(i,"see")
print("ok")
