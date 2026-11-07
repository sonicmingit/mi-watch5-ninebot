package local.ninebot.bridge;
import android.content.*;import android.os.SystemClock;
/** Shell-only setup; session key is never written to storage. */
public class ConfigReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){String key=i.getStringExtra("key");if(key!=null&&key.matches("[0-9a-f]{64}")){ProbeService.key=key;ProbeService.expires=SystemClock.elapsedRealtime()+1200000;setResultCode(101);android.util.Log.i("NinebotBridgeProbe","session configured");}else setResultCode(102);}
}
