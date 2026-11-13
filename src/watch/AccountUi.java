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
    /** Build script sets this false in the public flavor. */
    public static final boolean TEST_MENU_ENABLED = true;
    private final java.util.Map<Activity,VehicleStatusUi> status=new java.util.WeakHashMap<>();
    private final java.util.Map<Activity,TestDataMenu> testMenus=new java.util.WeakHashMap<>();
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
        // Verify the exact original gauge group before hiding it. Controls are siblings.
        View gauges=root.findViewById(0x7f090113);
        TextView battery=root.findViewById(0x7f090210),range=root.findViewById(0x7f090215);
        View batteryArc=root.findViewById(0x7f090175),rangeArc=root.findViewById(0x7f090178);
        if(!(gauges instanceof LinearLayout)||battery==null||range==null||batteryArc==null||rangeArc==null||gauges.findViewById(battery.getId())!=battery||gauges.findViewById(range.getId())!=range) {if(attempt<30)scheduleAttach(a,attempt+1,250);return;}
        constrainTitle(a,root.findViewById(0x7f090217));
        gauges.setVisibility(View.GONE);
        View content=scroll.getChildAt(0);scroll.removeView(content);
        content.setPadding(content.getPaddingLeft(),content.getPaddingTop(),content.getPaddingRight(),0);
        scroll.setPadding(scroll.getPaddingLeft(),scroll.getPaddingTop(),scroll.getPaddingRight(),dp(a,40));
        LinearLayout column=new LinearLayout(a);column.setOrientation(LinearLayout.VERTICAL);column.setGravity(Gravity.CENTER_HORIZONTAL);
        VehicleDashboard dashboard=new VehicleDashboard(a,battery,range);
        column.addView(dashboard,new LinearLayout.LayoutParams(-1,-2));
        column.addView(content,new LinearLayout.LayoutParams(-1,-2));
        VehicleStatusUi card=new VehicleStatusUi(a);card.setDashboard(dashboard);status.put(a,card);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(a,160),dp(a,44));lp.setMargins(0,dp(a,8),0,0);column.addView(entry(a),lp);
        if(TEST_MENU_ENABLED) {
            Button tests=button(a,"测试数据");tests.setTag("ninebot.testdata.entry");tests.setTextColor(0xff83ddff);tests.setOnClickListener(v->showTestMenu(a));
            LinearLayout.LayoutParams testLp=new LinearLayout.LayoutParams(dp(a,164),dp(a,44));testLp.setMargins(0,dp(a,8),0,dp(a,12));column.addView(tests,testLp);
        }
        scroll.addView(column,new ViewGroup.LayoutParams(-1,-2));
        card.resume();
    }
    private void showTestMenu(Activity a) {
        if(a.isFinishing()||a.isDestroyed())return;
        TestDataMenu current=testMenus.get(a);
        if(current!=null&&current.dialog.isShowing()){current.showMenu();return;}
        VehicleStatusUi card=status.get(a);if(card==null)return;
        TestDataMenu menu=new TestDataMenu(a,card);testMenus.put(a,menu);menu.showMenu();
    }
    private final class TestDataMenu {
        final Activity activity;
        final VehicleStatusUi card;
        final Dialog dialog;
        final LinearLayout page;
        final ScrollView scroll;
        boolean statusPage;
        boolean cleaned;

        TestDataMenu(Activity activity, VehicleStatusUi card) {
            this.activity = activity;
            this.card = card;
            dialog = new Dialog(activity) {
                @Override public void onBackPressed() {
                    if (statusPage) showMenu();
                    else super.onBackPressed();
                }
            };
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setOnDismissListener(ignored -> cleanup());

            page = new LinearLayout(activity);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setGravity(Gravity.CENTER_HORIZONTAL);
            page.setPadding(dp(activity, 38), dp(activity, 28), dp(activity, 38), dp(activity, 28));
            scroll = new ScrollView(activity);
            scroll.setFillViewport(true);
            scroll.setBackgroundColor(Color.BLACK);
            scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
            dialog.setContentView(scroll);
        }

        void showMenu() {
            statusPage = false;
            page.removeAllViews();
            page.setGravity(Gravity.CENTER);
            TextView title = new TextView(activity);
            title.setText("测试数据");
            title.setTextSize(18);
            title.setTextColor(Color.WHITE);
            title.setGravity(Gravity.CENTER);
            page.addView(title, new LinearLayout.LayoutParams(-1, -2));
            addButton("车辆状态测试", "ninebot.testdata.status", 44, 8, this::showStatus);
            addButton("速度测试", "ninebot.testdata.speed", 44, 6,
                () -> VehicleSpeedActivity.open(activity));
            addButton("返回车辆页", "ninebot.testdata.close", 40, 8, dialog::dismiss);

            if (!dialog.isShowing()) {
                Window window = dialog.getWindow();
                window.setBackgroundDrawableResource(android.R.color.black);
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                dialog.show();
                window.setLayout(-1, -1);
                CrownScroll.install(window, activity);
            }
            resetScroll();
        }

        void showStatus() {
            statusPage = true;
            page.removeAllViews();
            page.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            addButton("返回测试数据", "ninebot.testdata.back", 44, 0, this::showMenu);
            LinearLayout.LayoutParams cardLayout = new LinearLayout.LayoutParams(-1, -2);
            cardLayout.topMargin = dp(activity, 8);
            page.addView(card.view, cardLayout);
            resetScroll();
        }

        void addButton(String label, String tag, int height, int topMargin, Runnable action) {
            Button button = button(activity, label);
            button.setTag(tag);
            button.setOnClickListener(ignored -> action.run());
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, dp(activity, height));
            layout.topMargin = dp(activity, topMargin);
            page.addView(button, layout);
        }

        void resetScroll() {
            scroll.scrollTo(0, 0);
            scroll.post(() -> scroll.scrollTo(0, 0));
        }

        void cleanup() {
            if (cleaned) return;
            cleaned = true;
            CrownScroll.uninstall(dialog.getWindow());
            if (card.view.getParent() == page) page.removeView(card.view);
            if (testMenus.get(activity) == this) testMenus.remove(activity);
        }

        void dismiss() {
            if (dialog.isShowing()) dialog.dismiss();
            cleanup();
        }
    }
    private void constrainTitle(Activity a,View v) {
        if(v instanceof TextView){TextView t=(TextView)v;t.setSingleLine(true);t.setMaxWidth(dp(a,160));t.setEllipsize(android.text.TextUtils.TruncateAt.END);}
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)constrainTitle(a,((ViewGroup)v).getChildAt(i));
    }
    private ViewGroup findScroll(View v) {
        if(v.getClass().getName().endsWith("NestedScrollView"))return (ViewGroup)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){ViewGroup r=findScroll(((ViewGroup)v).getChildAt(i));if(r!=null)return r;}
        return null;
    }
    public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){}
    public void onActivityPaused(Activity a){resumed.remove(a);Runnable task=attachments.remove(a);if(task!=null)a.getWindow().getDecorView().removeCallbacks(task);VehicleStatusUi card=status.get(a);if(card!=null)card.pause();CrownScroll.uninstall(a);}
    public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){}
    public void onActivityDestroyed(Activity a){onActivityPaused(a);TestDataMenu menu=testMenus.remove(a);if(menu!=null)menu.dismiss();status.remove(a);}
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
