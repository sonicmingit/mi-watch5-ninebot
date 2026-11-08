package local.ninebot.account;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.*;
import java.lang.reflect.*;
import java.util.ArrayList;

/** Only two read-only calls through the existing authenticated Retrofit/Rx client. */
public final class VehicleStatusUi {
    public static final String TAG="ninebot.vehicle.status";
    public static final String REFRESH_TAG="ninebot.vehicle.status.refresh";
    private final Activity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<Object> disposables=new ArrayList<>();
    private ReadOnlyStatusRaw.Reader rawReader;
    private Thread rawWorker;
    private final TextView body;
    private final Button refresh;
    private boolean active;
    private int generation,pending;
    private long queried;
    private String location="速度：未知\n电源码：未知",engine="ACC：未知\n锁状态：未知\n车辆上报：未知";
    private String locState="未查询",engineState="未查询";
    private final Runnable timeout=()->{if(pending>0){cancelRequests();pending=0;generation++;locState="查询超时 / 旧值";engineState="查询超时 / 旧值";render();}};
    public final LinearLayout view;
    public VehicleStatusUi(Activity a) {
        activity=a;view=new LinearLayout(a);view.setOrientation(1);view.setTag(TAG);view.setPadding(AccountUi.dp(a,12),AccountUi.dp(a,12),AccountUi.dp(a,12),AccountUi.dp(a,12));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff191919);bg.setCornerRadius(AccountUi.dp(a,12));view.setBackground(bg);
        TextView title=new TextView(a);title.setText("车辆状态 · 测试");title.setTextSize(16);title.setTextColor(Color.WHITE);title.setGravity(Gravity.CENTER);view.addView(title,new LinearLayout.LayoutParams(-1,-2));
        body=new TextView(a);body.setTextSize(12);body.setTextColor(0xffdddddd);body.setPadding(0,AccountUi.dp(a,8),0,AccountUi.dp(a,8));body.setLineSpacing(AccountUi.dp(a,3),1);view.addView(body,new LinearLayout.LayoutParams(-1,-2));
        refresh=AccountUi.button(a,"刷新状态");refresh.setTag(REFRESH_TAG);refresh.setTextSize(14);refresh.setOnClickListener(v->refresh());view.addView(refresh,new LinearLayout.LayoutParams(-1,AccountUi.dp(a,44)));render();
    }
    public void resume(){active=true;refresh();}
    public void pause(){active=false;generation++;pending=0;main.removeCallbacksAndMessages(null);cancelRequests();}
    private static Object call(Object o,String name)throws Exception{return o.getClass().getMethod(name).invoke(o);}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void cancelRequests(){
        if(rawReader!=null){rawReader.cancel();rawReader=null;}
        if(rawWorker!=null){rawWorker.interrupt();rawWorker=null;}
        synchronized(disposables){for(Object d:disposables)try{Class.forName("io.reactivex.disposables.Disposable").getMethod("dispose").invoke(d);}catch(Exception ignored){}disposables.clear();}
    }
    public void refresh() {
        if(!active||pending>0)return;
        cancelRequests();int id=++generation;pending=2;queried=System.currentTimeMillis();locState="查询中 / 旧值";engineState="查询中 / 旧值";render();main.postDelayed(timeout,20000);
        try {
            Object presenter=field(activity,"mPresenter"),source=field(presenter,"mApiSource"),bean=field(presenter,"mDeviceBean");
            int type=((Number)call(bean,"getVehicleType")).intValue();String number=(String)call(bean,"getWnumber");
            readLocation(source,bean,id);
            Object service=call(source,"getService");
            Class<?> api=Class.forName("com.ninebot.ninebot.device.motor.network.MotorApiService");
            subscribe(api.getMethod("getEngineStatus",int.class,String.class).invoke(service,type,number),true,id);
        }catch(Exception e){if(active&&id==generation){generation++;pending=0;cancelRequests();main.removeCallbacks(timeout);locState="读取失败 / 旧值";engineState="读取失败 / 旧值";render();}}
    }
    private void readLocation(Object source,Object bean,int id) {
        ReadOnlyStatusRaw.Reader reader=new ReadOnlyStatusRaw.Reader();rawReader=reader;
        rawWorker=new Thread(()->{
            try {
                ReadOnlyStatusRaw.Location result=reader.read(source,bean);
                main.post(()->{if(!active||id!=generation)return;rawReader=null;rawWorker=null;location=result.text;locState="已查询（原始字段）";done();});
            }catch(Exception ignored){
                main.post(()->{if(!active||id!=generation)return;rawReader=null;rawWorker=null;fail(false,"读取失败 / 旧值");done();});
            }
        },"ninebot-read-only-status");rawWorker.start();
    }
    private void subscribe(Object stream,boolean power,int id)throws Exception {
        Class<?> observable=Class.forName("io.reactivex.Observable"),scheduler=Class.forName("io.reactivex.Scheduler"),observer=Class.forName("io.reactivex.Observer");
        Object io=Class.forName("io.reactivex.schedulers.Schedulers").getMethod("io").invoke(null);
        Object ui=Class.forName("io.reactivex.android.schedulers.AndroidSchedulers").getMethod("mainThread").invoke(null);
        stream=observable.getMethod("subscribeOn",scheduler).invoke(stream,io);stream=observable.getMethod("observeOn",scheduler).invoke(stream,ui);
        final boolean[] delivered={false};
        Object listener=Proxy.newProxyInstance(observer.getClassLoader(),new Class<?>[]{observer},(p,m,args)->{
            String n=m.getName();
            if(n.equals("hashCode"))return System.identityHashCode(p);
            if(n.equals("equals"))return p==args[0];
            if(n.equals("toString"))return "ReadOnlyStatusObserver";
            if(n.equals("onSubscribe")){
                Object d=args[0];synchronized(disposables){if(active&&id==generation)disposables.add(d);else Class.forName("io.reactivex.disposables.Disposable").getMethod("dispose").invoke(d);}return null;
            }
            if(!active||id!=generation)return null;
            if(n.equals("onNext")) {delivered[0]=true;accept(args[0],power);}
            else if(n.equals("onError") || (n.equals("onComplete")&&!delivered[0])){fail(power,"读取失败 / 旧值");done();}
            return null;
        });
        observable.getMethod("subscribe",observer).invoke(stream,listener);
    }
    private void accept(Object response,boolean power) {
        try {
            Object codes=Class.forName("com.ninebot.ninebot.nbnet.NbResponseCode").getField("INSTANCE").get(null);
            Object success=call(codes,"getSUCCESS"),code=call(response,"getCode");
            if(!success.equals(code)) {
                Object invalid=call(codes,"getERROR_WATCH_TOKEN_INVALID");
                fail(power,invalid.equals(code)?"授权失效 / 旧值":"接口拒绝 / 旧值");
            }else{
                Object data=call(response,"getData");if(data==null)throw new IllegalStateException();
                long now=System.currentTimeMillis();
                if(power){engine="最近上报\n电源："+StatusFormat.acc((String)call(data,"getAccStatus"))+"\n锁状态："+StatusFormat.lock((String)call(data,"getLockStatus"))+"\n车辆上报："+StatusFormat.report((String)call(data,"getReportTime"),now);engineState="已查询";}
            }
        }catch(Exception e){fail(power,"解析失败 / 旧值");}
        done();
    }
    private void fail(boolean power,String text){if(power)engineState=text;else locState=text;}
    private void done(){if(pending>0)pending--;if(pending==0)main.removeCallbacks(timeout);render();}
    private void render(){body.setText(location+"\n"+locState+"\n\n"+engine+"\n"+engineState+"\n\n查询："+StatusFormat.clock(queried)+"\n仅供参考；上电不等于行驶\n速度单位与状态码待实车核对");refresh.setEnabled(pending==0);refresh.setText(pending>0?"正在查询…":"刷新状态");}
}
