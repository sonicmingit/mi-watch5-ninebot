package local.ninebot.account;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import org.json.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;

/** Whitelist-only query diagnostics. No arbitrary keys, messages or response objects reach disk. */
public final class StatusQueryLog {
    /** Build script sets this false in the public flavor. */
    public static final boolean LOGGING_ENABLED = true;
    public interface Saved { void complete(String result); }
    private static final java.util.concurrent.Executor WRITER=Executors.newSingleThreadExecutor();
    private final String id=UUID.randomUUID().toString();
    private final long started=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();
    private final String trigger;
    public final Endpoint location=new Endpoint("vehicle_status","raw");
    public final Endpoint engine=new Endpoint("power_status","typed");
    private boolean finished;
    public StatusQueryLog(boolean manual){trigger=manual?"manual":"resume";}
    private StatusQueryLog(){trigger="speed_auto";engine.result("not_requested",null);}
    private StatusQueryLog(String value){trigger=value;}
    public static StatusQueryLog home(){return new StatusQueryLog("home_auto");}
    public static StatusQueryLog speed(){return new StatusQueryLog();}
    private static void put(JSONObject o,String k,Object v){try{o.put(k,v);}catch(JSONException ignored){}}
    private static String choice(String value,String... allowed){for(String s:allowed)if(s.equals(value))return s;return "other";}
    public static String exceptionKind(Throwable t) {
        while(t instanceof java.lang.reflect.InvocationTargetException && t.getCause()!=null)t=t.getCause();
        if(t instanceof java.net.SocketTimeoutException)return "socket_timeout";
        if(t instanceof java.net.UnknownHostException)return "unknown_host";
        if(t instanceof java.io.InterruptedIOException)return "interrupted_io";
        if(t instanceof InterruptedException)return "interrupted";
        if(t instanceof java.io.IOException)return "io";
        if(t instanceof JSONException)return "json";
        if(t instanceof ReflectiveOperationException)return "reflection";
        if(t instanceof SecurityException)return "security";
        if(t instanceof IllegalStateException)return "invalid_state";
        return "other_exception";
    }
    public static final class Endpoint {
        private final String endpoint,representation;
        private final long began=SystemClock.elapsedRealtime();
        private String phase="start",outcome="pending",error;
        private Integer http;
        private Double business;
        private long took;
        private JSONObject fields;
        private boolean sealed;
        private Endpoint(String e,String r){endpoint=e;representation=r;}
        public synchronized void phase(String value){if(!sealed)phase=choice(value,"start","source","retrofit_call","original_factory","execute","json","done","subscribe","response");}
        public synchronized void http(int value){if(!sealed&&value>=100&&value<=599)http=value;}
        public synchronized void business(Object value){if(!sealed)business=limitedNumber(value);}
        private static Double limitedNumber(Object value){Double n=StatusFormat.finiteNumber(value);return n!=null&&Math.abs(n)<=1e15?n:null;}
        private static String type(Object v) {
            if(v==null||v==JSONObject.NULL)return "null";
            if(v instanceof Number)return "number";
            if(v instanceof String)return "string";
            if(v instanceof Boolean)return "boolean";
            if(v instanceof JSONObject)return "object";
            if(v instanceof JSONArray)return "array";
            return "other";
        }
        private static JSONObject rawField(JSONObject data,String key) {
            JSONObject f=new JSONObject();boolean present=data.has(key);Object value=data.opt(key);
            put(f,"present",present);put(f,"null",present&&data.isNull(key));put(f,"type",present?type(value):"missing");
            Double n=limitedNumber(value);if(n!=null)put(f,"numericValue",n);
            return f;
        }
        public synchronized void rawFields(JSONObject data) {
            if(sealed)return;fields=new JSONObject();
            for(String key:new String[]{"curspeed","power_status","sd_time"})put(fields,key,rawField(data,key));
        }
        private static JSONObject typedField(String value) {
            JSONObject f=new JSONObject();put(f,"presence","typed_"+(value==null?"null":"non_null"));put(f,"type",value==null?"null":"string");
            Double n=limitedNumber(value);if(n!=null)put(f,"numericValue",n);return f;
        }
        public synchronized void engineFields(String acc,String lock,String report) {
            if(sealed)return;fields=new JSONObject();put(fields,"acc_status",typedField(acc));put(fields,"lock_status",typedField(lock));
            JSONObject stamp=new JSONObject();put(stamp,"presence","typed_"+(report==null?"null":"non_null"));
            long parsed=report!=null&&report.length()<=64?StatusFormat.reportMillis(report):0;
            put(stamp,"parsedTimeMillis",parsed);put(fields,"report_time",stamp);
        }
        public synchronized void result(String value,Throwable failure) {
            if(sealed||!outcome.equals("pending"))return;
            outcome=choice(value,"success","request_failed","parse_failed","api_rejected","empty","cancelled","timeout","setup_failed","not_requested");
            if(failure!=null)error=exceptionKind(failure);took=SystemClock.elapsedRealtime()-began;
        }
        private synchronized JSONObject snapshot(String overall) {
            if(outcome.equals("pending"))result(overall.equals("complete")?"empty":overall,null);
            sealed=true;JSONObject o=new JSONObject();put(o,"endpoint",endpoint);put(o,"representation",representation);put(o,"phase",phase);put(o,"outcome",outcome);put(o,"elapsedMs",took);
            if(http!=null)put(o,"httpCode",http);if(business!=null)put(o,"businessCode",business);if(error!=null)put(o,"exception",error);if(fields!=null)put(o,"fields",fields);
            return o;
        }
    }
    public synchronized void finish(Context context,String outcome,Saved saved) {
        if(finished)return;finished=true;
        if(!LOGGING_ENABLED){if(saved!=null)saved.complete("日志：测试日志已禁用");return;}
        String finalState=choice(outcome,"complete","cancelled","timeout","setup_failed");
        JSONObject o=new JSONObject();put(o,"queryId",id);put(o,"appVersion","0.0.9-test90019-test-menu");put(o,"trigger",trigger);
        put(o,"startedAtMillis",started);put(o,"endedAtMillis",System.currentTimeMillis());put(o,"elapsedMs",SystemClock.elapsedRealtime()-elapsed);put(o,"outcome",finalState);
        put(o,"location",location.snapshot(finalState));put(o,"engine",engine.snapshot(finalState));
        final String json=o.toString();final Context app=context.getApplicationContext();
        WRITER.execute(()->{
            String result;Uri uri=null;
            String name=new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.CHINA).format(new Date())+"-"+id.substring(0,8)+".json";
            try {
                if(Build.VERSION.SDK_INT<29)throw new IllegalStateException();
                ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,name);values.put(MediaStore.Downloads.MIME_TYPE,"application/json");
                values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/logs/ninebot/");values.put(MediaStore.Downloads.IS_PENDING,1);
                ContentResolver resolver=app.getContentResolver();uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IllegalStateException();
                try(OutputStream stream=resolver.openOutputStream(uri,"w")){if(stream==null)throw new IllegalStateException();stream.write(json.getBytes(StandardCharsets.UTF_8));}
                ContentValues ready=new ContentValues();ready.put(MediaStore.Downloads.IS_PENDING,0);if(resolver.update(uri,ready,null,null)!=1)throw new IllegalStateException();
                result="日志已保存：Download/logs/ninebot/"+name;
            }catch(Exception failure){if(uri!=null)try{app.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}result="日志保存失败（"+exceptionKind(failure)+"）";}
            final String message=result;new Handler(Looper.getMainLooper()).post(()->saved.complete(message));
        });
    }
}
