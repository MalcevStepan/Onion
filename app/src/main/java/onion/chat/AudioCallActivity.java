package onion.chat;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AudioCallActivity extends AppCompatActivity implements SensorEventListener {
	String socketName, receiver, sender;
	private LocalServerSocket serverSocket;
	private LocalSocket ls;
	ImageButton hangup, pickup;
	TextView contactName, status;
	Sock sock;
	InputStream in;
	OutputStream out;
	static int rateInHz = 8000, channelConfig = AudioFormat.CHANNEL_IN_MONO, audioFormat = AudioFormat.ENCODING_PCM_8BIT,
			bufferSize = AudioRecord.getMinBufferSize(rateInHz, channelConfig, audioFormat);
	String LOG_TAG = "AUDIO_CALL";
	boolean isConnected;
	AudioTrack audioReceived, beepCall;
	AudioRecord audioRecord;

	Thread serverThread = new Thread() {
		@Override
		public void run() {
			LocalServerSocket ss = serverSocket;
			if (ss != null)
				while (true) {
					Log.i(LOG_TAG, "waiting response");
					final LocalSocket ls;
					try {
						ls = ss.accept();
						if (BuildConfig.DEBUG) Log.i(LOG_TAG, "accept");
					} catch (IOException ex) {
						throw new Error(ex);
					}
					Log.i(LOG_TAG, "new connection");
					try {
						out = ls.getOutputStream();
						in = ls.getInputStream();
						byte[] b = new byte[1];
						if (in.read(b) == 1) {
							Log.i(LOG_TAG, "getting response");
							if (b[0] == 1) {
								out.write(new byte[]{1});
								out.flush();
								isConnected = true;
								runOnUiThread(() -> status.setText("Connected"));
								startAudioCallThreads();
								break;
							} else if (b[0] == 0) disconnect();
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
		}
	};
	Thread receiverThread = new Thread(() -> {
		while (!isConnected) {
			sock = new Sock(AudioCallActivity.this, receiver + ".onion", Tor.getHiddenServicePort());
			Log.i(LOG_TAG, "socket connected");
			in = sock.reader;
			out = sock.writer;
			if (in == null || out == null)
				continue;
			try {
				out.write(new byte[]{1});
				out.flush();
				Log.i(LOG_TAG, "Request sent");
				byte[] b = new byte[1];
				if (in.read(b) == 1) {
					Log.i(LOG_TAG, "Getting response");
					if (b[0] == 1) {
						isConnected = true;
						runOnUiThread(() -> status.setText("Connected"));
						startAudioCallThreads();
						break;
					} else if (b[0] == 0) disconnect();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	});

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//int flags = WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
		//getWindow().addFlags(flags);
		SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		assert sensorManager != null;
		Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

		if (sensor != null)
			sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

		audioReceived = new AudioTrack(AudioManager.STREAM_VOICE_CALL, rateInHz, AudioFormat.CHANNEL_OUT_MONO, audioFormat, bufferSize, AudioTrack.MODE_STREAM);
		beepCall = new AudioTrack(AudioManager.STREAM_VOICE_CALL, rateInHz, AudioFormat.CHANNEL_OUT_MONO, audioFormat, bufferSize, AudioTrack.MODE_STREAM);
		audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, rateInHz, channelConfig, audioFormat, bufferSize);
		setContentView(R.layout.activity_audio_call);
		hangup = findViewById(R.id.hangup);
		pickup = findViewById(R.id.pickup);
		contactName = findViewById(R.id.contactName);
		status = findViewById(R.id.connectStatus);
		Intent intent = getIntent();
		receiver = intent.getStringExtra("address");
		sender = intent.getStringExtra("sender");
		contactName.setText(intent.getStringExtra("name"));
		boolean isSender = intent.getStringExtra("receiver") == null;
		// CALL TO CONTACT AND WAITING FOR CONNECTION
		if (isSender) {
			pickup.setVisibility(View.GONE);
			Log.i(LOG_TAG, "start listening");
			try {
				socketName = new File(this.getFilesDir(), "socket").getAbsolutePath();
				ls = new LocalSocket();
				ls.bind(new LocalSocketAddress(socketName, LocalSocketAddress.Namespace.FILESYSTEM));
				serverSocket = new LocalServerSocket(ls.getFileDescriptor());
				socketName = "unix:" + socketName;
				Log.i(LOG_TAG, socketName);
			} catch (Exception ex) {
				throw new Error(ex);
			}
			Log.i(LOG_TAG, "started listening");
			serverThread.start();
		} else {
			status.setText("Incoming call");
			pickup.setOnClickListener(view -> {
				status.setText("Waiting...");
				pickup.setVisibility(View.GONE);
				receiverThread.start();
			});
		}

		new Thread(() -> {
			byte[] audioBuff = new byte[bufferSize];
			beepCall.play();
			while (!isConnected) {
				audioCallSound(audioBuff);
				try {
					beepCall.write(audioBuff, 0, bufferSize);
				} catch (IllegalStateException ignored) {
				}
			}
			beepCall.release();
		}).start();

		hangup.setOnClickListener(view -> new Thread(this::disconnect).start());
	}

	void startAudioCallThreads() {
		byte[] outbuff = new byte[bufferSize];
		byte[] inbuff = new byte[bufferSize];
		new Thread(() -> {
			Log.i(LOG_TAG, "Starting record");
			audioRecord.startRecording();
			while (isConnected) {
				audioRecord.read(outbuff, 0, bufferSize);
				try {
					if (out != null) {
						out.write(outbuff);
						out.flush();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
		// RECEIVING AUDIO CALL
		new Thread(() -> {
			Log.i(LOG_TAG, "Starting receiving");
			audioReceived.play();
			while (isConnected) {
				try {
					int len = 0;
					if (in != null)
						len = in.read(inbuff);
					if (len > 1) {
						audioVoice(inbuff);
						try {
							audioReceived.write(inbuff, 0, bufferSize);
						} catch (IllegalStateException ignored) {
						}
					} else if (len == -1)
						disconnect();
				} catch (IOException e) {
					e.printStackTrace();
					if (sock != null)
						if (!sock.sock.isClosed())
							disconnect();
				}
			}
		}).start();
	}

	private void disconnect() {
		isConnected = false;
		if (out != null) {
			try {
				out.write(new byte[]{0});
				out.flush();
			} catch (IOException ignored) {
			}
		}
		runOnUiThread(() -> status.setText("Disconnect"));
		try {
			Thread.sleep(50);
		} catch (InterruptedException ignored) {
		}
		if (sock != null) {
			sock.close();
			sock = null;
		}
		ls = null;
		if (in != null) {
			try {
				in.close();
				in = null;
				out.close();
				out = null;
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		if (audioRecord != null)
			try {
				audioRecord.stop();
			} catch (IllegalStateException ignored) {
			}
		if (audioReceived != null) try {
			audioReceived.release();
		} catch (IllegalStateException ignored) {
		}
		if (audioReceived != null) try {
			audioReceived.stop();
		} catch (IllegalStateException ignored) {
		}
		if (beepCall != null) try {
			beepCall.stop();
		} catch (IllegalStateException ignored) {
		}
		if (beepCall != null) try {
			beepCall.release();
		} catch (IllegalStateException ignored) {
		}
		System.gc();
		finish();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		disconnect();
	}

	@Override
	protected void onStop() {
		super.onStop();
		disconnect();
	}

	@Override
	public void onBackPressed() {

	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
			if (event.values[0] >= -4 && event.values[0] <= 4) {
				WindowManager.LayoutParams params = getWindow().getAttributes();
				params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
				params.screenBrightness = 0;
				getWindow().setAttributes(params);
				if (hangup != null) hangup.setEnabled(false);
			} else {
				WindowManager.LayoutParams params = getWindow().getAttributes();
				params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
				params.screenBrightness = 1;
				getWindow().setAttributes(params);
				if (hangup != null) hangup.setEnabled(true);
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int i) {

	}

	private static native void audioVoice(byte[] array);

	private static native void audioCallSound(byte[] array);
}