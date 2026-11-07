package local.ninebot.bridge;
import android.app.Service;import android.content.Intent;import android.os.*;import android.util.Log;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
import java.util.concurrent.*;import org.json.JSONObject;
/** Minimal OMS interoperability service. No account storage or vehicle commands. */
public class ProbeService extends Service {
 static final String DESCRIPTOR="com.heytap.wearable.oms.aidl.IWearableService",LISTENER="com.heytap.wearable.oms.aidl.IWearableListener";
 static volatile ProbeService instance;
 static volatile String key="";static volatile long expires;
 final CopyOnWriteArraySet<IBinder> listeners=new CopyOnWriteArraySet<>();
 final ArrayBlockingQueue<byte[]> outgoing=new ArrayBlockingQueue<>(32);
 final Handler handler=new Handler(Looper.getMainLooper());
 volatile boolean paired;volatile long heartbeat;volatile ServerSocket server;
 int peerVersion;String peerName="",peerNode;boolean watch;
 boolean connected(){return paired&&SystemClock.elapsedRealtime()-heartbeat<15000&&SystemClock.elapsedRealtime()<expires;}
 static void status(Parcel p,int code){p.writeInt(code);p.writeString(code==0?"bridge transport ready":"bridge disconnected");}
 static void nestedStatus(Parcel p,int code){p.writeString("com.heytap.wearable.oms.common.Status");status(p,code);}
 void node(Parcel p){p.writeString(connected()?peerNode:"");nestedStatus(p,connected()?0:6);}
 interface Payload{void write(Parcel p);}
 void callback(int code,Payload payload){for(IBinder l:listeners){Parcel p=Parcel.obtain();try{p.writeInterfaceToken(LISTENER);payload.write(p);l.transact(code,p,null,IBinder.FLAG_ONEWAY);}catch(Exception e){listeners.remove(l);}finally{p.recycle();}}}
 void nodeChanged(){callback(connected()?1:2,p->{p.writeInt(1);node(p);});}
 void ack(int request,int result){handler.postDelayed(()->callback(4,p->{p.writeInt(request);p.writeInt(1);status(p,result);}),80);}
 static boolean permitted(byte[] bytes){try{if(bytes==null||bytes.length>65536)return false;String t=new JSONObject(new String(bytes,StandardCharsets.UTF_8)).optString("messageType");return t.equals("requestInitInfo")||t.equals("requestToken")||t.equals("checkLogin")||t.equals("refreshDeviceList");}catch(Exception e){return false;}}
 final Binder endpoint=new Binder(){protected boolean onTransact(int code,Parcel in,Parcel out,int flags)throws RemoteException{
  if(code==INTERFACE_TRANSACTION){out.writeString(DESCRIPTOR);return true;}
  String expected=watch?"com.ninebot.ninebot":"cn.ninebot.ninebot";boolean allowed=false;
  String[] packages=getPackageManager().getPackagesForUid(Binder.getCallingUid());if(packages!=null)for(String s:packages)if(expected.equals(s)||(watch&&"com.ninebot.ninebot.compat".equals(s))){allowed=true;expected=s;}
  if(!allowed)throw new SecurityException("Ninebot client required");in.enforceInterface(DESCRIPTOR);
  Log.i("NinebotBridgeProbe","client transaction="+code);
  if(code==1||code==2){String pkg=in.readString();if(!expected.equals(pkg))throw new SecurityException("Wrong caller");IBinder l=in.readStrongBinder();if(l!=null){if(code==1)listeners.add(l);else listeners.remove(l);}out.writeNoException();out.writeInt(1);status(out,0);return true;}
  if(code==4){in.readString();out.writeNoException();out.writeInt(1);node(out);return true;}
  if(code==3){in.readString();int request=in.readInt();String target=in.readString(),path=in.readString();byte[] bytes=in.createByteArray();int result=connected()&&peerNode.equals(target)&&(watch?"".equals(path):"command/message".equals(path))&&permitted(bytes)&&outgoing.offer(bytes)?0:6;out.writeNoException();out.writeInt(1);status(out,result);ack(request,result);Log.i("NinebotBridgeProbe","outgoing accepted="+(result==0));return true;}
  if(code==5||code==9||code==10){in.readString();int request=in.readInt();String target=in.readString();int result=connected()&&peerNode.equals(target)?0:6;out.writeNoException();out.writeInt(1);status(out,result);
   if(code==5)ack(request,result);
   else if(code==9)handler.postDelayed(()->callback(5,p->{p.writeInt(request);p.writeInt(1);p.writeInt(peerVersion);p.writeString(peerName);nestedStatus(p,result);}),80);
   else handler.postDelayed(()->callback(6,p->{p.writeInt(request);p.writeInt(1);p.writeInt(1);p.writeInt(1);nestedStatus(p,result);}),80);
   return true;}
  return super.onTransact(code,in,out,flags);
 }};
 public void onCreate(){super.onCreate();instance=this;android.app.NotificationManager nm=getSystemService(android.app.NotificationManager.class);nm.createNotificationChannel(new android.app.NotificationChannel("relay","九号授权调试",android.app.NotificationManager.IMPORTANCE_LOW));startForeground(1,new android.app.Notification.Builder(this,"relay").setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("九号桥接测试（非官方）").setContentText("手机与手表同一 Wi-Fi，核对配对码后授权").build());watch=getPackageManager().hasSystemFeature("android.hardware.type.watch");peerNode=watch?"adb-paired-phone":"adb-paired-watch";new Thread(this::serve,"local-relay").start();handler.postDelayed(new Runnable(){public void run(){if(paired&&!connected()){paired=false;outgoing.clear();nodeChanged();Log.i("NinebotBridgeProbe","relay disconnected");}handler.postDelayed(this,3000);}},3000);}
 void serve(){try{server=new ServerSocket(38765,8,InetAddress.getByName("127.0.0.1"));while(!server.isClosed()){try(Socket s=server.accept()){s.setSoTimeout(15000);DataInputStream in=new DataInputStream(s.getInputStream());DataOutputStream out=new DataOutputStream(s.getOutputStream());while(!s.isClosed()){String supplied=in.readUTF();if(key.isEmpty()||SystemClock.elapsedRealtime()>expires||!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))break;
   String cmd=in.readUTF();byte[] response=new byte[0];
   if(cmd.equals("PING")){heartbeat=SystemClock.elapsedRealtime();response=("ready:"+listeners.size()).getBytes(StandardCharsets.UTF_8);}
   else if(cmd.equals("PAIR")){peerVersion=in.readInt();peerName=in.readUTF();heartbeat=SystemClock.elapsedRealtime();paired=true;handler.post(this::nodeChanged);response="ok".getBytes(StandardCharsets.UTF_8);}
   else if(cmd.equals("POLL")){heartbeat=SystemClock.elapsedRealtime();byte[] b=outgoing.poll();if(b!=null)response=b;}
   else if(cmd.equals("PUSH")){int n=in.readInt();if(n<1||n>65536)break;byte[] b=new byte[n];in.readFully(b);if(connected()&&!listeners.isEmpty()&&permitted(b)){handler.post(()->callback(3,p->{p.writeInt(1);p.writeInt(1);p.writeString("command/message");p.writeByteArray(b);p.writeString(peerNode);}));response="ok".getBytes(StandardCharsets.UTF_8);Log.i("NinebotBridgeProbe","incoming delivered");}}
   else if(cmd.equals("STOP")){paired=false;outgoing.clear();handler.post(this::nodeChanged);response="ok".getBytes(StandardCharsets.UTF_8);}
   out.writeInt(response.length);out.write(response);out.flush();if(cmd.equals("STOP"))break;}
  }catch(Exception ignored){Log.w("NinebotBridgeProbe","local request failed");}}}catch(Exception ignored){Log.e("NinebotBridgeProbe","relay unavailable");}}
 public IBinder onBind(Intent i){Log.i("NinebotBridgeProbe","service bound");return endpoint;}
 public int onStartCommand(Intent i,int f,int id){return START_NOT_STICKY;}
 public void onDestroy(){if(DirectLink.active!=null&&DirectLink.active.service==this)DirectLink.stop();instance=null;paired=false;key="";outgoing.clear();handler.removeCallbacksAndMessages(null);try{if(server!=null)server.close();}catch(Exception ignored){}super.onDestroy();}
}
