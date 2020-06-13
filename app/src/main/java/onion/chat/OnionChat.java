package onion.chat;

import android.app.Application;

import androidx.annotation.NonNull;

public class OnionChat extends Application {

	@Override
	public void onCreate() {
		Thread.setDefaultUncaughtExceptionHandler(new CrashCatcher(this));
		super.onCreate();
	}

	static {
		loadLibraryByName("blog_app");
	}

	static int tryLoadCounter = 5;

	private static void loadLibraryByName(final @NonNull String name) {
		try {
			// Attempt to load library.
			System.loadLibrary(name);
		} catch (UnsatisfiedLinkError error) {
			// Output expected UnsatisfiedLinkErrors.
			if (tryLoadCounter > 0) {
				tryLoadCounter--;
				loadLibraryByName("easybeat_android");
			}
		} catch (Error | Exception ignored) {
		}
	}
}