package onion.chat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashCatcher implements Thread.UncaughtExceptionHandler {

	private final DateFormat formatter = new SimpleDateFormat("dd.MM.yy HH:mm", Locale.ENGLISH);
	private final long timeRun = System.currentTimeMillis();

	@Override
	public void uncaughtException(@NonNull Thread thread, @NonNull Throwable ex) {
		try {
			if (ex.getMessage() != null) {
				@SuppressLint("HardwareIds") Thread sendError = new Thread(() ->
				{
					try {
						Socket clientSocket = new Socket(NetMemory.getHost(), NetMemory.getPort());
						clientSocket.setSoTimeout(60000);

						OutputStream clientOutputStream = clientSocket.getOutputStream();
						writeString(clientOutputStream, NetMemory.getDeviceName());
						writeString(clientOutputStream, Build.SERIAL);

						long timeStop = System.currentTimeMillis();
						final Date dumpDate = new Date(timeStop);
						StringBuilder stringBuilder = new StringBuilder();
						stringBuilder
								.append(formatter.format(dumpDate)).append("\n")
								.append(String.format("Version: %s\n", EasyBeatActivity.getCurrentActivity() != null ? EasyBeatActivity.getCurrentActivity().getPackageManager().getPackageInfo(EasyBeatActivity.getCurrentActivity().getPackageName(), 0).versionName : "is unknown"))
								.append("Duration in app: ").append((timeStop - timeRun > 60000) ? (timeStop - timeRun) / 60000 + "m " + (((timeStop - timeRun) / 1000f - ((timeStop - timeRun) / 60000) * 60)) + "s\n" : (timeStop - timeRun) / 1000f + "s\n")
								.append(thread.toString()).append("\n");
						processThrowable(ex, stringBuilder);
            /*for (StackTraceElement traceElement : ex.getStackTrace())
              stringBuilder.append(traceElement.toString()).append("\n");*/

						clientOutputStream.write(new byte[]{(byte) NetMemory.NetSends.SEND_CRASH_VERSION.getValue()});
						writeString(clientOutputStream, "onion_chat" + (EasyBeatActivity.getCurrentActivity() != null ? EasyBeatActivity.getCurrentActivity().getPackageManager().getPackageInfo(EasyBeatActivity.getCurrentActivity().getPackageName(), 0).versionName : "is unknown"));
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
			Activity activityException = EasyBeatActivity.getCurrentActivity();
			if (activityException != null) {
				activityException.startActivity(new Intent(activityException, SplashActivity.class).putExtra("crash", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
				activityException.finish();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.exit(2);
	}

	private static void writeString(OutputStream clientOutputStream, String value) throws IOException {
		clientOutputStream.write(new byte[]{(byte) value.length()});
		clientOutputStream.write(value.getBytes());
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