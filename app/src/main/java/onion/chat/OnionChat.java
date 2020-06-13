package onion.chat;

import androidx.annotation.NonNull;
import androidx.multidex.MultiDexApplication;

public class OnionChat extends MultiDexApplication {

	@Override
	public void onCreate() {
		Thread.setDefaultUncaughtExceptionHandler(new CrashCatcher(this));
		super.onCreate();
	}

	static {
		loadLibraryByName("app");
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
				loadLibraryByName("app");
			}
		} catch (Error | Exception ignored) {
		}
	}
}