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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.provider.MediaStore;
import android.renderscript.FieldPacker;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class ChatActivity extends AppCompatActivity {

	public static final int RECORD_AUDIO = 0;
	public static final int GALLERY_SUCCESS = 1;
	public static final int TAKE_PHOTO_SUCCESS = 2;
	public static final int CAPTURE_SUCCESS = 3;
	public static final long MAX_FILE_SIZE = 16000000;

	private MediaRecorder mediaRecorder;
	private MediaPlayer mediaPlayer;
	private String pathToAudio;
	private String pathToPhotoAndVideo;
	ChatAdapter adapter;
	RecyclerView recycler;
	ImageView videoIcon, microIcon, photoIcon, sendIcon, galleryIcon;
	EditText edit;
	TextView noMessages;
	Cursor cursor;
	Database db;
	Tor tor;
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

		pathToAudio = this.getCacheDir().getPath() + "/Media/Audio";
		pathToPhotoAndVideo = this.getCacheDir().getPath() + "/Media/Video";
		new File(pathToAudio).mkdirs();
		new File(pathToPhotoAndVideo).mkdir();

		sender = tor.getID();
		sendIcon = findViewById(R.id.send);
		edit = findViewById(R.id.editmessage);
		noMessages = findViewById(R.id.noMessages);
		client = Client.getInstance(this);

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

		adapter = new ChatAdapter();
		recycler.setAdapter(adapter);


		final View attach = findViewById(R.id.attachment);
		final View redCircle = findViewById(R.id.redCircle);
		final Animation redCircleAnim = AnimationUtils.loadAnimation(this, R.anim.red_circle_anim);
		// SENDING MESSAGE
		sendIcon.setOnClickListener(view -> {
			if (sender == null || sender.trim().equals("")) {
				sendPendingAndUpdate("sendIcon");
				return;
			}

			String message = edit.getText().toString();
			message = message.trim();
			if (message.equals("")) return;

			db.addPendingOutgoingMessage(sender, address, "msg", message.getBytes());

			edit.setText("");

			sendPendingAndUpdate("sendIcon");

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
						recordStart();
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
							File audio = new File(pathToAudio + "/record.3gpp");
							byte[] data = new byte[(int) audio.length()];
							FileInputStream in;
							try {
								in = new FileInputStream(audio);
								in.read(data);
								in.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
							db.addPendingOutgoingMessage(sender, address, "audio", data);
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
						for(int currentItem = 0;currentItem<count;currentItem++){
							Bitmap photo = null;
							try {
								photo = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getClipData().getItemAt(currentItem).getUri());
							} catch (IOException e) {
								e.printStackTrace();
							}
							ByteArrayOutputStream stream = new ByteArrayOutputStream();
							assert photo != null;
							photo.compress(Bitmap.CompressFormat.JPEG, 100, stream);
							byte[] byteArray = stream.toByteArray();
							photo.recycle();
							db.addPendingOutgoingMessage(sender, address, "photo", byteArray);
							sendPendingAndUpdate("camera");
							recycler.smoothScrollToPosition(Math.max(0, cursor.getCount() - 1));
							rep = 0;
						}
					} else if (data.getData() != null) {
						Toast.makeText(this, "PHOTO CHOOSED", Toast.LENGTH_SHORT).show();
						Bitmap photo = null;
						try {
							photo = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());
						} catch (IOException e) {
							e.printStackTrace();
						}
						ByteArrayOutputStream stream = new ByteArrayOutputStream();
						assert photo != null;
						photo.compress(Bitmap.CompressFormat.JPEG, 100, stream);
						byte[] byteArray = stream.toByteArray();
						photo.recycle();
						db.addPendingOutgoingMessage(sender, address, "photo", byteArray);
						sendPendingAndUpdate("camera");
						recycler.smoothScrollToPosition(Math.max(0, cursor.getCount() - 1));
						rep = 0;
					}
				}
				break;
			case CAPTURE_SUCCESS:
				if (resultCode == RESULT_OK) {
					Toast.makeText(this, "Video captured", Toast.LENGTH_SHORT).show();
					try {
						db.addPendingOutgoingMessage(sender, address, "video", read(new File(Objects.requireNonNull(Objects.requireNonNull(data.getData()).getPath()))));
					} catch (IOException e) {
						e.printStackTrace();
					}
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
					db.addPendingOutgoingMessage(sender, address, "photo", byteArray);
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

	public byte[] read(File file) throws IOException{
		if (file.length() > MAX_FILE_SIZE)return null;

		byte[] buffer = new byte[(int) file.length()];
		try (InputStream ios = new FileInputStream(file)) {
			if (ios.read(buffer) == -1) {
				throw new IOException(
						"EOF reached while trying to read the whole file");
			}
		}
		return buffer;
	}
	public void recordStart() {
		try {
			releaseRecorder();
			Log.i("AUDIO", "released");
			File record = new File(pathToAudio + "/record.3gpp");
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
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.chat_activity_menu, menu);
		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();

		Server.getInstance(this).setListener(() -> runOnUiThread(this::update));

		Tor.getInstance(this).setListener(() -> runOnUiThread(() -> {
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

		Notifier.getInstance(this).onResumeActivity();

		db.clearIncomingMessageCount(address);

		((TorStatusView) findViewById(R.id.torStatusView)).update();

		startService(new Intent(this, HostService.class));
	}

	void log(String s) {
		Log.i("Chat", s);
	}

	@Override
	protected void onPause() {
		db.clearIncomingMessageCount(address);
		Notifier.getInstance(this).onPauseActivity();
		timer.cancel();
		timer.purge();
		Server.getInstance(this).setListener(null);
		tor.setListener(null);
		client.setStatusListener(null);
		super.onPause();
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

	static class AudioHolder extends ChatHolder {
		private FloatingActionButton fab;
		private ProgressBar progress;

		public AudioHolder(View v) {
			super(v);
			fab = v.findViewById(R.id.playAudio);
			progress = v.findViewById(R.id.audioProgress);
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

		@Override
		public int getItemViewType(int position) {
			cursor.moveToFirst();
			cursor.moveToPosition(position);
			String type = cursor.getString(cursor.getColumnIndex("type"));
			return MessageType.getEnum(type).value;
		}

		@Override
		@NonNull
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			switch (MessageType.values()[viewType - 1]) {
				case TEXT:
					return new MessageHolder(getLayoutInflater().inflate(R.layout.item_message, parent, false));
				case AUDIO:
					return new AudioHolder(getLayoutInflater().inflate(R.layout.item_audio, parent, false));
				case VIDEO:
					return new VideoHolder(getLayoutInflater().inflate(R.layout.item_video, parent, false));
				case PHOTO:
					return new PhotoHolder(getLayoutInflater().inflate(R.layout.item_photo, parent, false));
				default:
					return null;
			}
		}

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


			if (sender.equals(tor.getID())) sender = "You";

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
			if (sender.equals(address)) {
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
					//status = "\u2713";
					//status = "Sent.";
				}
			}


			int color = pending ? 0xff000000 : 0xff888888;
			((ChatHolder) holder).time.setTextColor(color);
			((ChatHolder) holder).status.setTextColor(color);


			//holder.message.setText(content);
			if (holder instanceof VideoHolder) {
				Log.i("CONTENT", "video");
				File receivedVideo = new File(pathToPhotoAndVideo + "/received" + time.replaceAll(" ", "_") + ".3gp");
				Log.i("PATH_TO_PHOTO", pathToPhotoAndVideo + "/received" + time.replaceAll(" ", "_") + ".3gp");
				try {
					FileOutputStream out = new FileOutputStream(receivedVideo);
					out.write(content);
					out.flush();
					out.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				((VideoHolder) holder).video.setVideoPath(receivedVideo.getPath());
			} else if (holder instanceof PhotoHolder) {
				Log.i("CONTENT", "photo");
				File receivedPhoto = new File(pathToPhotoAndVideo + "/received" + time.replaceAll(" ", "_") + ".jpeg");
				Log.i("PATH_TO_PHOTO", pathToPhotoAndVideo + "/received" + time.replaceAll(" ", "_") + ".jpeg");
				try {
					FileOutputStream out = new FileOutputStream(receivedPhoto);
					out.write(content);
					out.flush();
					out.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				((PhotoHolder) holder).photo.setImageBitmap(BitmapFactory.decodeFile(receivedPhoto.getPath()));
			} else if (holder instanceof AudioHolder) {
				Log.i("AUDIO_ARRAY_LENGTH", String.valueOf(content.length));
				File receivedAudio = new File(pathToAudio + "/received" + time + ".3gpp");
				Log.i("PATH_TO_AUDIO", receivedAudio.getPath());
				try {
					FileOutputStream out = new FileOutputStream(receivedAudio);
					out.write(content);
					out.flush();
					out.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				((AudioHolder) holder).fab.setOnClickListener(view -> playStart(receivedAudio.getPath()));
			} else {
				Log.i("CONTENT", "content");
				((MessageHolder) holder).message.setMovementMethod(LinkMovementMethod.getInstance());
				((MessageHolder) holder).message.setText(Utils.linkify(ChatActivity.this, new String(content)));
			}

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
