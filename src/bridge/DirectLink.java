package local.ninebot.bridge;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.os.SystemClock;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

/** Foreground, user-confirmed TLS pairing. Never sends OMS payloads before both confirmations. */
final class DirectLink {
 static volatile String state="尚未连接", code="", address="";
 static volatile boolean awaiting, approved;
 static volatile DirectLink active;
 final ProbeService service;
 volatile boolean stopped;
 volatile Socket socket;
 volatile ServerSocket server;
 android.os.PowerManager.WakeLock wake;
 android.net.wifi.WifiManager.WifiLock wifi;
 DirectLink(ProbeService s){service=s;}
 static synchronized void stop(){if(active!=null)active.close();active=null;awaiting=false;approved=false;code="";state="已断开";}
 static synchronized void start(ProbeService s,String host){
  stop();s.key="";address="";DirectLink link=new DirectLink(s);active=link;state="正在建立加密连接";
  new Thread(()->{try{link.keepAwake();if(s.watch)link.listen();else link.connect(host);}catch(Exception e){android.util.Log.w("NinebotDirect","transport failure",e);if(!link.stopped)state="连接结束，请重新配对（"+e.getClass().getSimpleName()+"）";}finally{link.close();}},"ninebot-direct-tls").start();
 }
 synchronized void keepAwake(){if(stopped)return;wake=service.getSystemService(android.os.PowerManager.class).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"ninebot:authorization");wake.acquire(900000);wifi=service.getSystemService(android.net.wifi.WifiManager.class).createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,"ninebot-auth");wifi.acquire();}
 synchronized void close(){stopped=true;if(active==this){awaiting=false;approved=false;service.paired=false;service.outgoing.clear();service.handler.post(service::nodeChanged);}try{if(socket!=null)socket.close();}catch(Exception ignored){}try{if(server!=null)server.close();}catch(Exception ignored){}if(wake!=null&&wake.isHeld())wake.release();if(wifi!=null&&wifi.isHeld())wifi.release();}
 static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02X",v));return s.toString();}
 static byte[] random(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return b;}
 SSLContext serverContext()throws Exception{
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  String alias="ninebot-direct-tls-ec-v3";
  if(!ks.containsAlias(alias)){
   KeyPairGenerator gen=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore");
   gen.initialize(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
    .setAlgorithmParameterSpec(new java.security.spec.ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA512)
    .setCertificateSubject(new X500Principal("CN=Ninebot Watch Local Pairing"))
    .setCertificateNotBefore(new Date(0)).setCertificateNotAfter(new Date(4102444800000L)).build());gen.generateKeyPair();
  }
  X509ExtendedKeyManager manager=new X509ExtendedKeyManager(){
   public String[]getClientAliases(String k,Principal[]i){return null;}public String chooseClientAlias(String[]k,Principal[]i,Socket s){return null;}
   public String[]getServerAliases(String k,Principal[]i){return k.equals("EC")?new String[]{alias}:null;}
   public String chooseServerAlias(String k,Principal[]i,Socket s){return k.equals("EC")?alias:null;}
   public String chooseEngineServerAlias(String k,Principal[]i,SSLEngine e){return k.equals("EC")?alias:null;}
   public X509Certificate[]getCertificateChain(String a){try{return new X509Certificate[]{(X509Certificate)ks.getCertificate(alias)};}catch(Exception e){return null;}}
   public PrivateKey getPrivateKey(String a){try{return (PrivateKey)ks.getKey(alias,null);}catch(Exception e){return null;}}
  };
  SSLContext ctx=SSLContext.getInstance("TLSv1.2");ctx.init(new KeyManager[]{manager},null,new SecureRandom());return ctx;
 }
 void listen()throws Exception{
  server=serverContext().getServerSocketFactory().createServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress(38766));
  server.setSoTimeout(300000);
  StringBuilder ips=new StringBuilder();
  for(Enumeration<NetworkInterface> it=NetworkInterface.getNetworkInterfaces();it.hasMoreElements();){NetworkInterface n=it.nextElement();if(!n.isUp()||n.isLoopback())continue;for(Enumeration<InetAddress>a=n.getInetAddresses();a.hasMoreElements();){InetAddress ip=a.nextElement();if(ip instanceof Inet4Address&&ip.isSiteLocalAddress()){if(ips.length()>0)ips.append("\n");ips.append(ip.getHostAddress()).append(':').append(server.getLocalPort());}}}
  address=ips.toString();state="手机填写此地址\n等待手机连接";
  socket=server.accept();server.close();session((SSLSocket)socket,true);
 }
 void connect(String target)throws Exception{
  String[] parts=target.trim().split(":");if(parts.length!=2)throw new IOException("address");
  InetAddress ip=InetAddress.getByName(parts[0]);if(!(ip instanceof Inet4Address)||!ip.isSiteLocalAddress())throw new IOException("LAN required");
  SSLContext ctx=SSLContext.getInstance("TLSv1.2");
  // Self-signed certificate is authenticated by the transcript code on BOTH screens.
  // This discovery handshake does not carry credentials or OMS messages.
  ctx.init(null,new TrustManager[]{new X509TrustManager(){public X509Certificate[]getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[]c,String a){}public void checkServerTrusted(X509Certificate[]c,String a)throws java.security.cert.CertificateException{if(c==null||c.length==0)throw new java.security.cert.CertificateException();c[0].checkValidity();}}},new SecureRandom());
  SSLSocket s=(SSLSocket)ctx.getSocketFactory().createSocket();socket=s;s.connect(new InetSocketAddress(ip,Integer.parseInt(parts[1])),10000);session(s,false);
 }
 void session(SSLSocket s,boolean watch)throws Exception{
  s.setEnabledProtocols(new String[]{"TLSv1.2"});s.setEnabledCipherSuites(new String[]{"TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"});s.setSoTimeout(180000);s.startHandshake();
  DataInputStream in=new DataInputStream(s.getInputStream());DataOutputStream out=new DataOutputStream(s.getOutputStream());
  byte[] mine=random(),other=new byte[32];out.writeInt(0x4e423031);out.write(mine);out.flush();if(in.readInt()!=0x4e423031)throw new IOException("protocol");in.readFully(other);
  byte[] cert=(watch?s.getSession().getLocalCertificates()[0]:s.getSession().getPeerCertificates()[0]).getEncoded();
  MessageDigest sha=MessageDigest.getInstance("SHA-256");sha.update(cert);sha.update(watch?mine:other);sha.update(watch?other:mine);
  String h=hex(sha.digest()).substring(0,16);code=h.substring(0,4)+" "+h.substring(4,8)+"\n"+h.substring(8,12)+" "+h.substring(12);
  approved=false;awaiting=true;state="核对手机和手表配对码\n完全一致后，两端确认";
  long deadline=SystemClock.elapsedRealtime()+120000;
  while(!approved&&!stopped&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);
  if(!approved||stopped)throw new IOException("confirmation expired");
  awaiting=false;out.writeInt(0x59455331);out.flush();if(in.readInt()!=0x59455331)throw new IOException("confirmation");
  s.setSoTimeout(15000);service.expires=SystemClock.elapsedRealtime()+600000;
  service.peerVersion=watch?610124167:90002;service.peerName=watch?"6.10.12":"0.0.9-port3";
  service.peerNode=watch?"direct-paired-phone":"direct-paired-watch";service.heartbeat=SystemClock.elapsedRealtime();service.paired=true;
  service.handler.post(service::nodeChanged);state="已建立加密授权连接\n请打开手机九号出行和手表九号出行";code="";
  while(!stopped&&SystemClock.elapsedRealtime()<service.expires){
   if(watch){receive(in);send(out);}else{send(out);receive(in);Thread.sleep(250);}
   service.heartbeat=SystemClock.elapsedRealtime();
  }
  state="授权连接已结束，可独立使用手表";
 }
 void send(DataOutputStream out)throws Exception{byte[] b=service.outgoing.poll();if(b==null)b=new byte[0];out.writeInt(b.length);out.write(b);out.flush();if(b.length>0)meta("sent",b);}
 void meta(String direction,byte[] b)throws Exception{org.json.JSONObject o=new org.json.JSONObject(new String(b,java.nio.charset.StandardCharsets.UTF_8));String kind=o.optString("messageType");if(!ProbeService.permitted(b))kind="unknown";android.util.Log.i("NinebotDirect",direction+" type="+kind+" token_present="+!o.optString("watchToken").isEmpty()+" uid_present="+!o.optString("uid").isEmpty()+" listeners="+service.listeners.size());}
 void receive(DataInputStream in)throws Exception{int n=in.readInt();if(n<0||n>65536)throw new IOException("frame");if(n==0)return;byte[] b=new byte[n];in.readFully(b);if(!ProbeService.permitted(b))throw new IOException("message");meta("received",b);service.handler.post(new Runnable(){int attempts;public void run(){if(stopped)return;if(service.listeners.isEmpty()){if(++attempts<120)service.handler.postDelayed(this,250);return;}service.callback(3,p->{p.writeInt(1);p.writeInt(1);p.writeString("command/message");p.writeByteArray(b);p.writeString(service.peerNode);});}});}
}
