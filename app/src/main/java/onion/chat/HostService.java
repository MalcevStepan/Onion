/*
 * Chat.onion - P2P Instant Messenger
 *
 * http://play.google.com/store/apps/details?id=onion.chat
 * http://onionapps.github.io/Chat.onion/
 * http://github.com/onionApps/Chat.onion
 *
 * Author: http://github.com/onionApps - http://jkrnk73uid7p5thz.onion - bitcoin:1kGXfWx8PHZEVriCNkbP5hzD15HS4AyKf
 */

package onion.chat;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.Timer;
import java.util.TimerTask;

public class HostService extends Service {

	String TAG = "HostServiceBlog";
	Timer timer;
	Client client;
	Server server;
	Tor tor;
	WifiManager.WifiLock wifiLock;
	PowerManager.WakeLock wakeLock;

	public HostService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Server.getInstance(this);
		Tor.getInstance(this);
		return START_STICKY;
	}

	void log(String s) {
		Log.i(TAG, s);
	}

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	@SuppressLint("InvalidWakeLockTag")
	@Override
	public void onCreate() {
		super.onCreate();

		log("onCreate");
		server = Server.getInstance(this);
		tor = Tor.getInstance(this);
		client = Client.getInstance(this);

		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkRequest nr = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
		NetworkRequest nr2 = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build();
		if (cm != null) {
			cm.registerNetworkCallback(nr, new ConnectionCallback());
			cm.registerNetworkCallback(nr2, new ConnectionCallback());
		} else Log.i(TAG, "ConnectivityManager is unavailable");

		timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				log("update");
				client.doSendPendingFriends();
				client.doSendAllPendingMessages();
			}
		}, 0, 1000 * 60 * 60);

        /*
        WifiManager wMgr = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        //wifiLock = wMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WifiLock");
        wifiLock = wMgr.createWifiLock(WifiManager.WIFI_MnODE_FULL, "WifiLock");
        wifiLock.acquire();
        */

		PowerManager pMgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
		assert pMgr != null;
		wakeLock = pMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeLock");
		wakeLock.acquire();
	}

	@Override
	public void onDestroy() {

		log("onDestroy");

		timer.cancel();
		timer.purge();

		if (wifiLock != null) {
			wifiLock.release();
			wifiLock = null;
		}

		if (wakeLock != null) {
			wakeLock.release();
			wakeLock = null;
		}
		super.onDestroy();
	}

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	class ConnectionCallback extends ConnectivityManager.NetworkCallback {
		@Override
		public void onAvailable(Network network) {
			Log.i(TAG, "Network connection is available");
			Tor.getInstance(HostService.this);
			Server.getInstance(HostService.this);
		}

		@Override
		public void onLost(Network network) {
			Log.i(TAG, "Losing network connection");
			tor.kill();
			tor.close();
			//server.close();
		}

		@Override
		public void onUnavailable() {
			Log.i(TAG, "Network connection is unavailable");
			tor.kill();
			tor.close();
			//server.close();
		}
	}
}
