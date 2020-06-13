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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class ChatActivity extends AppCompatActivity {

	public static final int RECORD_AUDIO = 0;
	public static final int READ_EXTERNAL_STORAGE = 5;
	public static final int GALLERY_SUCCESS = 1;
	public static final int TAKE_PHOTO_SUCCESS = 2;
	public static final int CAPTURE_SUCCESS = 3;
	private final int REQUEST_RECORD_AUDIO = 12;

	private MediaRecorder mediaRecorder;
	private MediaPlayer mediaPlayer;
	static String pathToAudio;
	static String pathToPhotoAndVideo;
	ChatAdapter adapter;
	RecyclerView recycler;
	TorStatusView torStatusView;
	ImageView videoIcon, microIcon, photoIcon, sendIcon, galleryIcon;
	EditText edit;
	TextView noMessages;
	Cursor cursor;
	Database db;
	Tor tor;
	Notifier notifier;
	Server server;
	String address;
	Client client;

	String myname = "", othername = "";
	String sender;

	long idMsgLastLast = -1;

	long rep = 0;
	Timer timer;

	void update() {
		Cursor oldMsgCursor = cursor;

		myname = db.getName().trim();
		othername = db.getContactName(address).trim();

		//cursor = db.getReadableDatabase().query("messages", null, "((sender=? AND receiver=?) OR (sender=? AND receiver=?)) AND sender != '' AND receiver != ''", new String[] { tor.getID(), address, address, tor.getID() }, null, null, "time ASC");

		String a = tor.getID();
		String b = address;
		//cursor = db.getReadableDatabase().query("messages", null, "(sender=? AND receiver=?) OR (sender=? AND receiver=?)", new String[] { a, b, b, a }, null, null, "time ASC");
		//cursor = db.getReadableDatabase().rawQuery("SELECT * FROM (SELECT * FROM messages WHERE ((sender=? AND receiver=?) OR (sender=? AND receiver=?)) ORDER BY time DESC LIMIT 64) ORDER BY time ASC", new String[]{a, b, b, a});
		cursor = db.getMessages(a, b);

		cursor.moveToLast();
		long idMsgLast = -1;

		int i = cursor.getColumnIndex("_id");
		if (i >= 0 && cursor.getCount() > 0) {
			idMsgLast = cursor.getLong(i);
		}

		//if(oldCursor == null || cursor.getCount() != oldCursor.getCount())
		if (idMsgLast != idMsgLastLast) {
			idMsgLastLast = idMsgLast;

			if (oldMsgCursor == null || oldMsgCursor.getCount() == 0)
				recycler.scrollToPosition(Math.max(0, cursor.getCount() - 1));
			else
				recycler.smoothScrollToPosition(Math.max(0, cursor.getCount() - 1));

			//client.startSendPendingMessages(address);
		}

		adapter.notifyDataSetChanged();

		if (oldMsgCursor != null)
			oldMsgCursor.close();

		noMessages.setVisibility(cursor.getCount() > 0 ? View.GONE : View.VISIBLE);
	}

	void sendPendingAndUpdate(String log) {
		//if(!client.isBusy()) {
		log(log);
		client.startSendPendingMessages(address);
		//}
		update();
	}

	@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
	@SuppressLint("ClickableViewAccessibility")
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		//requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		//requestWindowFeature(Window.FEATURE_PROGRESS);

		//requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		//requestWindowFeature(Window.FEATURE_PROGRESS);


		super.onCreate(savedInstanceState);


		//supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);


		setContentView(R.layout.activity_chat);

		db = Database.getInstance(this);
		tor = Tor.getInstance(this);
		notifier = Notifier.getInstance(this);
		sender = tor.getID();

		pathToAudio = this.getCacheDir().getPath() + "/Media/Audio";
		pathToPhotoAndVideo = this.getCacheDir().getPath() + "/Media/Video";
		new File(pathToAudio + "/" + sender).mkdirs();
		new File(pathToPhotoAndVideo + "/" + sender).mkdirs();


		sendIcon = findViewById(R.id.send);
		edit = findViewById(R.id.editmessage);
		noMessages = findViewById(R.id.noMessages);
		client = Client.getInstance(this);
		torStatusView = findViewById(R.id.torStatusView);

		address = getIntent().getDataString();

		assert address != null;
		if (address.contains(":"))
			address = address.substring(address.indexOf(':') + 1);

		Log.i("ADDRESS", address);

		String name = db.getContactName(address);
		if (name.isEmpty()) {
			Objects.requireNonNull(getSupportActionBar()).setTitle(address);
		} else {
			Objects.requireNonNull(getSupportActionBar()).setTitle(name);
			getSupportActionBar().setSubtitle(address);
		}

		recycler = findViewById(R.id.recycler);

		recycler.setLayoutManager(new LinearLayoutManager(this));

		adapter = new ChatAdapter(this, address, sender);
		recycler.setAdapter(adapter);


		final View attach = findViewById(R.id.attachment);
		final View redCircle = findViewById(R.id.redCircle);
		final Animation redCircleAnim = AnimationUtils.loadAnimation(this, R.anim.red_circle_anim);
		// SENDING MESSAGE
		sendIcon.setOnClickListener(view -> {
			if (sender == null || sender.trim().equals("")) {
				sendPendingAndUpdate("sendMessage");
				return;
			}

			String message = edit.getText().toString();
			message = message.trim();
			if (message.equals("")) return;

			db.addPendingOutgoingMessage(sender, address, "msg", message.getBytes());

			edit.setText("");

			sendPendingAndUpdate("sendMessage");

			//recycler.scrollToPosition(cursor.getCount() - 1);

			recycler.smoothScrollToPosition(Math.max(0, cursor.getCount() - 1));

			rep = 0;
		});

		// RECORDING AND SENDING AUDIO MESSAGE
		microIcon = findViewById(R.id.micro);
		microIcon.setOnTouchListener((view, motionEvent) -> {
			if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
				ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
						RECORD_AUDIO);
			else
				switch (motionEvent.getActionMasked()) {
					case MotionEvent.ACTION_DOWN:
						pressTime = System.currentTimeMillis();
						recordStart(pressTime);
						redCircle.setVisibility(View.VISIBLE);
						redCircle.startAnimation(redCircleAnim);
						edit.setVisibility(View.INVISIBLE);
						attach.setVisibility(View.INVISIBLE);
						sendIcon.setVisibility(View.INVISIBLE);
						break;
					case MotionEvent.ACTION_CANCEL:
					case MotionEvent.ACTION_UP:
						if (System.currentTimeMillis() - pressTime > 1500) {
							recordStop();
							redCircle.setVisibility(View.INVISIBLE);
							edit.setVisibility(View.VISIBLE);
							attach.setVisibility(View.VISIBLE);
							sendIcon.setVisibility(View.VISIBLE);
							if (sender == null || sender.trim().equals("")) {
								sendPendingAndUpdate("audio 1");
								break;
							}
							String fileName = "/" + sender + "/record" + pressTime + ".3gpp";
							File audio = new File(pathToAudio + fileName);
							byte[] data = new byte[(int) audio.length()];
							FileInputStream in;
							try {
								in = new FileInputStream(audio);
								in.read(data);
								in.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
							db.addPendingOutgoingMessage(sender, address, "audio", fileName.getBytes());
							sendPendingAndUpdate("audio 2");
							recycler.smoothScrollToPosition(Math.max(0, cursor.getCount() - 1));
							rep = 0;
						} else {
							final Handler handler = new Handler();
							handler.postDelayed(() -> {
								recordStop();
								redCircle.setVisibility(View.INVISIBLE);
								edit.setVisibility(View.VISIBLE);
								attach.setVisibility(View.VISIBLE);
								sendIcon.setVisibility(View.VISIBLE);
							}, 1500 - System.currentTimeMillis() + pressTime);
						}
						break;
				}
			return false;
		});

		photoIcon = findViewById(R.id.takePhoto);
		videoIcon = findViewById(R.id.videoCapture);
		galleryIcon = findViewById(R.id.gallery);
		// CHOOSING MEDIA
		attach.setOnClickListener(view -> {
			if (microIcon.getVisibility() == View.GONE) {
				edit.setVisibility(View.VISIBLE);
				sendIcon.setVisibility(View.VISIBLE);
				microIcon.setVisibility(View.VISIBLE);
				photoIcon.setVisibility(View.GONE);
				videoIcon.setVisibility(View.GONE);
				galleryIcon.setVisibility(View.GONE);
			} else {
				edit.setVisibility(View.GONE);
				sendIcon.setVisibility(View.GONE);
				microIcon.setVisibility(View.GONE);
				photoIcon.setVisibility(View.VISIBLE);
				videoIcon.setVisibility(View.VISIBLE);
				galleryIcon.setVisibility(View.VISIBLE);
			}
		});

		// TAKE PHOTO
		photoIcon.setOnClickListener(view -> {
			Intent takePicture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			startActivityForResult(takePicture, TAKE_PHOTO_SUCCESS);
		});

		// CAPTURE VIDEO
		videoIcon.setOnClickListener(view -> {
			Intent captureVideo = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
			if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
				ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
						READ_EXTERNAL_STORAGE);
			else
				startActivityForResult(captureVideo, CAPTURE_SUCCESS);
		});

		// OPEN GALLERY
		galleryIcon.setOnClickListener(view -> {
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("image/*");
			intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
			startActivityForResult(Intent.createChooser(intent, "Select Picture"), GALLERY_SUCCESS);
		});


		startService(new Intent(this, HostService.class));

		final float a = 0.5f;
		sendIcon.setAlpha(a);
		sendIcon.setClickable(false);
		edit.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (s.toString().trim().length() == 0) {
					sendIcon.setAlpha(a);
					sendIcon.setClickable(false);
				} else {
					sendIcon.setAlpha(0.7f);
					sendIcon.setClickable(true);
				}
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

	}

	private long pressTime;

	// SENDING IMAGE
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
			case GALLERY_SUCCESS:
				if (resultCode == RESULT_OK) {
					if (data.getClipData() != null) {
						int count = data.getClipData().getItemCount();
						for (int currentItem = 0; currentItem < count; currentItem++) {
							Bitmap photo = null;
							try {
								photo = getBitmap(data.getClipData().getItemAt(currentItem).getUri());
							} catch (IOException e) {
								e.printStackTrace();
							}
							ByteArrayOutputStream stream = new ByteArrayOutputStream();
							photo.compress(Bitmap.CompressFormat.JPEG, 100, stream);
							byte[] byteArray = stream.toByteArray();
							photo.recycle();
							String photoName = "/" + sender + "/" + "photo" + System.currentTimeMillis() + ".jpeg";
							File photoFile = new File(pathToPhotoAndVideo + photoName);
							FileOutputStream out;
							try {
								out = new FileOutputStream(photoFile);
								out.write(byteArray);
								out.flush();
								out.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
							db.addPendingOutgoingMessage(sender, address, "photo", photoName.getBytes());
							sendPendingAndUpdate("camera");
							recycler.smoothScrollToPosition(Math.max(0, cursor.getCount() - 1));
							rep = 0;
						}
					} else if (data.getData() != null) {
						Toast.makeText(this, "PHOTO CHOOSED", Toast.LENGTH_SHORT).show();
						Bitmap photo = null;
						try {
							photo = getBitmap(data.getData());
						} catch (IOException e) {
							e.printStackTrace();
						}
						ByteArrayOutputStream stream = new ByteArrayOutputStream();
						assert photo != null;
						photo.compress(Bitmap.CompressFormat.JPEG, 100, stream);
						byte[] byteArray = stream.toByteArray();
						photo.recycle();
						String photoName = "/" + sender + "/" + "photo" + System.currentTimeMillis() + ".jpeg";
						File photoFile = new File(pathToPhotoAndVideo + photoName);
						FileOutputStream out;
						try {
							out = new FileOutputStream(photoFile);
							out.write(byteArray);
							out.flush();
							out.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
						db.addPendingOutgoingMessage(sender, address, "photo", photoName.getBytes());
						sendPendingAndUpdate("camera");
						recycler.smoothScrollToPosition(Math.max(0, cursor.getCount() - 1));
						rep = 0;
					}
				}
				break;
			case CAPTURE_SUCCESS:
				if (resultCode == RESULT_OK) {
					Toast.makeText(this, "Video captured", Toast.LENGTH_SHORT).show();
					String path = getRealPathFromURI(data.getData());
					Log.i("VIDEO_PATH", path);
					File video = new File(path);
					db.addPendingOutgoingMessage(sender, address, "video", (path).getBytes());
					Log.i("VIDEO_SIZE", video.length() / 1024 + "kb");
					sendPendingAndUpdate("video");
					recycler.smoothScrollToPosition(Math.max(0, cursor.getCount() - 1));
					rep = 0;
				}
				break;
			case TAKE_PHOTO_SUCCESS:
				if (resultCode == RESULT_OK) {
					Toast.makeText(this, "Photo taked", Toast.LENGTH_SHORT).show();
					Bitmap photo = (Bitmap) Objects.requireNonNull(data.getExtras()).get("data");
					ByteArrayOutputStream stream = new ByteArrayOutputStream();
					assert photo != null;
					photo.compress(Bitmap.CompressFormat.JPEG, 100, stream);
					byte[] byteArray = stream.toByteArray();
					photo.recycle();
					String photoName = "/" + sender + "/" + "photo" + System.currentTimeMillis() + ".jpeg";
					File photoFile = new File(pathToPhotoAndVideo + photoName);
					FileOutputStream out;
					try {
						out = new FileOutputStream(photoFile);
						out.write(byteArray);
						out.flush();
						out.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					db.addPendingOutgoingMessage(sender, address, "photo", photoName.getBytes());
					Log.i("PHOTO_SIZE", byteArray.length / 1024 + "kb");
					sendPendingAndUpdate("camera");
					recycler.smoothScrollToPosition(Math.max(0, cursor.getCount() - 1));
					rep = 0;
				}
				break;
		}
		edit.setVisibility(View.VISIBLE);
		sendIcon.setVisibility(View.VISIBLE);
		microIcon.setVisibility(View.VISIBLE);
		photoIcon.setVisibility(View.GONE);
		videoIcon.setVisibility(View.GONE);
		galleryIcon.setVisibility(View.GONE);
	}

	public String getRealPathFromURI(Uri contentUri) {
		String res = null;
		String[] proj = {MediaStore.Images.Media.DATA};
		Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
		if (cursor.moveToFirst()) {
			int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			res = cursor.getString(column_index);
		}
		cursor.close();
		return res;
	}

	public void recordStart(long pressTime) {
		try {
			releaseRecorder();
			Log.i("AUDIO", "released");
			File record = new File(pathToAudio + "/" + sender + "/record" + pressTime + ".3gpp");
			Log.i("AUDIO", "createdFile");
			if (record.exists())
				record.delete();

			mediaRecorder = new MediaRecorder();
			Log.i("AUDIO", "new media");
			mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
			mediaRecorder.setOutputFile(record.getAbsolutePath());
			Log.i("AUDIO", "setOutput");
			mediaRecorder.prepare();
			Log.i("AUDIO", "prepared");
			mediaRecorder.start();
			Log.i("AUDIO", "start");
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void recordStop() {
		if (mediaRecorder != null) {
			mediaRecorder.stop();
		}
	}

	public void playStart(String pathToAudio) {
		try {
			releasePlayer();
			mediaPlayer = new MediaPlayer();
			mediaPlayer.setDataSource(pathToAudio);
			mediaPlayer.prepare();
			mediaPlayer.setOnPreparedListener(mp -> mediaPlayer.start());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void playStop() {
		if (mediaPlayer != null) {
			mediaPlayer.stop();
		}
	}

	private void releaseRecorder() {
		if (mediaRecorder != null) {
			mediaRecorder.release();
			mediaRecorder = null;
		}
	}

	private void releasePlayer() {
		if (mediaPlayer != null) {
			mediaPlayer.release();
			mediaPlayer = null;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		releasePlayer();
		releaseRecorder();

		Log.i("ONDESTROY", "closing socket");
		/*if (sock != null)
			new Thread(() -> {
				client.sendMessage(sock, address, new byte[]{-1}, "exit");
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				sock.close();
				sock = null;
			}).start();*/
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.chat_activity_menu, menu);
		return true;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == REQUEST_RECORD_AUDIO && !(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED))
			Toast.makeText(this, "The app was not allowed to record audio. Hence, it cannot function properly. Please consider granting it this permission", Toast.LENGTH_LONG).show();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.phone_call:
				if (ContextCompat.checkSelfPermission(this,
						Manifest.permission.RECORD_AUDIO)
						!= PackageManager.PERMISSION_GRANTED)
					ActivityCompat.requestPermissions(this,
							new String[]{Manifest.permission.RECORD_AUDIO},
							REQUEST_RECORD_AUDIO);
				else if (tor.isReady()) {
					db.addPendingOutgoingMessage(sender, address, "incomingCall", new byte[]{1});
					sendPendingAndUpdate("call");
					recycler.smoothScrollToPosition(Math.max(0, cursor.getCount() - 1));
					rep = 0;
				} else if (!tor.isReady())
					Toast.makeText(this, "Tor isn't ready", Toast.LENGTH_SHORT).show();
				return true;
			/*case R.id.update_tor:
				tor.kill();
				tor.close();
				Tor.getInstance(this);
				tor.setListener(() -> runOnUiThread(() -> {
					if (!client.isBusy())
						sendPendingAndUpdate("resume");
				}));
				Toast.makeText(this, "Reconnect to tor", Toast.LENGTH_SHORT).show();
				return true;*/
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i("ChatActivity", "OnResume");
		server = Server.getInstance(this);
		server.setListener(() -> runOnUiThread(this::update));
		db.deleteOutgoingAudioCalls(address);

		tor.setListener(() -> runOnUiThread(() -> {
			if (!client.isBusy())
				sendPendingAndUpdate("resume");
		}));

		client.setStatusListener(loading -> runOnUiThread(() -> {
			Log.i("LOADING", "" + loading);
			//setProgressBarIndeterminateVisibility(loading);
			//setProgressBarIndeterminate(true);
			//setProgressBarVisibility(loading);
			//setProgressBarIndeterminateVisibility(loading);
			//setProgressBarVisibility(loading);
			findViewById(R.id.progressbar).setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
			if (!loading) update();
		}));

		//sendPendingAndUpdate();


		timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {

				rep++;

				if (rep > 5 && rep % 5 != 0) {
					log("wait");
					return;
				}


				log("update");


				if (client.isBusy()) {
					log("abort update, client busy");
					return;
				} else {
					log("do update");
					client.startSendPendingMessages(address);
				}

			}
		}, 0, 1000 * 60);

		notifier.onResumeActivity();

		db.clearIncomingMessageCount(address);

		torStatusView.update();

		startService(new Intent(this, HostService.class));
	}

	void log(String s) {
		Log.i("Chat", s);
	}

	@Override
	protected void onPause() {
		db.clearIncomingMessageCount(address);
		notifier.onPauseActivity();
		timer.cancel();
		timer.purge();
		server.setListener(null);
		tor.setListener(null);
		client.setStatusListener(null);
		super.onPause();
	}

	@Override
	public void onBackPressed() {

	}

	private String date(String str) {
		long t;
		try {
			t = Long.parseLong(str);
		} catch (Exception ex) {
			return "";
		}
		@SuppressLint("SimpleDateFormat") DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		return sdf.format(new Date(t));
	}

	private Bitmap getBitmap(String path) {
		final int IMAGE_MAX_SIZE = 120000; // 1.2MP
		// Decode image size
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(path, options);
		int scale = 1;
		while ((options.outWidth * options.outHeight) * (1 / Math.pow(scale, 2)) > IMAGE_MAX_SIZE)
			scale++;
		Log.d("SCALE", "scale = " + scale + ", orig-width: " + options.outWidth + ", orig-height: " + options.outHeight);

		Bitmap resultBitmap = null;
		if (scale > 1) {
			scale--;
			// scale to max possible inSampleSize that still yields an image
			// larger than target
			options = new BitmapFactory.Options();
			options.inSampleSize = scale;
			resultBitmap = BitmapFactory.decodeFile(path, options);

			// resize to desired dimensions
			int height = resultBitmap.getHeight();
			int width = resultBitmap.getWidth();
			Log.d("SCALE", "1th scale operation dimenions - width: " + width + ", height: " + height);

			double y = Math.sqrt(IMAGE_MAX_SIZE / (((double) width) / height));
			double x = (y / height) * width;

			//Bitmap scaledBitmap = Bitmap.createScaledBitmap(resultBitmap, (int) x, (int) y, true);
			//resultBitmap.recycle();
			//resultBitmap = scaledBitmap;

			System.gc();
		} else {
			resultBitmap = BitmapFactory.decodeFile(path);
		}

		Log.d("SIZE", "bitmap size - width: " + resultBitmap.getWidth() + ", height: " + resultBitmap.getHeight());
		return resultBitmap;
	}

	private Bitmap getBitmap(Uri path) throws IOException {
		final int IMAGE_MAX_SIZE = 120000; // 1.2MP
		// Decode image size
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		InputStream is = getContentResolver().openInputStream(path);
		BitmapFactory.decodeStream(is, null, options);
		assert is != null;
		is.close();
		int scale = 1;
		while ((options.outWidth * options.outHeight) * (1 / Math.pow(scale, 2)) > IMAGE_MAX_SIZE)
			scale++;
		Log.d("SCALE", "scale = " + scale + ", orig-width: " + options.outWidth + ", orig-height: " + options.outHeight);

		Bitmap resultBitmap;
		if (scale > 1) {
			scale--;
			// scale to max possible inSampleSize that still yields an image
			// larger than target
			options = new BitmapFactory.Options();
			options.inSampleSize = scale;
			InputStream is2 = getContentResolver().openInputStream(path);
			resultBitmap = BitmapFactory.decodeStream(is2, null, options);
			assert is2 != null;
			is2.close();

			// resize to desired dimensions
			assert resultBitmap != null;
			int height = resultBitmap.getHeight();
			int width = resultBitmap.getWidth();
			Log.d("SCALE", "1th scale operation dimenions - width: " + width + ", height: " + height);

			double y = Math.sqrt(IMAGE_MAX_SIZE / (((double) width) / height));
			double x = (y / height) * width;

			//Bitmap scaledBitmap = Bitmap.createScaledBitmap(resultBitmap, (int) x, (int) y, true);
			//resultBitmap.recycle();
			//resultBitmap = scaledBitmap;

			System.gc();
		} else {
			InputStream is2 = getContentResolver().openInputStream(path);
			resultBitmap = BitmapFactory.decodeStream(is2);
			assert is2 != null;
			is2.close();
		}

		Log.d("SIZE", "bitmap size - width: " + resultBitmap.getWidth() + ", height: " + resultBitmap.getHeight());
		return resultBitmap;
	}

	static class ChatHolder extends RecyclerView.ViewHolder {
		private TextView time, status;
		private View left, right;
		private CardView card;
		private View abort;

		public ChatHolder(View v) {
			super(v);
			//sender = (TextView)v.findViewById(R.id.sender);
			time = v.findViewById(R.id.time);
			status = v.findViewById(R.id.status);
			left = v.findViewById(R.id.left);
			right = v.findViewById(R.id.right);
			card = v.findViewById(R.id.card);
			abort = v.findViewById(R.id.abort);
		}
	}

	static class MessageHolder extends ChatHolder {
		private TextView message;

		public MessageHolder(View v) {
			super(v);
			message = v.findViewById(R.id.message);
		}
	}

	/*static class IncomingCallHolder extends ChatHolder {
		private ImageButton accept;
		private ImageButton decline;

		public IncomingCallHolder(View v) {
			super(v);
			accept = v.findViewById(R.id.acceptCall);
			decline = v.findViewById(R.id.declineCall);
		}
	}*/

	static class AudioHolder extends ChatHolder {
		private FloatingActionButton fab;
		private ProgressBar progress;
		private TextView time;

		public AudioHolder(View v) {
			super(v);
			fab = v.findViewById(R.id.playAudio);
			progress = v.findViewById(R.id.audioProgress);
			time = v.findViewById(R.id.audioTime);
		}
	}

	static class CallHolder extends ChatHolder {

		public CallHolder(View v) {
			super(v);
		}
	}

	static class VideoHolder extends ChatHolder {
		private VideoView video;

		public VideoHolder(View v) {
			super(v);
			video = v.findViewById(R.id.videoView);
		}
	}

	static class PhotoHolder extends ChatHolder {
		private ImageView photo;

		public PhotoHolder(View v) {
			super(v);
			photo = v.findViewById(R.id.photoView);
		}
	}

	class ChatAdapter extends RecyclerView.Adapter {

		Context context;
		String receiver, senderName;

		ChatAdapter(Context context, String receiver, String senderName) {
			this.context = context;
			this.receiver = receiver;
			this.senderName = senderName;
		}

		@Override
		public int getItemViewType(int position) {
			cursor.moveToFirst();
			cursor.moveToPosition(position);
			String type = cursor.getString(cursor.getColumnIndex("type"));
			switch (type) {
				case "incomingCall":
					return 5;
				case "video":
					return 2;
				case "photo":
					return 3;
				case "audio":
					return 4;
				default:
					return 1;
			}
		}

		@Override
		@NonNull
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			switch (viewType) {
				case 5:
					return new CallHolder(getLayoutInflater().inflate(R.layout.item_call, parent, false));
				case 4:
					return new AudioHolder(getLayoutInflater().inflate(R.layout.item_audio, parent, false));
				case 2:
					return new VideoHolder(getLayoutInflater().inflate(R.layout.item_video, parent, false));
				case 3:
					return new PhotoHolder(getLayoutInflater().inflate(R.layout.item_photo, parent, false));
				default:
					return new MessageHolder(getLayoutInflater().inflate(R.layout.item_message, parent, false));
			}
		}

		@SuppressLint("SetTextI18n")
		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			if (cursor == null) return;

			cursor.moveToFirst();
			cursor.moveToPosition(position);

			final long id = cursor.getLong(cursor.getColumnIndex("_id"));
			byte[] content = cursor.getBlob(cursor.getColumnIndex("content"));
			String sender = cursor.getString(cursor.getColumnIndex("sender"));
			String time = date(cursor.getString(cursor.getColumnIndex("time")));
			boolean pending = cursor.getInt(cursor.getColumnIndex("pending")) > 0;
			boolean tx = sender.equals(tor.getID());

			if (tx) sender = "You";

			if (tx) {
				((ChatHolder) holder).left.setVisibility(View.VISIBLE);
				((ChatHolder) holder).right.setVisibility(View.GONE);
			} else {
				((ChatHolder) holder).left.setVisibility(View.GONE);
				((ChatHolder) holder).right.setVisibility(View.VISIBLE);
			}

			if (pending)
				//holder.card.setCardElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics()));
				((ChatHolder) holder).card.setCardElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()));
			else
				((ChatHolder) holder).card.setCardElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, getResources().getDisplayMetrics()));


			String status = "";
			if (!tx) {
				if (othername.isEmpty())
					status = address;
				else
					status = othername;
			} else {
				if (pending) {
					status = getString(R.string.message_pending);
					//status = "...";
					//status = "Waiting...";
				} else {
					status = getString(R.string.message_sent);
					if (holder instanceof CallHolder) {
						Intent intent = new Intent(context, AudioCallActivity.class);
						intent.putExtra("address", receiver);
						intent.putExtra("sender", senderName);
						intent.putExtra("name", db.getContactName(receiver));
						server.close();
						startActivity(intent);
					}
					//status = "\u2713";
					//status = "Sent.";
				}
			}


			int color = pending ? 0xff000000 : 0xff888888;
			((ChatHolder) holder).time.setTextColor(color);
			((ChatHolder) holder).status.setTextColor(color);
			String path = new String(content);
			boolean self = path.split("/").length <= 3;

			if (holder instanceof VideoHolder) {
				Log.i("CONTENT", "video");
				path = self ? pathToPhotoAndVideo + path : path;
				File receivedVideo = new File(path);
				Log.i("PATH_TO_VIDEO", receivedVideo.getPath());
				((VideoHolder) holder).video.setVideoPath(receivedVideo.getPath());
				MediaController mediaController = new MediaController(holder.itemView.getContext());
				((VideoHolder) holder).video.setMediaController(mediaController);
				mediaController.setMediaPlayer(((VideoHolder) holder).video);
			} else if (holder instanceof PhotoHolder) {
				Log.i("CONTENT", "photo");
				path = self ? pathToPhotoAndVideo + path : path;
				Log.i("RECEIVED_PHOTO", path);
				((PhotoHolder) holder).photo.setImageBitmap(getBitmap(path));
			} else if (holder instanceof AudioHolder) {
				Log.i("AUDIO_ARRAY_LENGTH", String.valueOf(content.length));
				path = self ? pathToAudio + path : path;
				File receivedAudio = new File(path);
				Log.i("PATH_TO_AUDIO", receivedAudio.getPath());
				((AudioHolder) holder).fab.setOnClickListener(view -> {
					/*	 if (mediaPlayer == null) {*/
					((AudioHolder) holder).fab.setImageResource(R.drawable.pause);
					playStart(receivedAudio.getPath());
					((AudioHolder) holder).progress.setProgress(mediaPlayer.getCurrentPosition());
					mediaPlayer.setOnCompletionListener(mediaPlayer -> {
						((AudioHolder) holder).fab.setImageResource(R.drawable.play);
					});
					/*if (mediaPlayer.isPlaying()) {
						((AudioHolder) holder).fab.setImageResource(R.drawable.play);
						mediaPlayer.pause();
					} else {
						((AudioHolder) holder).fab.setImageResource(R.drawable.pause);
						mediaPlayer.start();
					}*/
				});
			} else if (holder instanceof MessageHolder) {
				((MessageHolder) holder).message.setMovementMethod(LinkMovementMethod.getInstance());
				((MessageHolder) holder).message.setText(Utils.linkify(ChatActivity.this, new String(content)));
			} else if (holder instanceof CallHolder && !tx)
				if (tor.isReady()) {
					db.deleteOutgoingMessage(id);
					Intent intent = new Intent(context, AudioCallActivity.class);
					intent.putExtra("address", receiver);
					intent.putExtra("sender", senderName);
					intent.putExtra("receiver", "");
					intent.putExtra("name", db.getContactName(receiver));
					server.close();
					startActivity(intent);
				} else if (!tor.isReady())
					Toast.makeText(context, "Tor isn't ready", Toast.LENGTH_SHORT).show();

			((ChatHolder) holder).time.setText(time);
			((ChatHolder) holder).status.setText(status);


			if (pending) {
				((ChatHolder) holder).abort.setVisibility(View.VISIBLE);
				((ChatHolder) holder).abort.setClickable(true);
				((ChatHolder) holder).abort.setOnClickListener(v -> {
					boolean ok = db.abortOutgoingMessage(id);
					update();
					Toast.makeText(ChatActivity.this, ok ? "Pending message aborted." : "Error: Message already sent.", Toast.LENGTH_SHORT).show();
				});
			} else {
				((ChatHolder) holder).abort.setVisibility(View.GONE);
				((ChatHolder) holder).abort.setClickable(false);
				((ChatHolder) holder).abort.setOnClickListener(null);
			}
		}

		@Override
		public int getItemCount() {
			return cursor != null ? cursor.getCount() : 0;
		}

	}
}