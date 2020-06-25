package onion.chat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

public class OnInternetReceiver extends BroadcastReceiver {

	String action;

	@RequiresApi(api = Build.VERSION_CODES.M)
	@Override
	public void onReceive(Context context, Intent intent) {
		if (action == null || !action.equals(intent.getAction()))
			if (checkInternet(context)) {
				Log.i("InternetReceiver", "Internet is connected");
				Tor.getInstance(context);
			} else {
				Log.i("InternetReceiver", "Internet isn't available");
				if (Tor.instance != null) {
					Tor.instance.kill();
					Tor.instance.close();
				}
			}
	}

	@RequiresApi(api = Build.VERSION_CODES.M)
	boolean checkInternet(Context context) {
		ConnectivityManager cm =
				(ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		assert cm != null;
		Network network = cm.getActiveNetwork();
		if (network == null) return false;
		NetworkCapabilities networkCapabilities = cm.getNetworkCapabilities(network);
		assert networkCapabilities != null;
		if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return true;
		if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true;
		return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
	}
}
