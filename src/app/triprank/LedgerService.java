package app.triprank;

import android.app.*;
import android.content.*;
import android.os.*;
import android.content.pm.ServiceInfo;

public class LedgerService extends Service {
    public static volatile boolean running=false;
    public static volatile String code="", error="";
    private LocalServer server;
    private PowerManager.WakeLock wake;
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null && "STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(running)return START_NOT_STICKY;
        try{
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel("ledger","共享榜单运行状态",NotificationManager.IMPORTANCE_LOW));
            PendingIntent launch=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
            PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,LedgerService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
            Notification note=new Notification.Builder(this,"ledger").setSmallIcon(R.drawable.icon).setContentTitle("旅途排排榜共享中").setContentText("保持热点连接。点击管理地址与访问码。").setContentIntent(launch).setOngoing(true).addAction(new Notification.Action.Builder(null,"停止共享",stop).build()).build();
            if(Build.VERSION.SDK_INT>=29)startForeground(1,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);else startForeground(1,note);
            server=new LocalServer(this);server.start();code=server.code;error="";running=true;
            wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"triprank:sharing");wake.acquire();
        }catch(Exception e){error=e.getMessage()==null?"服务启动失败":e.getMessage();stopSelf();}
        return START_NOT_STICKY;
    }
    @Override public void onDestroy(){running=false;code="";if(server!=null)server.close();if(wake!=null&&wake.isHeld())wake.release();stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
