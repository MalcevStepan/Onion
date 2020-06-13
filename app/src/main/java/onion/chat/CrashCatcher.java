package onion.chat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashCatcher implements Thread.UncaughtExceptionHandler {

	private final Context context;
	private final DateFormat formatter = new SimpleDateFormat("dd.MM.yy HH:mm", Locale.ENGLISH);
	private final long timeRun = System.currentTimeMillis();

	public CrashCatcher(Context context) {
		super();
		this.context = context;
	}

	@Override
	public void uncaughtException(@NonNull Thread thread, @NonNull Throwable ex) {
		try {
			if (ex.getMessage() != null) {
				@SuppressLint("HardwareIds") Thread sendError = new Thread(() ->
				{
					try {
						Socket clientSocket = new Socket("95.142.45.201", 390);
						clientSocket.setSoTimeout(60000);

						OutputStream clientOutputStream = clientSocket.getOutputStream();
						writeString(clientOutputStream, getDeviceName());
						writeString(clientOutputStream, Build.SERIAL);

						long timeStop = System.currentTimeMillis();
						final Date dumpDate = new Date(timeStop);
						StringBuilder stringBuilder = new StringBuilder();
						stringBuilder
								.append(formatter.format(dumpDate)).append("\n")
								.append(String.format("Version: %s\n", context != null ? context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName : "is unknown"))
								.append("Duration in app: ").append((timeStop - timeRun > 60000) ? (timeStop - timeRun) / 60000 + "m " + (((timeStop - timeRun) / 1000f - ((timeStop - timeRun) / 60000) * 60)) + "s\n" : (timeStop - timeRun) / 1000f + "s\n")
								.append(thread.toString()).append("\n");
						processThrowable(ex, stringBuilder);
            /*for (StackTraceElement traceElement : ex.getStackTrace())
              stringBuilder.append(traceElement.toString()).append("\n");*/

						clientOutputStream.write(new byte[]{(byte) 12});
						writeString(clientOutputStream, "onion_chat" + (context != null ? context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName : "is unknown"));
						writeStringAsFile(clientOutputStream, ex.getMessage());
						writeStringAsFile(clientOutputStream, stringBuilder.toString());
						clientOutputStream.close();
						clientSocket.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
				});
				sendError.start();
				if (sendError.isAlive())
					sendError.join();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		//context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
		System.exit(2);
	}

	private static void writeString(OutputStream clientOutputStream, String value) throws IOException {
		clientOutputStream.write(new byte[]{(byte) value.length()});
		clientOutputStream.write(value.getBytes());
		Log.i("CrashCatcher", "Message sent");
	}


	@NonNull
	private static String capitalize(@Nullable String s) {
		return s == null || s.length() == 0 ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	@NonNull
	public static String getDeviceName() {
		return Build.MODEL.toLowerCase().startsWith(Build.MANUFACTURER.toLowerCase()) ? capitalize(Build.MODEL) : capitalize(Build.MANUFACTURER) + " " + Build.MODEL;
	}

	private static void writeStringAsFile(OutputStream clientOutputStream, String value) throws IOException {
		byte[] bytes = ByteBuffer.allocate(4).putInt(value.length()).array();
		byte tmp = bytes[0];
		bytes[0] = bytes[3];
		bytes[3] = tmp;
		tmp = bytes[1];
		bytes[1] = bytes[2];
		bytes[2] = tmp;
		clientOutputStream.write(bytes);
		clientOutputStream.write(value.getBytes());
	}

	private void processThrowable(Throwable exception, StringBuilder builder) {
		if (exception == null)
			return;
		StackTraceElement[] stackTraceElements = exception.getStackTrace();
		builder.append("Exception: ").append(exception.getClass().getName()).append("\n")
				.append("Message: ").append(exception.getMessage()).append("\nStacktrace:\n");
		for (StackTraceElement element : stackTraceElements) {
			builder.append("\t").append(element.toString()).append("\n");
		}
		processThrowable(exception.getCause(), builder);
	}
}