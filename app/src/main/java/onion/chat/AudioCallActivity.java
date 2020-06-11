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
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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
	ImageButton hangup;
	TextView contactName, status;
	AudioRecord audioRecord;
	Sock sock;
	InputStream in;
	OutputStream out;
	int rateInHz = 8000, channelConfig = AudioFormat.CHANNEL_IN_MONO, audioFormat = AudioFormat.ENCODING_PCM_8BIT,
			bufferSize = AudioRecord.getMinBufferSize(rateInHz, channelConfig, audioFormat);
	int timeout = 1000;
	String LOG_TAG = "AUDIO_CALL";
	boolean isConnected;
	AudioTrack audioReceived = new AudioTrack(AudioManager.STREAM_VOICE_CALL, rateInHz, AudioFormat.CHANNEL_OUT_MONO, audioFormat, bufferSize, AudioTrack.MODE_STREAM);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//int flags = WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
		//getWindow().addFlags(flags);
		SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		assert sensorManager != null;
		Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

		sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

		setContentView(R.layout.activity_audio_call);
		hangup = findViewById(R.id.hangup);
		contactName = findViewById(R.id.contactName);
		status = findViewById(R.id.connectStatus);
		Intent intent = getIntent();
		receiver = intent.getStringExtra("address");
		sender = intent.getStringExtra("sender");
		contactName.setText(intent.getStringExtra("name"));
		// CALL TO CONTACT AND WAITING FOR CONNECTION
		if (intent.getStringExtra("receiver") == null) {
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
			new Thread() {
				@Override
				public void run() {
					LocalServerSocket ss = serverSocket;
					if (ss != null) {
						Log.i(LOG_TAG, "waiting response");
						final LocalSocket ls;
						try {
							ls = ss.accept();
							if (BuildConfig.DEBUG) Log.i(LOG_TAG, "accept");
							ls.setSoTimeout(timeout);
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
								}
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}.start();
		} else {
			new Thread(() -> {
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
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}).start();
		}

		new Thread(() -> {
			byte[] audioBuff = new byte[bufferSize];
			audioReceived.play();
			while (!isConnected) {
				audioCallSound(audioBuff);
				audioReceived.write(audioBuff, 0, bufferSize);
			}
		}).start();

		hangup.setOnClickListener(view -> {
			new Thread(this::disconnect).start();
			if (audioRecord != null)
				try {
					audioRecord.stop();
				} catch (IllegalStateException ignored) {

				}
		});
	}

	void startAudioCallThreads() {
		byte[] outbuff = new byte[bufferSize];
		byte[] inbuff = new byte[bufferSize];
		new Thread(() -> {
			Log.i(LOG_TAG, "Starting record");
			audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, rateInHz, channelConfig, audioFormat, bufferSize);
			audioRecord.startRecording();
			while (isConnected) {
				audioRecord.read(outbuff, 0, bufferSize);
				try {
					if (out != null) {
						out.write(outbuff);
						out.flush();
					}
					Log.i(LOG_TAG, "packet sent");
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
						Log.i(LOG_TAG, "packet received");
						audioVoice(inbuff);
						audioReceived.write(inbuff, 0, bufferSize);
					} else if (len == -1)
						disconnect();
					Log.i(LOG_TAG, "LENGTH = " + len);
				} catch (IOException e) {
					e.printStackTrace();
					if (sock != null)
						if (!sock.sock.isClosed())
							disconnect();
				}
			}
			audioReceived.stop();
		}).start();
	}

	private void disconnect() {
		isConnected = false;
		runOnUiThread(() -> status.setText("Disconnect"));
		if (sock != null) {
			sock.close();
			sock = null;
		}
		if (ls != null) {
			try {
				ls.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			ls = null;
		}
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
		if (audioRecord != null) audioRecord.release();
		startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("chat:" + receiver), getApplicationContext(), ChatActivity.class));
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		disconnect();
	}

	@Override
	protected void onPause() {
		super.onPause();
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