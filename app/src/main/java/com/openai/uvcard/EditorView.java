package com.openai.uvcard;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class EditorView extends View {
    public enum FitMode { COVER, STRETCH, CONTAIN }

    private Bitmap reference;
    private Bitmap designOriginal;
    private Bitmap designPrint;
    private Bitmap uvOverlay;
    private Bitmap textMaskOverlay;

    // TL, TR, BR, BL in normalized reference-image coordinates.
    private final float[] card = new float[]{
            211f/1536f,166f/1152f,
            1250f/1536f,153f/1152f,
            1256f/1536f,806f/1152f,
            216f/1536f,826f/1152f
    };
    private final float[] defaultCard = card.clone();

    // Local overlay transform relative to the physical card.
    private float moveX=0f, moveY=0f, scale=1f, rotationDeg=0f;
    private float leftEdge=0f, rightEdge=0f, topEdge=0f, bottomEdge=0f;

    private FitMode fitMode = FitMode.COVER;
    private float roundPct = 0.045f;
    private float bleedPct = 0.005f;

    private int inkPct=92, texturePct=90, whiteProtectPct=96;
    private boolean protectColorRegions=true;

    // V9.1 portrait/card matching.
    // These defaults match the user-confirmed sample: slightly darker skin,
    // automatic tint matching to the physical card, and a crisper portrait edge.
    private boolean autoCardTone=true;
    private int autoTonePct=65;
    private int portraitDarkPct=10;
    private int portraitEdgeCrispPct=72;
    private boolean uvEnabled=true, strictText=true, showTextMask=false;
    private int threshold=105, uvPct=62, glossPct=68, emboss=3, roughPct=8, featherPct=5;

    private boolean showCorners=true;
    private int dragging=-1;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final RectF refRect = new RectF();

    public EditorView(Context c){ super(c); init(); }
    public EditorView(Context c, AttributeSet a){ super(c,a); init(); }
    private void init(){
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        paint.setDither(true);
        reference = ((BitmapDrawable)getResources().getDrawable(com.openai.uvcard.R.drawable.default_card)).getBitmap();
    }

    public void setReference(Bitmap b, boolean resetCorners){
        reference=ensureArgb8888(b);
        if(resetCorners){
            card[0]=.15f;card[1]=.16f; card[2]=.85f;card[3]=.16f;
            card[4]=.85f;card[5]=.84f; card[6]=.15f;card[7]=.84f;
        }
        if(designOriginal!=null) rebuildPrintBitmap();
        invalidate();
    }

    public void setDesign(Bitmap b){
        designOriginal=ensureArgb8888(b);
        rebuildPrintBitmap();
        rebuildUvOverlay();
        fitCardCenter();
    }

    private static Bitmap ensureArgb8888(Bitmap b){
        if(b==null) return null;
        if(b.getConfig()==Bitmap.Config.ARGB_8888 && b.isPremultiplied()) return b.copy(Bitmap.Config.ARGB_8888,false);
        Bitmap out=Bitmap.createBitmap(b.getWidth(),b.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
        p.setDither(true);
        c.drawBitmap(b,0,0,p);
        return out;
    }

    public boolean hasDesign(){ return designOriginal!=null; }

    public void setShowCorners(boolean v){ showCorners=v; invalidate(); }
    public boolean getShowCorners(){ return showCorners; }
    public void setFitMode(FitMode m){ fitMode=m; invalidate(); }
    public void setRoundPct(float v){ roundPct=v; invalidate(); }
    public void setBleedPct(float v){ bleedPct=v; invalidate(); }

    public void setInkPct(int v){ inkPct=v; invalidate(); }
    public void setTexturePct(int v){ texturePct=v; invalidate(); }
    public void setWhiteProtectPct(int v){ whiteProtectPct=v; rebuildPrintBitmap(); invalidate(); }
    public void setProtectColorRegions(boolean v){ protectColorRegions=v; rebuildPrintBitmap(); invalidate(); }
    public void setAutoCardTone(boolean v){ autoCardTone=v; rebuildPrintBitmap(); invalidate(); }
    public void setAutoTonePct(int v){ autoTonePct=Math.max(0,Math.min(100,v)); rebuildPrintBitmap(); invalidate(); }
    public void setPortraitDarkPct(int v){ portraitDarkPct=Math.max(0,Math.min(30,v)); rebuildPrintBitmap(); invalidate(); }
    public void setPortraitEdgeCrispPct(int v){ portraitEdgeCrispPct=Math.max(0,Math.min(100,v)); rebuildPrintBitmap(); invalidate(); }

    public void setUvEnabled(boolean v){ uvEnabled=v; invalidate(); }
    public void setStrictText(boolean v){ strictText=v; rebuildUvOverlay(); invalidate(); }
    public void setShowTextMask(boolean v){ showTextMask=v; invalidate(); }
    public boolean getShowTextMask(){ return showTextMask; }
    public void setThreshold(int v){ threshold=v; rebuildUvOverlay(); invalidate(); }
    public void setUvPct(int v){ uvPct=v; invalidate(); }
    public void setGlossPct(int v){ glossPct=v; rebuildUvOverlay(); invalidate(); }
    public void setEmboss(int v){ emboss=v; rebuildUvOverlay(); invalidate(); }
    public void setRoughPct(int v){ roughPct=v; rebuildUvOverlay(); invalidate(); }
    public void setFeatherPct(int v){ featherPct=v; rebuildUvOverlay(); invalidate(); }

    public void fitCardCenter(){ fitMode=FitMode.COVER; resetTransform(); }
    public void fitCardContain(){ fitMode=FitMode.CONTAIN; resetTransform(); }
    public void resetCardCorners(){
        System.arraycopy(defaultCard,0,card,0,8);
        if(designOriginal!=null && autoCardTone) rebuildPrintBitmap();
        invalidate();
    }
    public void resetTransform(){
        moveX=moveY=0f; scale=1f; rotationDeg=0f;
        leftEdge=rightEdge=topEdge=bottomEdge=0f;
        invalidate();
    }
    public void move(float dx,float dy){ moveX+=dx;moveY+=dy;invalidate(); }
    public void scaleBy(float f){ scale*=f; scale=Math.max(.2f,Math.min(5f,scale));invalidate(); }
    public void rotateBy(float degrees){
        rotationDeg += degrees;
        while(rotationDeg>180f) rotationDeg-=360f;
        while(rotationDeg<-180f) rotationDeg+=360f;
        invalidate();
    }
    public float getRotationDeg(){ return rotationDeg; }
    public void edgeLeft(float d){ leftEdge+=d;invalidate(); }
    public void edgeRight(float d){ rightEdge+=d;invalidate(); }
    public void edgeTop(float d){ topEdge+=d;invalidate(); }
    public void edgeBottom(float d){ bottomEdge+=d;invalidate(); }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(reference==null)return;
        computeFitCenter(reference.getWidth(),reference.getHeight(),getWidth(),getHeight(),refRect);
        canvas.drawBitmap(reference,null,refRect,paint);
        if(designPrint!=null) drawComposite(canvas,refRect,false);
        if(showCorners) drawHandles(canvas,refRect);
    }

    private void drawComposite(Canvas canvas, RectF rr, boolean export){
        float[] cardPts = cardPoints(rr);
        Path clip = roundedQuad(cardPts, roundPct);
        canvas.save();
        canvas.clipPath(clip);

        float[] dst = transformedOverlayQuad(cardPts);
        RectF srcCrop = sourceCrop(designPrint.getWidth(), designPrint.getHeight(), dst);

        float bleed = bleedPct;
        if(Math.abs(bleed)>0.0001f){
            srcCrop.inset(-srcCrop.width()*bleed*.5f,-srcCrop.height()*bleed*.5f);
        }
        srcCrop.left=Math.max(0,srcCrop.left);srcCrop.top=Math.max(0,srcCrop.top);
        srcCrop.right=Math.min(designPrint.getWidth(),srcCrop.right);srcCrop.bottom=Math.min(designPrint.getHeight(),srcCrop.bottom);

        Matrix m = quadMatrix(srcCrop,dst);
        paint.setAlpha(Math.round(255*inkPct/100f));
        paint.setFilterBitmap(true);
        paint.setDither(true);
        canvas.drawBitmap(designPrint,m,paint);

        if(uvEnabled && uvOverlay!=null){
            Paint uvPaint = new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
            uvPaint.setDither(true);
            uvPaint.setAlpha(Math.round(255*uvPct/100f));
            canvas.drawBitmap(uvOverlay,m,uvPaint);
        }

        if(showTextMask && textMaskOverlay!=null && !export){
            Paint maskPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            maskPaint.setAlpha(185);
            canvas.drawBitmap(textMaskOverlay,m,maskPaint);
        }

        paint.setAlpha(255);
        canvas.restore();
    }

    private RectF sourceCrop(int sw,int sh,float[] dst){
        float dstW=(dist(dst[0],dst[1],dst[2],dst[3])+dist(dst[6],dst[7],dst[4],dst[5]))*.5f;
        float dstH=(dist(dst[0],dst[1],dst[6],dst[7])+dist(dst[2],dst[3],dst[4],dst[5]))*.5f;
        float srcAR=sw/(float)sh;
        float dstAR=dstW/Math.max(1f,dstH);
        if(fitMode==FitMode.STRETCH) return new RectF(0,0,sw,sh);
        if(fitMode==FitMode.COVER){
            if(srcAR>dstAR){
                float cw=sh*dstAR; float l=(sw-cw)/2f; return new RectF(l,0,l+cw,sh);
            }else{
                float ch=sw/dstAR; float t=(sh-ch)/2f; return new RectF(0,t,sw,t+ch);
            }
        }
        return new RectF(0,0,sw,sh);
    }

    private float[] transformedOverlayQuad(float[] cp){
        // Edge controls first define the local rectangle. Scale and rotation are then
        // applied around that rectangle's center, and finally moveX/moveY shifts it.
        float lx=0-leftEdge, rx=1+rightEdge, ty=0-topEdge, by=1+bottomEdge;
        float cx=(lx+rx)/2f, cy=(ty+by)/2f;
        float[][] local={{lx,ty},{rx,ty},{rx,by},{lx,by}};
        float rad=(float)Math.toRadians(rotationDeg);
        float cos=(float)Math.cos(rad), sin=(float)Math.sin(rad);
        float[] out=new float[8];
        for(int i=0;i<4;i++){
            float x=(local[i][0]-cx)*scale;
            float y=(local[i][1]-cy)*scale;
            float xr=x*cos-y*sin;
            float yr=x*sin+y*cos;
            float u=xr+cx+moveX;
            float v=yr+cy+moveY;
            out[i*2]=bilinearQuadX(cp,u,v);
            out[i*2+1]=bilinearQuadY(cp,u,v);
        }
        return out;
    }

    private static float bilinearQuadX(float[] p,float u,float v){
        return (1-u)*(1-v)*p[0] + u*(1-v)*p[2] + u*v*p[4] + (1-u)*v*p[6];
    }
    private static float bilinearQuadY(float[] p,float u,float v){
        return (1-u)*(1-v)*p[1] + u*(1-v)*p[3] + u*v*p[5] + (1-u)*v*p[7];
    }

    private Matrix quadMatrix(RectF src,float[] dst){
        float[] sp={src.left,src.top, src.right,src.top, src.right,src.bottom, src.left,src.bottom};
        Matrix m=new Matrix(); m.setPolyToPoly(sp,0,dst,0,4); return m;
    }

    private Path roundedQuad(float[] p,float frac){
        Path path=new Path();
        float f=Math.max(0f,Math.min(.20f,frac));
        PointF[] c=new PointF[4];
        for(int i=0;i<4;i++)c[i]=new PointF(p[i*2],p[i*2+1]);
        PointF start=toward(c[0],c[3],f);
        path.moveTo(start.x,start.y);
        for(int i=0;i<4;i++){
            PointF cur=c[i],prev=c[(i+3)%4],next=c[(i+1)%4];
            PointF pin=toward(cur,prev,f),pout=toward(cur,next,f);
            path.lineTo(pin.x,pin.y);
            path.quadTo(cur.x,cur.y,pout.x,pout.y);
        }
        path.close(); return path;
    }
    private static PointF toward(PointF from,PointF to,float f){ return new PointF(from.x+(to.x-from.x)*f,from.y+(to.y-from.y)*f); }

    private void drawHandles(Canvas c,RectF rr){
        float[] p=cardPoints(rr);
        Paint hp=new Paint(Paint.ANTI_ALIAS_FLAG); hp.setStyle(Paint.Style.FILL);hp.setColor(Color.RED);
        Paint ring=new Paint(Paint.ANTI_ALIAS_FLAG);ring.setStyle(Paint.Style.STROKE);ring.setStrokeWidth(dp(2));ring.setColor(Color.WHITE);
        for(int i=0;i<4;i++){
            c.drawCircle(p[i*2],p[i*2+1],dp(8),hp);
            c.drawCircle(p[i*2],p[i*2+1],dp(8),ring);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(!showCorners || reference==null)return true;
        computeFitCenter(reference.getWidth(),reference.getHeight(),getWidth(),getHeight(),refRect);
        float[] p=cardPoints(refRect);
        if(e.getAction()==MotionEvent.ACTION_DOWN){
            float best=dp(36); dragging=-1;
            for(int i=0;i<4;i++){
                float d=dist(e.getX(),e.getY(),p[i*2],p[i*2+1]);
                if(d<best){best=d;dragging=i;}
            }
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_MOVE && dragging>=0){
            float nx=(e.getX()-refRect.left)/refRect.width();
            float ny=(e.getY()-refRect.top)/refRect.height();
            card[dragging*2]=Math.max(0,Math.min(1,nx));
            card[dragging*2+1]=Math.max(0,Math.min(1,ny));
            invalidate();return true;
        }
        if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){
            dragging=-1;
            if(designOriginal!=null && autoCardTone) rebuildPrintBitmap();
            invalidate();
            return true;
        }
        return true;
    }

    public Bitmap renderFullResolution(){
        if(reference==null)return null;
        Bitmap out=Bitmap.createBitmap(reference.getWidth(),reference.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
        p.setDither(true);
        c.drawBitmap(reference,0,0,p);
        if(designPrint!=null){
            RectF full=new RectF(0,0,out.getWidth(),out.getHeight());
            drawComposite(c,full,true);
        }
        return out;
    }

    private float[] cardPoints(RectF rr){
        float[] p=new float[8];
        for(int i=0;i<4;i++){
            p[i*2]=rr.left+card[i*2]*rr.width();
            p[i*2+1]=rr.top+card[i*2+1]*rr.height();
        }
        return p;
    }

    private void rebuildPrintBitmap(){
        if(designOriginal==null)return;
        int w=designOriginal.getWidth(),h=designOriginal.getHeight();
        int[] px=new int[w*h];
        designOriginal.getPixels(px,0,w,0,0,w,h);
        float protect=whiteProtectPct/100f;
        float[] cardTone=sampleCardTone();
        float cardR=cardTone[0], cardG=cardTone[1], cardB=cardTone[2], cardLum=Math.max(1f,cardTone[3]);
        float toneStrength=autoCardTone ? autoTonePct/100f : 0f;
        float crisp=portraitEdgeCrispPct/100f;
        float edgeGamma=1f+2.5f*crisp; // higher value = less blurry white-edge transition

        // Keep the card cast subtle. This changes the print/portrait temperature to follow
        // the photographed card without turning skin gray.
        float gainR=clamp(cardR/cardLum,.88f,1.12f);
        float gainG=clamp(cardG/cardLum,.88f,1.12f);
        float gainB=clamp(cardB/cardLum,.88f,1.12f);
        float castMix=.20f*toneStrength;
        float cardDark=Math.max(0f,205f-cardLum)/205f * .06f * toneStrength;
        float userDark=(portraitDarkPct/100f)*.55f;

        for(int i=0;i<px.length;i++){
            int a=Color.alpha(px[i]);
            int r=Color.red(px[i]),g=Color.green(px[i]),b=Color.blue(px[i]);
            float lum=.2126f*r+.7152f*g+.0722f*b;
            int max=Math.max(r,Math.max(g,b));
            int min=Math.min(r,Math.min(g,b));
            float chroma=max-min;

            // V9.1 edge treatment: fully white background still disappears, but the
            // transition is steeper so hair/ears/shoulders do not get a wide gray halo.
            float whiteBase=clamp01((lum-226f)/29f);
            whiteBase=(float)Math.pow(whiteBase,edgeGamma);
            float neutral=protectColorRegions ? clamp01((38f-chroma)/18f) : 1f;
            float white=whiteBase*neutral*protect;
            int na=Math.round(a*(1f-white));

            // Auto card-tone matching only affects portrait-like/midtone print areas.
            // Black text, very light background and strongly saturated red stamps stay intact.
            boolean saturatedRed=r>125 && r>g*1.30f && r>b*1.30f;
            boolean portraitLike=a>20 && lum>62f && lum<226f && chroma<115f && !saturatedRed;
            if(portraitLike){
                float rr=r*((1f-castMix)+castMix*gainR);
                float gg=g*((1f-castMix)+castMix*gainG);
                float bb=b*((1f-castMix)+castMix*gainB);
                float dark=clamp01(userDark+cardDark);
                rr*=1f-dark; gg*=1f-dark; bb*=1f-dark;
                r=clamp255(Math.round(rr));
                g=clamp255(Math.round(gg));
                b=clamp255(Math.round(bb));
            }
            px[i]=Color.argb(na,r,g,b);
        }
        designPrint=Bitmap.createBitmap(px,w,h,Bitmap.Config.ARGB_8888);
        designPrint.setPremultiplied(true);
    }

    private float[] sampleCardTone(){
        if(reference==null) return new float[]{190f,190f,190f,190f};
        int w=reference.getWidth(), h=reference.getHeight();
        float minX=1f,minY=1f,maxX=0f,maxY=0f;
        for(int i=0;i<4;i++){
            minX=Math.min(minX,card[i*2]); maxX=Math.max(maxX,card[i*2]);
            minY=Math.min(minY,card[i*2+1]); maxY=Math.max(maxY,card[i*2+1]);
        }
        // Sample the center of the physical card, away from its edge/shadow.
        float ix=(maxX-minX)*.16f, iy=(maxY-minY)*.16f;
        int x0=Math.max(0,Math.round((minX+ix)*w));
        int x1=Math.min(w-1,Math.round((maxX-ix)*w));
        int y0=Math.max(0,Math.round((minY+iy)*h));
        int y1=Math.min(h-1,Math.round((maxY-iy)*h));
        int step=Math.max(2,Math.min(w,h)/220);
        long sr=0,sg=0,sb=0,count=0;
        for(int y=y0;y<=y1;y+=step){
            for(int x=x0;x<=x1;x+=step){
                int c=reference.getPixel(x,y);
                int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
                float lum=.2126f*r+.7152f*g+.0722f*b;
                int chroma=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));
                if(lum<95f || lum>248f || chroma>55) continue;
                sr+=r;sg+=g;sb+=b;count++;
            }
        }
        if(count<20) return new float[]{190f,190f,190f,190f};
        float r=sr/(float)count,g=sg/(float)count,b=sb/(float)count;
        float lum=.2126f*r+.7152f*g+.0722f*b;
        return new float[]{r,g,b,lum};
    }

    private void rebuildUvOverlay(){
        if(designOriginal==null)return;
        int w=designOriginal.getWidth(),h=designOriginal.getHeight();
        int[] src=new int[w*h];designOriginal.getPixels(src,0,w,0,0,w,h);
        byte[] mask=new byte[w*h];
        int[] integral=new int[(w+1)*(h+1)];

        for(int y=0;y<h;y++){
            int row=0;
            for(int x=0;x<w;x++){
                int c=src[y*w+x],r=Color.red(c),g=Color.green(c),b=Color.blue(c),a=Color.alpha(c);
                int lum=Math.round(.2126f*r+.7152f*g+.0722f*b);
                row+=lum;
                integral[(y+1)*(w+1)+(x+1)]=integral[y*(w+1)+(x+1)]+row;
                int chroma=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));
                if(a>20&&lum<threshold&&chroma<42)mask[y*w+x]=(byte)255;
            }
        }

        if(strictText){
            int rad=5;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int idx=y*w+x;
                if((mask[idx]&255)==0)continue;
                int x0=Math.max(0,x-rad),x1=Math.min(w-1,x+rad),y0=Math.max(0,y-rad),y1=Math.min(h-1,y+rad);
                int sum=rectSum(integral,w+1,x0,y0,x1+1,y1+1);
                int cnt=(x1-x0+1)*(y1-y0+1);
                if(sum/(float)cnt<145f)mask[idx]=0;
            }
        }

        int feather=Math.round(featherPct/10f);
        if(feather>0) mask=dilate(mask,w,h,Math.min(2,feather));

        int[] maskPreview=new int[w*h];
        for(int i=0;i<mask.length;i++){
            int m=mask[i]&255;
            if(m>0) maskPreview[i]=Color.argb(Math.min(190,m),255,35,35);
        }
        textMaskOverlay=Bitmap.createBitmap(maskPreview,w,h,Bitmap.Config.ARGB_8888);

        int[] out=new int[w*h];
        int sh=Math.max(1,emboss);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int m=mask[y*w+x]&255;
            if(m==0)continue;
            int ml=getMask(mask,w,h,x-sh,y-sh),md=getMask(mask,w,h,x+sh,y+sh);
            float hi=Math.max(0,(m-ml)/255f),sd=Math.max(0,(m-md)/255f);
            float grain=(pseudo(x,y)-.5f)*(roughPct/100f);
            int alphaHi=Math.round(255*hi*(glossPct/100f)*(.9f+grain*.2f));
            int alphaSd=Math.round(110*sd);
            if(alphaHi>=alphaSd) out[y*w+x]=Color.argb(alphaHi,255,255,255);
            else out[y*w+x]=Color.argb(alphaSd,0,0,0);
        }
        uvOverlay=Bitmap.createBitmap(out,w,h,Bitmap.Config.ARGB_8888);
    }

    private static float clamp01(float v){ return Math.max(0f,Math.min(1f,v)); }
    private static float clamp(float v,float lo,float hi){ return Math.max(lo,Math.min(hi,v)); }
    private static int clamp255(int v){ return Math.max(0,Math.min(255,v)); }
    private static int rectSum(int[] in,int stride,int x0,int y0,int x1,int y1){return in[y1*stride+x1]-in[y0*stride+x1]-in[y1*stride+x0]+in[y0*stride+x0];}
    private static byte[] dilate(byte[] src,int w,int h,int r){
        byte[] o=src.clone();
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            if((src[y*w+x]&255)==0)continue;
            for(int yy=Math.max(0,y-r);yy<=Math.min(h-1,y+r);yy++)
                for(int xx=Math.max(0,x-r);xx<=Math.min(w-1,x+r);xx++)o[yy*w+xx]=(byte)255;
        }
        return o;
    }
    private static int getMask(byte[] m,int w,int h,int x,int y){if(x<0||y<0||x>=w||y>=h)return 0;return m[y*w+x]&255;}
    private static float pseudo(int x,int y){double s=Math.sin(x*12.9898+y*78.233)*43758.5453;return (float)(s-Math.floor(s));}

    private static void computeFitCenter(int sw,int sh,int vw,int vh,RectF out){
        float s=Math.min(vw/(float)sw,vh/(float)sh);
        float w=sw*s,h=sh*s;
        out.set((vw-w)/2f,(vh-h)/2f,(vw+w)/2f,(vh+h)/2f);
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    private static float dist(float x1,float y1,float x2,float y2){return (float)Math.hypot(x2-x1,y2-y1);}
}
