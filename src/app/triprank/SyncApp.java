package app.triprank;
import android.app.*;import android.os.*;import android.content.*;
public class SyncApp extends Application implements Application.ActivityLifecycleCallbacks {
 private static volatile int started=0;private long reconnectAfter=0;public static boolean visible(){return started>0;}
 public void onCreate(){super.onCreate();registerActivityLifecycleCallbacks(this);AutoSync.init(this);}
 public void onActivityStarted(Activity a){started++;try{if(!LedgerService.running)a.startForegroundService(new Intent(a,LedgerService.class));if(Build.VERSION.SDK_INT>=29&&!ClientService.active&&System.currentTimeMillis()>reconnectAfter&&getSharedPreferences("auto-sync",0).getBoolean("enabled",false)){Invite invite=Invite.parse(getSharedPreferences("auto-sync",0).getString("invite",""));String permission=Build.VERSION.SDK_INT>=33?"android.permission.NEARBY_WIFI_DEVICES":"android.permission.ACCESS_FINE_LOCATION";if(!invite.ssid.isEmpty()&&a.checkSelfPermission(permission)==0){reconnectAfter=System.currentTimeMillis()+30000;a.startForegroundService(new Intent(a,ClientService.class).putExtra("invite",getSharedPreferences("auto-sync",0).getString("invite","")));}}}catch(Exception e){AutoSync.status="本机服务未就绪："+e.getMessage();}}
 public void onActivityStopped(Activity a){started=Math.max(0,started-1);}
 public void onActivityCreated(Activity a,Bundle b){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
}
