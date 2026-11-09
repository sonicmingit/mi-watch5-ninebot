package local.ninebot.account;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** Local UI additions only; no vehicle commands or credential export. */
public class AccountUi extends ContentProvider implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "ninebot.logout.entry";
    private final java.util.Map<Activity,VehicleStatusUi> status=new java.util.WeakHashMap<>();
    private final java.util.Map<Activity,Runnable> attachments=new java.util.WeakHashMap<>();
    private final java.util.Set<Activity> resumed=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Activity,Boolean>());
    public boolean onCreate() {
        ((Application)getContext().getApplicationContext()).registerActivityLifecycleCallbacks(this);
        return true;
    }
    static int dp(Context c,int n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    public static boolean clearCredentials(Context c) {
        return c.getSharedPreferences("com.ninebot.ninebot",0).edit().remove("token").remove("uid").commit();
    }
    static Button button(Context c,String text) {
        Button b=new Button(c); b.setText(text); b.setTextSize(16); b.setTextColor(Color.WHITE);
        b.setAllCaps(false); b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(8,0,8,0);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff262626);bg.setCornerRadius(dp(c,24));b.setBackground(bg);
        return b;
    }
    private Button entry(Activity a) {
        Button b=button(a,"退出登录");b.setTag(TAG);
        b.setOnClickListener(v->a.startActivity(new Intent(a,ConfirmActivity.class)));
        return b;
    }
    public void onActivityResumed(Activity a) {
        resumed.add(a);CrownScroll.install(a);
        if(a.getClass().getName().equals("com.ninebot.ninebot.MainActivity") &&
            a.getSharedPreferences("com.ninebot.ninebot",0).getString("token","").isEmpty()) {
            a.startActivity(new Intent().setClassName(a,"local.ninebot.bridge.ProbeActivity"));
            return;
        }
        if(a.getClass().getName().equals("com.ninebot.ninebot.DeviceListActivity")) {
            ListView list=a.findViewById(0x7f090115);
            if(list!=null && list.findViewWithTag(TAG)==null && list.getFooterViewsCount()==0) {
                LinearLayout footer=new LinearLayout(a);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(a,8),0,dp(a,12));
                footer.addView(entry(a),new LinearLayout.LayoutParams(dp(a,160),dp(a,44)));list.addFooterView(footer,null,false);
            }
        }
        if(a.getClass().getName().equals("com.ninebot.ninebot.DeviceDetailActivity")) {
            // The original content arrives asynchronously after the vehicle request.
            View root=a.getWindow().getDecorView();
            VehicleStatusUi existing=status.get(a);
            if(existing!=null)existing.resume();
            else scheduleAttach(a,0,200);
        }
    }
    private void scheduleAttach(Activity a,int attempt,long delay) {
        View root=a.getWindow().getDecorView();Runnable old=attachments.remove(a);if(old!=null)root.removeCallbacks(old);
        Runnable task=()->{attachments.remove(a);attachDetail(a,attempt);};attachments.put(a,task);root.postDelayed(task,delay);
    }
    private void attachDetail(Activity a,int attempt) {
        if(a.isDestroyed()||a.isFinishing()||!resumed.contains(a))return;
        View root=a.getWindow().getDecorView();if(root.findViewWithTag(TAG)!=null)return;
        ViewGroup scroll=findScroll(root);
        if(scroll==null || scroll.getChildCount()==0) { if(attempt<30)scheduleAttach(a,attempt+1,250);return; }
        View content=scroll.getChildAt(0);scroll.removeView(content);
        content.setPadding(content.getPaddingLeft(),content.getPaddingTop(),content.getPaddingRight(),0);
        scroll.setPadding(scroll.getPaddingLeft(),scroll.getPaddingTop(),scroll.getPaddingRight(),dp(a,40));
        LinearLayout column=new LinearLayout(a);column.setOrientation(LinearLayout.VERTICAL);column.setGravity(Gravity.CENTER_HORIZONTAL);
        Button speed=button(a,"速度测试");speed.setTag("ninebot.speed.entry");speed.setTextColor(0xff83ddff);speed.setOnClickListener(v->VehicleSpeedActivity.open(a));
        LinearLayout.LayoutParams speedLp=new LinearLayout.LayoutParams(dp(a,164),dp(a,44));speedLp.setMargins(0,dp(a,8),0,dp(a,10));column.addView(speed,speedLp);
        column.addView(content,new LinearLayout.LayoutParams(-1,-2));
        VehicleStatusUi card=new VehicleStatusUi(a);status.put(a,card);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(a,184),-2);cp.setMargins(0,dp(a,12),0,dp(a,8));column.addView(card.view,cp);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(a,160),dp(a,44));lp.setMargins(0,dp(a,8),0,0);column.addView(entry(a),lp);
        scroll.addView(column,new ViewGroup.LayoutParams(-1,-2));
        card.resume();
    }
    private ViewGroup findScroll(View v) {
        if(v.getClass().getName().endsWith("NestedScrollView"))return (ViewGroup)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){ViewGroup r=findScroll(((ViewGroup)v).getChildAt(i));if(r!=null)return r;}
        return null;
    }
    public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){}
    public void onActivityPaused(Activity a){resumed.remove(a);Runnable task=attachments.remove(a);if(task!=null)a.getWindow().getDecorView().removeCallbacks(task);VehicleStatusUi card=status.get(a);if(card!=null)card.pause();CrownScroll.uninstall(a);}
    public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){}
    public void onActivityDestroyed(Activity a){onActivityPaused(a);status.remove(a);}
    public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;} public String getType(Uri u){return null;}
    public Uri insert(Uri u,ContentValues v){return null;} public int delete(Uri u,String s,String[] a){return 0;}public int update(Uri u,ContentValues v,String s,String[] a){return 0;}

    public static class ConfirmActivity extends Activity {
        protected void onCreate(Bundle state) {
            super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.BLACK);
            LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setGravity(Gravity.CENTER);box.setPadding(dp(this,32),dp(this,26),dp(this,32),dp(this,28));
            TextView title=new TextView(this);title.setText("退出登录？");title.setTextSize(19);title.setTextColor(Color.WHITE);title.setGravity(17);box.addView(title,new LinearLayout.LayoutParams(-1,-2));
            TextView body=new TextView(this);body.setText("退出后将关闭应用。\n重新打开后，可通过\n手机授权助手再次登录。");body.setTextSize(13);body.setTextColor(0xffbbbbbb);body.setGravity(17);body.setPadding(0,dp(this,8),0,dp(this,12));box.addView(body,new LinearLayout.LayoutParams(-1,-2));
            Button cancel=button(this,"取消");cancel.setOnClickListener(v->finish());box.addView(cancel,new LinearLayout.LayoutParams(-1,dp(this,40)));
            Button confirm=button(this,"确认退出");confirm.setTextColor(0xffffa195);confirm.setOnClickListener(v->{
                // End this process so the original SDK cannot reuse cached account state.
                boolean saved=clearCredentials(this);
                if(!saved){Toast.makeText(this,"退出失败，请重试",Toast.LENGTH_SHORT).show();return;}
                finishAffinity();android.os.Process.killProcess(android.os.Process.myPid());
            });LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(this,40));lp.topMargin=dp(this,6);box.addView(confirm,lp);
            scroll.addView(box,new ScrollView.LayoutParams(-1,-1));setContentView(scroll);
        }
    }
}
