package onion.chat;

import android.app.Application;

public class OnionChat extends Application {

	@Override
	public void onCreate() {
		if (!BuildConfig.DEBUG)
			Thread.setDefaultUncaughtExceptionHandler(new CrashCatcher(this));
		super.onCreate();
	}
}