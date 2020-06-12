package onion.chat;

import android.app.Application;

public class OnionChat extends Application {

	@Override
	public void onCreate() {
		Thread.setDefaultUncaughtExceptionHandler(new CrashCatcher(this));
		super.onCreate();
	}
}