package local.ninebot.bridge;
import android.app.Activity;import android.os.*;import android.content.Intent;import android.widget.*;import android.graphics.Color;import android.view.Gravity;
public class ProbeActivity extends Activity {
 final Handler handler=new Handler(Looper.getMainLooper());TextView status,code;EditText address;Button confirm;boolean watch;
 int dp(int x){return (int)(getResources().getDisplayMetrics().density*x+.5f);}
 TextView text(LinearLayout root,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setGravity(Gravity.CENTER);root.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
 Button button(LinearLayout root,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(14);root.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));b.setOnClickListener(v->action.run());return b;}
 public void onCreate(Bundle b){super.onCreate(b);watch=getPackageManager().hasSystemFeature("android.hardware.type.watch");
  getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.BLACK);scroll.setClipToPadding(false);scroll.setPadding(dp(watch?30:24),dp(watch?30:32),dp(watch?30:24),dp(watch?54:24));
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);scroll.addView(root);setContentView(scroll);
  text(root,watch?"九号手表授权":"九号授权助手（非官方）",18);
  text(root,"手机与手表连接同一 Wi-Fi\n授权完成后可关闭助手",12);
  if(!watch){address=new EditText(this);address.setTextColor(Color.WHITE);address.setHintTextColor(Color.LTGRAY);address.setTextSize(16);address.setSingleLine(true);address.setHint("手表地址，例如 10.0.0.5:12345");address.setText(getPreferences(0).getString("address",""));root.addView(address);}
  button(root,watch?"开始配对":"连接手表",()->{ProbeService s=ProbeService.instance;if(s==null){Toast.makeText(this,"服务启动中，请稍后再试",0).show();return;}String a=watch?"":address.getText().toString();if(!watch)getPreferences(0).edit().putString("address",a).apply();DirectLink.start(s,a);});
  status=text(root,"尚未连接",14);code=text(root,"",20);
  confirm=button(root,"配对码一致，确认",()->{if(DirectLink.awaiting)DirectLink.approved=true;});
  button(root,watch?"打开九号出行":"打开手机九号出行",()->{Intent i=getPackageManager().getLaunchIntentForPackage(watch?"com.ninebot.ninebot":"cn.ninebot.ninebot");if(i!=null)startActivity(i);else Toast.makeText(this,"请先安装九号出行",0).show();});
  button(root,"结束授权连接",DirectLink::stop);
  handler.post(new Runnable(){public void run(){if(getPackageName().equals("com.ninebot.ninebot") && !getSharedPreferences("com.ninebot.ninebot",0).getString("token","").isEmpty()){startActivity(new Intent().setClassName(ProbeActivity.this,"com.ninebot.ninebot.StartActivity").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();return;}status.setText(DirectLink.state+(watch&&!DirectLink.address.isEmpty()?"\n"+DirectLink.address:""));code.setText(DirectLink.code);confirm.setEnabled(DirectLink.awaiting&&!DirectLink.approved);handler.postDelayed(this,400);}});
 }
 public void onResume(){super.onResume();try{startForegroundService(new Intent(this,ProbeService.class));}catch(IllegalStateException|SecurityException e){Toast.makeText(this,"请重新打开授权助手",0).show();}}
 public void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
