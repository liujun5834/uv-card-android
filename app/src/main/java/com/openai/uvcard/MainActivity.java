package com.openai.uvcard;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;

import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int PICK_REF=1001, PICK_DESIGN=1002;
    private EditorView editor;
    private TextView status;
    private Button maskBtn;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);

        ScrollView sc=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(18));
        sc.addView(root);

        TextView title=new TextView(this);
        title.setText("UV卡片实拍合成器 · V9.1");
        title.setTextSize(21);
        title.setPadding(0,0,0,dp(4));
        root.addView(title);

        TextView tip=new TextView(this);
        tip.setText("V9.1：按已确认样张更新人物融合——自动读取实物卡底色做轻量调色，默认让肤色稍暗；头像边缘羽化更窄、更清晰；保留V9的紧贴图片微调工具和左右旋转1°。");
        tip.setTextSize(13);
        root.addView(tip);

        LinearLayout pickRow=row();
        Button refBtn=btn("换实物卡照片");
        Button designBtn=btn("上传印刷图");
        pickRow.addView(refBtn,weight());
        pickRow.addView(designBtn,weight());
        root.addView(pickRow);

        editor=new EditorView(this);
        root.addView(editor,new LinearLayout.LayoutParams(-1,dp(465)));

        // V9: keep all direct-manipulation controls immediately adjacent to the preview.
        TextView toolTitle=heading("图片微调工具");
        toolTitle.setPadding(0,dp(6),0,dp(2));
        root.addView(toolTitle);
        TextView toolDesc=new TextView(this);
        toolDesc.setText("只调整印上去的图片，不改变实物卡照片。点击微调；长按可连续调整。");
        toolDesc.setTextSize(12);
        root.addView(toolDesc);

        LinearLayout moveRow=row();
        Button left=btn("左移 ←"); Button up=btn("上移 ↑"); Button down=btn("下移 ↓"); Button right=btn("右移 →");
        moveRow.addView(left,weight()); moveRow.addView(up,weight()); moveRow.addView(down,weight()); moveRow.addView(right,weight());
        root.addView(moveRow);

        LinearLayout sizeRotateRow=row();
        Button plus=btn("放大 +"); Button minus=btn("缩小 -"); Button rotL=btn("左旋 1°"); Button rotR=btn("右旋 1°");
        sizeRotateRow.addView(plus,weight()); sizeRotateRow.addView(minus,weight()); sizeRotateRow.addView(rotL,weight()); sizeRotateRow.addView(rotR,weight());
        root.addView(sizeRotateRow);

        LinearLayout e1=row();
        Button te=btn("上边拉升"); Button ts=btn("上边缩回"); Button be=btn("下边拉升"); Button bs=btn("下边缩回");
        e1.addView(te,weight()); e1.addView(ts,weight()); e1.addView(be,weight()); e1.addView(bs,weight());
        root.addView(e1);

        LinearLayout e2=row();
        Button le=btn("左边拉升"); Button ls=btn("左边缩回"); Button re=btn("右边拉升"); Button rs=btn("右边缩回");
        e2.addView(le,weight()); e2.addView(ls,weight()); e2.addView(re,weight()); e2.addView(rs,weight());
        root.addView(e2);

        Button resetT=btn("重置位移 / 拉伸 / 旋转");
        root.addView(resetT);

        status=new TextView(this);
        status.setText("先上传要印上去的图片，然后点“一键铺满卡片并居中”。");
        status.setPadding(0,dp(7),0,dp(8));
        root.addView(status);

        root.addView(heading("卡片契合"));
        LinearLayout fitRow=row();
        Button fit=btn("一键铺满卡片并居中"); Button contain=btn("完整显示并居中");
        fitRow.addView(fit,weight()); fitRow.addView(contain,weight()); root.addView(fitRow);
        LinearLayout fit2=row();
        Button auto=btn("自动识别卡片"); Button resetC=btn("恢复卡片四角");
        fit2.addView(auto,weight()); fit2.addView(resetC,weight()); root.addView(fit2);

        CheckBox corners=new CheckBox(this);
        corners.setText("显示四角定位点"); corners.setChecked(true); root.addView(corners);

        Spinner fitMode=new Spinner(this);
        fitMode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"铺满卡片并居中","拉伸铺满卡片","完整显示并居中"}));
        root.addView(fitMode);

        addSeek(root,"真实圆角",0,12,5,v->editor.setRoundPct(v/100f));
        addSeek(root,"边缘出血",0,7,3,v->editor.setBleedPct((v-3)/400f));

        root.addView(heading("印刷融合 / 色彩保护"));
        addSeek(root,"印刷浓度",0,100,92,editor::setInkPct);
        addSeek(root,"实物卡纹理保留",0,100,90,editor::setTexturePct);
        addSeek(root,"白底保护",70,100,96,editor::setWhiteProtectPct);
        CheckBox colorProtect=new CheckBox(this);
        colorProtect.setText("人物/彩色区域保护（避免头像发灰、偏色）");
        colorProtect.setChecked(true);
        root.addView(colorProtect);
        TextView colorTip=new TextView(this);
        colorTip.setText("开启后，白底保护只处理接近中性白/灰的区域，不再把亮肤色、头像、印章和彩色高光误当成白底透明化。");
        colorTip.setTextSize(12);
        root.addView(colorTip);

        CheckBox autoTone=new CheckBox(this);
        autoTone.setText("根据实物卡底图自动调色（推荐）");
        autoTone.setChecked(true);
        root.addView(autoTone);
        addSeek(root,"底图自动调色强度",0,100,65,editor::setAutoTonePct);
        addSeek(root,"人物肤色压暗",0,30,10,editor::setPortraitDarkPct);
        addSeek(root,"头像边缘清晰度",0,100,72,editor::setPortraitEdgeCrispPct);
        TextView portraitTip=new TextView(this);
        portraitTip.setText("本版默认值按你确认的样张设定：肤色比V9更暗，底色随实物卡自动匹配；“头像边缘清晰度”越高，白/灰边羽化越窄。不会对黑字和高饱和红章做自动调色。");
        portraitTip.setTextSize(12);
        root.addView(portraitTip);

        root.addView(heading("黑色文字 / 数字 UV"));
        CheckBox uv=new CheckBox(this);
        uv.setText("只给黑色文字/数字加UV"); uv.setChecked(true); root.addView(uv);
        CheckBox strict=new CheckBox(this);
        strict.setText("严格文字识别（减少人物/图案误识别）"); strict.setChecked(true); root.addView(strict);

        maskBtn=btn("查看文字识别区域");
        root.addView(maskBtn);

        addSeek(root,"黑字识别阈值",25,190,105,editor::setThreshold);
        addSeek(root,"UV强度",0,100,62,editor::setUvPct);
        addSeek(root,"镜面高光",0,100,68,editor::setGlossPct);
        addSeek(root,"凸起深度",1,8,3,editor::setEmboss);
        addSeek(root,"字体粗糙度",0,40,8,editor::setRoughPct);
        addSeek(root,"毛边 / 羽化",0,30,5,editor::setFeatherPct);

        root.addView(heading("输出"));
        Button save=btn("保存PNG");
        root.addView(save);

        refBtn.setOnClickListener(v->pickImage(PICK_REF));
        designBtn.setOnClickListener(v->pickImage(PICK_DESIGN));
        fit.setOnClickListener(v->{editor.fitCardCenter();fitMode.setSelection(0);status.setText("已铺满实物卡片区域并自动居中。");});
        contain.setOnClickListener(v->{editor.fitCardContain();fitMode.setSelection(2);status.setText("已完整显示并居中到实物卡片区域。");});
        resetC.setOnClickListener(v->editor.resetCardCorners());
        auto.setOnClickListener(v->Toast.makeText(this,"当前版本先用四角拖动精确定位；自动识别后续再增强。",Toast.LENGTH_SHORT).show());
        corners.setOnCheckedChangeListener((b1,c)->editor.setShowCorners(c));

        fitMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                if(pos==0)editor.setFitMode(EditorView.FitMode.COVER);
                else if(pos==1)editor.setFitMode(EditorView.FitMode.STRETCH);
                else editor.setFitMode(EditorView.FitMode.CONTAIN);
            }
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });

        uv.setOnCheckedChangeListener((b1,c)->editor.setUvEnabled(c));
        strict.setOnCheckedChangeListener((b1,c)->editor.setStrictText(c));
        colorProtect.setOnCheckedChangeListener((b1,c)->editor.setProtectColorRegions(c));
        autoTone.setOnCheckedChangeListener((b1,c)->editor.setAutoCardTone(c));
        maskBtn.setOnClickListener(v->{
            boolean show=!editor.getShowTextMask();
            editor.setShowTextMask(show);
            maskBtn.setText(show?"返回正常效果":"查看文字识别区域");
        });
        save.setOnClickListener(v->savePng());

        final float m=.003f,eStep=.003f,sStep=.008f;
        bindRepeat(up,()->editor.move(0,-m));
        bindRepeat(down,()->editor.move(0,m));
        bindRepeat(left,()->editor.move(-m,0));
        bindRepeat(right,()->editor.move(m,0));
        bindRepeat(plus,()->editor.scaleBy(1+sStep));
        bindRepeat(minus,()->editor.scaleBy(1-sStep));
        bindRepeat(rotL,()->editor.rotateBy(-1f));
        bindRepeat(rotR,()->editor.rotateBy(1f));
        bindRepeat(te,()->editor.edgeTop(eStep));
        bindRepeat(ts,()->editor.edgeTop(-eStep));
        bindRepeat(be,()->editor.edgeBottom(eStep));
        bindRepeat(bs,()->editor.edgeBottom(-eStep));
        bindRepeat(le,()->editor.edgeLeft(eStep));
        bindRepeat(ls,()->editor.edgeLeft(-eStep));
        bindRepeat(re,()->editor.edgeRight(eStep));
        bindRepeat(rs,()->editor.edgeRight(-eStep));
        resetT.setOnClickListener(v->editor.resetTransform());

        setContentView(sc);
    }

    private void pickImage(int req){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,req);
    }

    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            Uri uri=data.getData();
            ImageDecoder.Source src=ImageDecoder.createSource(getContentResolver(),uri);
            Bitmap b=ImageDecoder.decodeBitmap(src,(decoder,info,s)->{
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
            });
            if(req==PICK_REF){
                editor.setReference(b,true);
                status.setText("已更换实物卡照片，可拖四个红点对准卡片四角。");
            }else{
                editor.setDesign(b);
                status.setText("已自动执行：铺满实物卡片区域并居中。微调工具就在图片下方，可直接移动/缩放/旋转。");
            }
        }catch(IOException ex){
            Toast.makeText(this,"图片读取失败："+ex.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private void savePng(){
        Bitmap b=editor.renderFullResolution();
        if(b==null)return;
        try{
            ContentValues cv=new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME,"UV卡片_V9_1_"+System.currentTimeMillis()+".png");
            cv.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/UVCard");
            Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv);
            if(u==null)throw new IOException("无法创建输出文件");
            try(OutputStream os=getContentResolver().openOutputStream(u)){
                b.compress(Bitmap.CompressFormat.PNG,100,os);
            }
            Toast.makeText(this,"已保存到 Pictures/UVCard",Toast.LENGTH_LONG).show();
        }catch(Exception e){
            Toast.makeText(this,"保存失败："+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private void bindRepeat(Button button, Runnable action){
        final Handler handler=new Handler(Looper.getMainLooper());
        final boolean[] repeating={false};
        final Runnable repeater=new Runnable(){
            @Override public void run(){
                if(!repeating[0])return;
                action.run();
                handler.postDelayed(this,75);
            }
        };
        button.setOnTouchListener((v,event)->{
            if(event.getAction()==MotionEvent.ACTION_DOWN){
                repeating[0]=true;
                action.run();
                handler.postDelayed(repeater,320);
                v.setPressed(true);
                return true;
            }
            if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL){
                repeating[0]=false;
                handler.removeCallbacks(repeater);
                v.setPressed(false);
                return true;
            }
            return true;
        });
    }

    private interface SeekChange{void onChange(int value);}
    private void addSeek(LinearLayout root,String name,int min,int max,int init,SeekChange cb){
        LinearLayout line=new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        TextView label=new TextView(this);label.setText(name);
        TextView value=new TextView(this);value.setText(String.valueOf(init));value.setGravity(Gravity.END);
        line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        line.addView(value,new LinearLayout.LayoutParams(dp(70),-2));
        root.addView(line);
        SeekBar s=new SeekBar(this);s.setMax(max-min);s.setProgress(init-min);root.addView(s);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean f){int v=min+p;value.setText(String.valueOf(v));cb.onChange(v);}
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
    }

    private LinearLayout row(){
        LinearLayout r=new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0,dp(3),0,dp(3));
        return r;
    }
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextSize(13);return b;}
    private LinearLayout.LayoutParams weight(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        p.setMargins(dp(2),0,dp(2),0);
        return p;
    }
    private TextView heading(String s){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(17);t.setPadding(0,dp(14),0,dp(5));return t;
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
