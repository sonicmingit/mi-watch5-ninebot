package local.ninebot.account;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.*;
import android.view.View;
import android.widget.TextView;

/** Native round-screen dashboard. Values remain owned and updated by the original SDK. */
public final class VehicleDashboard extends View {
    private final Paint paint=new Paint(3);
    private final Drawable batteryIcon,rangeIcon,lockIcon;
    private final TextView battery,range;
    private HomeStatus.State state=HomeStatus.State.UNKNOWN;
    private Double speed;
    private String notice="查询中…";
    private final TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){updateDescription();invalidate();}public void afterTextChanged(Editable e){}};
    public VehicleDashboard(Activity a,TextView b,TextView r) {
        super(a);battery=b;range=r;setTag("ninebot.home.dashboard");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        batteryIcon=a.getDrawable(0x7f0800e3);rangeIcon=a.getDrawable(0x7f0800e9);lockIcon=a.getDrawable(android.R.drawable.ic_lock_lock);
        updateDescription();
    }
    protected void onAttachedToWindow(){super.onAttachedToWindow();battery.addTextChangedListener(watcher);range.addTextChangedListener(watcher);}
    protected void onDetachedFromWindow(){battery.removeTextChangedListener(watcher);range.removeTextChangedListener(watcher);super.onDetachedFromWindow();}
    public void show(HomeStatus.State value,Double number,String message){state=value;speed=number;notice=message;updateDescription();invalidate();}
    private void updateDescription(){setContentDescription(state.label+(state==HomeStatus.State.DRIVING?"，速度 "+StatusFormat.speedValueText(speed):"")+"，剩余电量 "+metric(battery)+"，剩余里程 "+metric(range)+(notice.isEmpty()?"":"，"+notice));}
    private static String metric(TextView v){String s=v.getText().toString();return s.matches("[0-9]+(?:\\.[0-9]+)?\\s*(?:%|km|公里)")?s:"--";}
    private static Double number(TextView v){String s=metric(v);if(s.equals("--"))return null;return StatusFormat.finiteNumber(s.replaceAll("[^0-9.]",""));}
    protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w);setMeasuredDimension(width,Math.round(width*210f/240f));}
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/240f,getWidth()/240f);
        boolean driving=state==HomeStatus.State.DRIVING;
        float fs=Math.min(1.3f,Math.max(1,getResources().getConfiguration().fontScale));
        float labelSize=19*fs;
        paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(labelSize);
        float textWidth=paint.measureText(state.label),start=(240-textWidth)/2;
        text(canvas,state.label,driving?132:128,28,labelSize,Color.WHITE,true,184);
        if(state==HomeStatus.State.PARKED){lockIcon.setTint(0xff55ed27);icon(canvas,lockIcon,start-24,4,22,26);}
        if(driving){paint.setColor(0xff06d2ed);paint.setStrokeWidth(2);paint.setStrokeCap(Paint.Cap.ROUND);for(int i=0;i<3;i++)canvas.drawLine(start-25+i*3,16+i*5,start-10+i*3,16+i*5,paint);text(canvas,StatusFormat.speedValueText(speed),120,76,52*fs,Color.WHITE,true,184);}
        float y=driving?124:92,r=driving?27:46;
        gauge(canvas,driving?81:65,y,r,battery,true,fs);gauge(canvas,driving?159:175,y,r,range,false,fs);
        if(!notice.isEmpty())text(canvas,notice,120,194,10*fs,0xffbbbbbb,false,150);
        canvas.restore();
    }
    private void gauge(Canvas c,float x,float y,float r,TextView source,boolean isBattery,float fs){
        Double n=number(source);int color=isBattery?0xff53ef26:0xff00c9f3;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(6);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(0xff242424);
        RectF oval=new RectF(x-r,y-r,x+r,y+r);c.drawArc(oval,135,270,false,paint);
        Double level=number(battery);
        // Both original SDK arcs use battery level; never sample their animation frame.
        if(n!=null&&level!=null){paint.setColor(color);float percentage=(float)Math.max(0,Math.min(100,level));c.drawArc(oval,135,270*percentage/100,false,paint);}
        paint.setStyle(Paint.Style.FILL);
        text(c,metric(source),x,y+4,(r<35?16:22)*fs,Color.WHITE,true,r*1.7f);
        icon(c,isBattery?batteryIcon:rangeIcon,x-9,y+r*.50f,18,12);
        text(c,isBattery?"剩余电量":"剩余里程",x,y+r+18,(r<35?10:12)*fs,0xffdddddd,false,r*2.1f);
    }
    private void text(Canvas c,String value,float x,float y,float size,int color,boolean bold,float max){paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));paint.setTextSize(size);float w=paint.measureText(value);if(w>max)paint.setTextSize(size*max/w);paint.setColor(color);paint.setTextAlign(Paint.Align.CENTER);c.drawText(value,x,y,paint);}
    private void icon(Canvas c,Drawable d,float x,float y,float w,float h){d.setBounds((int)x,(int)y,(int)(x+w),(int)(y+h));d.draw(c);}
}
