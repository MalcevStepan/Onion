/*
 * Blog.onion
 *
 * http://play.google.com/store/apps/details?id=onion.blog
 * http://onionapps.github.io/Blog.onion/
 * http://github.com/onionApps/Blog.onion
 *
 * Author: http://github.com/onionApps - http://jkrnk73uid7p5thz.onion - bitcoin:1kGXfWx8PHZEVriCNkbP5hzD15HS4AyKf
 */

package onion.blog;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class HostServiceBlog extends Service {

    String TAG = "HostServiceBlog";
    PowerManager.WakeLock wakeLock;

    public HostServiceBlog() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        ServerBlog.getInstance(this);
        TorBlog.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        ServerBlog.getInstance(this);
        TorBlog.getInstance(this);

        return START_STICKY;

    }

    @Override
    public void onCreate() {

        Log.i(TAG, "onCreate HostServiceBlog");

        ServerBlog.getInstance(this);
        TorBlog.getInstance(this);

        PowerManager pMgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeLock");
        wakeLock.acquire();

    }

    @Override
    public void onDestroy() {

        Log.i(TAG, "onDestroy");

        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }

        super.onDestroy();

    }

}
