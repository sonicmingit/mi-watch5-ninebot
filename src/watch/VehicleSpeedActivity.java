package local.ninebot.account;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.Serializable;
import java.lang.reflect.Field;

/** Foreground-only serial observation of the existing read-only cloud speed endpoint. */
public final class VehicleSpeedActivity extends Activity {
    private static final String EXTRA="local.ninebot.speed.bean";
    private final Handler main=new Handler(Looper.getMainLooper());
    private Object bean,source;
    private boolean foreground,userPaused,inFlight;
    private int generation;
    private long lastSuccess,duration;
    private Double lastSpeed;
    private String status="等待查询",log="日志：自动保存至Download/logs/ninebot/";
    private TextView value,state,logView;
    private Button toggle;
    private ReadOnlyStatusRaw.Reader reader;
    private Thread worker;
    private StatusQueryLog record,latest;
    private final Runnable next=()->startQuery();
    private final Runnable timeout=()->{
        if(!inFlight)return;
        generation++;stopRequest("timeout");duration=20000;status=lastSpeed==null?"查询超时，尚无读数":"查询超时 · 旧值";render();schedule();
    };
    private static Object field(Object o,String name)throws Exception {
        for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException();
    }
    public static void open(Activity detail) {
        try {
            Object bean=field(field(detail,"mPresenter"),"mDeviceBean");
            if(!(bean instanceof Serializable))throw new IllegalStateException();
            Intent intent=new Intent(detail,VehicleSpeedActivity.class);intent.setExtrasClassLoader(detail.getClassLoader());intent.putExtra(EXTRA,(Serializable)bean);detail.startActivity(intent);
        }catch(Exception ignored){Toast.makeText(detail,"暂时无法打开速度测试",Toast.LENGTH_SHORT).show();}
    }
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);requestWindowFeature(Window.FEATURE_NO_TITLE);getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        userPaused=saved!=null&&saved.getBoolean("userPaused",false);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.BLACK);
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setGravity(Gravity.CENTER_HORIZONTAL);box.setPadding(AccountUi.dp(this,32),AccountUi.dp(this,26),AccountUi.dp(this,32),AccountUi.dp(this,36));
        TextView title=text("速度 · 测试",14);box.addView(title,new LinearLayout.LayoutParams(-1,-2));
        value=text("--",44);value.setTag("ninebot.speed.value");value.setTextColor(0xff83ddff);value.setAutoSizeTextTypeUniformWithConfiguration(20,44,1,android.util.TypedValue.COMPLEX_UNIT_SP);box.addView(value,new LinearLayout.LayoutParams(-1,AccountUi.dp(this,54)));
        box.addView(text("原始数值 · 单位待核对",11),new LinearLayout.LayoutParams(-1,-2));
        state=text("",11);state.setTag("ninebot.speed.state");state.setPadding(0,AccountUi.dp(this,6),0,AccountUi.dp(this,6));box.addView(state,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);
        toggle=AccountUi.button(this,"暂停");toggle.setTag("ninebot.speed.toggle");toggle.setTextSize(14);toggle.setOnClickListener(v->{userPaused=!userPaused;if(userPaused){generation++;main.removeCallbacks(next);main.removeCallbacks(timeout);stopRequest("cancelled");status="已暂停";updateAwake();render();}else{updateAwake();startQuery();}});
        actions.addView(toggle,new LinearLayout.LayoutParams(0,AccountUi.dp(this,40),1));
        Button back=AccountUi.button(this,"返回");back.setTextSize(14);back.setOnClickListener(v->finish());LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,AccountUi.dp(this,40),1);bp.leftMargin=AccountUi.dp(this,6);actions.addView(back,bp);box.addView(actions,new LinearLayout.LayoutParams(-1,-2));
        logView=text(log,10);logView.setPadding(0,AccountUi.dp(this,10),0,0);box.addView(logView,new LinearLayout.LayoutParams(-1,-2));scroll.addView(box,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
        try {
            ClassLoader loader=Class.forName("com.ninebot.ninebot.device.motor.network.bean.DeviceBean").getClassLoader();
            getIntent().setExtrasClassLoader(loader);bean=getIntent().getSerializableExtra(EXTRA);if(bean==null)throw new IllegalStateException();
            int type=((Number)bean.getClass().getMethod("getVehicleType").invoke(bean)).intValue();
            source=Class.forName("com.ninebot.ninebot.device.motor.network.MotorApiSource").getMethod("getInstance",int.class).invoke(null,type);if(source==null)throw new IllegalStateException();
        }catch(Exception ignored){status="车辆信息不可用，请返回重试";}
        render();
    }
    private TextView text(String content,int size){TextView v=new TextView(this);v.setText(content);v.setTextSize(size);v.setTextColor(0xffdddddd);v.setGravity(Gravity.CENTER);return v;}
    protected void onResume(){super.onResume();foreground=true;updateAwake();if(userPaused){status="已暂停";render();}else startQuery();}
    protected void onPause(){foreground=false;generation++;main.removeCallbacks(next);main.removeCallbacks(timeout);stopRequest("cancelled");updateAwake();super.onPause();}
    protected void onSaveInstanceState(Bundle saved){saved.putBoolean("userPaused",userPaused);super.onSaveInstanceState(saved);}
    protected void onDestroy(){foreground=false;generation++;main.removeCallbacksAndMessages(null);stopRequest("cancelled");updateAwake();if(source!=null)try{Object strategy=field(source,"mRetrofitStrategy");Class<?> listener=Class.forName("cn.ninebot.lib.network.core.RetrofitStrategy$OnChangeListener");strategy.getClass().getMethod("removeChangeListener",listener).invoke(strategy,source);}catch(Exception ignored){}source=null;bean=null;super.onDestroy();}
    private void updateAwake(){if(foreground&&!userPaused&&source!=null)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void finishLog(String outcome) {
        StatusQueryLog current=record;if(current==null)return;record=null;
        int expected=generation;
        current.finish(getApplicationContext(),outcome,result->{if(foreground&&!userPaused&&expected==generation&&latest==current){log=result;render();}});
    }
    private void stopRequest(String outcome){finishLog(outcome);if(reader!=null){reader.cancel();reader=null;}if(worker!=null){worker.interrupt();worker=null;}inFlight=false;}
    private void schedule(){if(foreground&&!userPaused&&source!=null){main.removeCallbacks(next);main.postDelayed(next,3000);}}
    private void startQuery() {
        if(!foreground||userPaused||inFlight||source==null)return;
        main.removeCallbacks(next);int id=++generation;inFlight=true;status=lastSpeed==null?"查询中…":"查询中 · 保留上次值";record=StatusQueryLog.speed();latest=record;
        ReadOnlyStatusRaw.Reader current=new ReadOnlyStatusRaw.Reader(record.location);reader=current;Object currentSource=source,currentBean=bean;long began=SystemClock.elapsedRealtime();render();main.postDelayed(timeout,20000);
        worker=new Thread(()->{
            try {
                ReadOnlyStatusRaw.Location result=current.read(currentSource,currentBean);
                main.post(()->{if(!foreground||userPaused||id!=generation)return;inFlight=false;reader=null;worker=null;main.removeCallbacks(timeout);duration=SystemClock.elapsedRealtime()-began;
                    if(result.speed!=null){lastSpeed=result.speed;lastSuccess=System.currentTimeMillis();status="查询成功";}else status=lastSpeed==null?"速度未知（字段或格式）":"速度未知 · 旧值";
                    finishLog("complete");render();schedule();});
            }catch(Exception ignored){main.post(()->{if(!foreground||userPaused||id!=generation)return;inFlight=false;reader=null;worker=null;main.removeCallbacks(timeout);duration=SystemClock.elapsedRealtime()-began;status=lastSpeed==null?"请求失败，尚无读数":"请求失败 · 旧值";finishLog("complete");render();schedule();});}
        },"ninebot-speed-observer");worker.start();
    }
    private void render(){if(value==null)return;value.setText(StatusFormat.speedValueText(lastSpeed));state.setText(status+"\n最近成功："+StatusFormat.clock(lastSuccess)+"\n耗时："+duration+"ms · 完成后3秒再查");toggle.setText(userPaused?"继续":"暂停");toggle.setEnabled(source!=null);logView.setText(log);}
}
