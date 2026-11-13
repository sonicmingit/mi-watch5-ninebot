package local.ninebot.account;

import android.app.Activity;
import android.os.Looper;
import org.json.JSONObject;
import java.lang.reflect.*;
import java.util.IdentityHashMap;

/** Narrow read-only probe. Never exports the raw response, URL, headers or vehicle identifiers. */
public final class ReadOnlyStatusRaw {
    private ReadOnlyStatusRaw(){}
    private static Object invoke(Object o,String method)throws Exception{return o.getClass().getMethod(method).invoke(o);}
    private static Object field(Object o,String name)throws Exception {
        for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException();
    }
    private static Object findCall(Object o,Class<?> type,IdentityHashMap<Object,Boolean> seen,int depth)throws Exception {
        if(o==null || depth>10 || seen.size()>128 || seen.put(o,true)!=null)return null;
        if(type.isInstance(o))return o;
        for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass()) {
            String name=c.getName();if(!name.startsWith("io.reactivex.")&&!name.startsWith("retrofit2."))continue;
            for(Field f:c.getDeclaredFields()) {
                if(Modifier.isStatic(f.getModifiers())||f.getType().isPrimitive())continue;
                try{f.setAccessible(true);Object found=findCall(f.get(o),type,seen,depth+1);if(found!=null)return found;}catch(IllegalAccessException ignored){}
            }
        }
        return null;
    }
    /** Holds only formatted status fields, never the full JSON body. */
    public static final class Location {
        public final String text;
        public final Double speed;
        public final Integer power;
        public final String speedText;
        private Location(String value,Double number,Integer code){text=value;speed=number;power=code;speedText=StatusFormat.speedValueText(number);}
    }
    private static Object nullable(JSONObject data,String key){return data.isNull(key)?null:data.opt(key);}
    public static Location parseLocation(JSONObject data,long now) {
        return new Location("速度："+StatusFormat.rawSpeed(data.has("curspeed"),nullable(data,"curspeed"))
            +"\n电源码："+StatusFormat.rawPower(data.has("power_status"),nullable(data,"power_status"))
            +"\n定位上报："+StatusFormat.rawReport(data.has("sd_time"),nullable(data,"sd_time"),now),StatusFormat.speedValue(data.has("curspeed"),nullable(data,"curspeed")),HomeStatus.binary(nullable(data,"power_status")));
    }
    /** One execution handle. Cancellation is safe before, during and after call registration. */
    public static final class Reader {
        private volatile boolean cancelled;
        private final StatusQueryLog.Endpoint diagnostic;
        public Reader(){this(null);}
        public Reader(StatusQueryLog.Endpoint value){diagnostic=value;}
        private void setStage(String value){stage=value;if(diagnostic!=null)diagnostic.phase(value);}
        private Object call;
        private String stage="start";
        private final JSONObject summary=new JSONObject();
        private void checkCancelled()throws InterruptedException{if(cancelled)throw new InterruptedException();}
        public void cancel() {
            Object current;synchronized(this){cancelled=true;current=call;}
            if(current!=null)try{Class.forName("okhttp3.Call").getMethod("cancel").invoke(current);}catch(Exception ignored){}
        }
        public Location read(Object source,Object bean)throws Exception {
            Object body=null;
        try {
            if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException();
            setStage("source");
            checkCancelled();
            int type=((Number)invoke(bean,"getVehicleType")).intValue();String number=(String)invoke(bean,"getWnumber");
            Object stream=source.getClass().getMethod("getLocationAndStatus",int.class,String.class).invoke(source,type,number);
            setStage("retrofit_call");Class<?> retrofitCall=Class.forName("retrofit2.Call");
            Object original=findCall(stream,retrofitCall,new IdentityHashMap<>(),0);if(original==null)throw new IllegalStateException();
            Object request=retrofitCall.getMethod("request").invoke(original);
            Class<?> requestType=Class.forName("okhttp3.Request"),urlType=Class.forName("okhttp3.HttpUrl");
            String method=(String)requestType.getMethod("method").invoke(request);
            Object url=requestType.getMethod("url").invoke(request);
            String path=(String)urlType.getMethod("encodedPath").invoke(url);
            if(!"POST".equals(method)||!path.endsWith("/vehicle/vehicle/vehicle-status"))throw new IllegalStateException();
            setStage("original_factory");
            Object strategy=field(source,"mRetrofitStrategy"),retrofit=invoke(strategy,"getRetrofit");
            Object factory=Class.forName("retrofit2.Retrofit").getMethod("callFactory").invoke(retrofit);
            Object created=Class.forName("okhttp3.Call$Factory").getMethod("newCall",requestType).invoke(factory,request);
            synchronized(this){checkCancelled();call=created;}
            setStage("execute");Class<?> callType=Class.forName("okhttp3.Call");
            // Bound the probe to the existing request timeouts plus a whole-call deadline when available.
            try{Object timeout=callType.getMethod("timeout").invoke(call);Class.forName("okio.Timeout").getMethod("timeout",long.class,java.util.concurrent.TimeUnit.class).invoke(timeout,20L,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception ignored){}
            Object response=callType.getMethod("execute").invoke(call);
            Class<?> responseType=Class.forName("okhttp3.Response");
            int http=((Number)responseType.getMethod("code").invoke(response)).intValue();summary.put("httpCode",http);if(diagnostic!=null)diagnostic.http(http);
            body=responseType.getMethod("body").invoke(response);if(body==null)throw new IllegalStateException();
            setStage("json");Class<?> bodyType=Class.forName("okhttp3.ResponseBody");
            long length=((Number)bodyType.getMethod("contentLength").invoke(body)).longValue();if(length>131072)throw new IllegalStateException();
            String text=(String)bodyType.getMethod("string").invoke(body);if(text.length()>131072)throw new IllegalStateException();
            JSONObject root=new JSONObject(text);Object code=root.has("code")?root.opt("code"):root.opt("resultCode");
            if(diagnostic!=null)diagnostic.business(code);
            Object codes=Class.forName("com.ninebot.ninebot.nbnet.NbResponseCode").getField("INSTANCE").get(null);
            summary.put("success",String.valueOf(invoke(codes,"getSUCCESS")).equals(String.valueOf(code)));
            JSONObject data=root.optJSONObject("data");summary.put("dataObject",data!=null);
            if(data!=null)for(String key:new String[]{"curspeed","power_status","sd_time"}) {
                summary.put(key+"Present",data.has(key));summary.put(key+"Null",data.isNull(key));
                Object value=data.opt(key);summary.put(key+"Numeric",value instanceof Number);
            }
            if(diagnostic!=null&&data!=null)diagnostic.rawFields(data);
            checkCancelled();
            if(http<200||http>=300||!summary.optBoolean("success")||data==null){
                if(diagnostic!=null)diagnostic.result("api_rejected",null);throw new IllegalStateException();
            }
            setStage("done");summary.put("stage",stage);
            Location result=parseLocation(data,System.currentTimeMillis());
            if(diagnostic!=null)diagnostic.result("success",null);return result;

            }catch(Exception failure){
                if(diagnostic!=null)diagnostic.result(cancelled?"cancelled":stage.equals("json")?"parse_failed":"request_failed",failure);
                throw failure;
            }finally {
                if(body!=null)try{Class.forName("okhttp3.ResponseBody").getMethod("close").invoke(body);}catch(Exception ignored){}
                cancel();
            }
        }
        private String metadata(boolean failed) {
            try{summary.put("stage",stage);if(failed)summary.put("failed",true);}catch(Exception ignored){}
            return summary.toString();
        }
    }
    /** Called by the private instrumentation runner on its worker thread. Metadata only. */
    public static String inspect(Activity activity) {
        Reader reader=new Reader();
        try {
            Object presenter=field(activity,"mPresenter");
            reader.read(field(presenter,"mApiSource"),field(presenter,"mDeviceBean"));
            return reader.metadata(false);
        }catch(Exception ignored){return reader.metadata(true);}
        finally{reader.cancel();}
    }
}
