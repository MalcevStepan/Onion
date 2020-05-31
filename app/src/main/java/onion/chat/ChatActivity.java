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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

	void sendPendingAndUpdate() {
		//if(!client.isBusy()) {
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
				sendPendingAndUpdate();
				return;
			}

			String message = edit.getText().toString();
			message = message.trim();
			if (message.equals("")) return;

			db.addPendingOutgoingMessage(sender, address, message, "0", "0", "0");

			edit.setText("");

			sendPendingAndUpdate();

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
								sendPendingAndUpdate();
								break;
							}
							File audio = new File(pathToAudio + "/record.3gpp");
							byte[] data = new byte[(int) audio.length()];
							FileInputStream in = null;
							try {
								in = new FileInputStream(audio);
								in.read(data);
							} catch (IOException e) {
								e.printStackTrace();
							}
							if (in == null) break;
							db.addPendingOutgoingMessage(sender, address, "0", new String(data, StandardCharsets.UTF_8), "0", "0");
							sendPendingAndUpdate();
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
		FileInputStream fis = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		switch (requestCode) {
			case GALLERY_SUCCESS:
				if (resultCode == RESULT_OK) {
					if (data.getClipData() != null) {
						int count = data.getClipData().getItemCount();
						int currentItem = 0;
						while (currentItem < count) {
							Uri imageUri = data.getClipData().getItemAt(currentItem).getUri();
							try {
								fis = new FileInputStream(new File(String.valueOf(imageUri)));
								byte[] buf = new byte[1024];
								int n;
								while (-1 != (n = fis.read(buf)))
									baos.write(buf, 0, n);
							} catch (IOException e) {
								e.printStackTrace();
							}
							if (fis == null) {
								Log.i("PHOTO", "failed to convert photo");
								break;
							}
							db.addPendingOutgoingMessage(sender, address, "0", "0", "0", new String(baos.toByteArray(), StandardCharsets.UTF_8));
							sendPendingAndUpdate();
							recycler.smoothScrollToPosition(Math.max(0, cursor.getCount() - 1));
							rep = 0;
							currentItem = currentItem + 1;
						}
					} else if (data.getData() != null) {
						try {
							fis = new FileInputStream(new File(Objects.requireNonNull(data.getData().getPath())));
							byte[] buf = new byte[1024];
							int n;
							while (-1 != (n = fis.read(buf)))
								baos.write(buf, 0, n);
						} catch (IOException e) {
							e.printStackTrace();
						}
						if (fis == null) {
							Log.i("PHOTO", "failed to convert photo");
							break;
						}
						db.addPendingOutgoingMessage(sender, address, "0", "0", "0", new String(baos.toByteArray(), StandardCharsets.UTF_8));
						sendPendingAndUpdate();
						recycler.smoothScrollToPosition(Math.max(0, cursor.getCount() - 1));
						rep = 0;
					}
				}
				break;
			case CAPTURE_SUCCESS:
				if (resultCode == RESULT_OK) {
					Toast.makeText(this, "Video captured", Toast.LENGTH_SHORT).show();

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
					db.addPendingOutgoingMessage(sender, address, "0", "0", "0", new String(byteArray, StandardCharsets.UTF_8));
					sendPendingAndUpdate();
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
				sendPendingAndUpdate();
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

		sendPendingAndUpdate();


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
		private TextView message, time, status;
		private View left, right;
		private CardView card;
		private View abort;
		private FloatingActionButton fab;
		private ProgressBar progress;
		private VideoView video;
		private ImageView photo;

		public ChatHolder(View v) {
			super(v);
			message = v.findViewById(R.id.message);
			//sender = (TextView)v.findViewById(R.id.sender);
			time = v.findViewById(R.id.time);
			status = v.findViewById(R.id.status);
			left = v.findViewById(R.id.left);
			right = v.findViewById(R.id.right);
			card = v.findViewById(R.id.card);
			abort = v.findViewById(R.id.abort);
			fab = v.findViewById(R.id.playAudio);
			progress = v.findViewById(R.id.audioProgress);
			photo = v.findViewById(R.id.photoView);
			video = v.findViewById(R.id.videoView);
		}
	}

	class ChatAdapter extends RecyclerView.Adapter<ChatHolder> {

		@Override
		@NonNull
		public ChatHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return new ChatHolder(getLayoutInflater().inflate(R.layout.item_message, parent, false));
		}

		@Override
		public void onBindViewHolder(@NonNull ChatHolder holder, int position) {
			if (cursor == null) return;

			cursor.moveToFirst();
			cursor.moveToPosition(position);

			final long id = cursor.getLong(cursor.getColumnIndex("_id"));
			String content = cursor.getString(cursor.getColumnIndex("content"));
			String audioContent = cursor.getString(cursor.getColumnIndex("audioContent"));
			String videoContent = cursor.getString(cursor.getColumnIndex("videoContent"));
			String photoContent = cursor.getString(cursor.getColumnIndex("photoContent"));
			String sender = cursor.getString(cursor.getColumnIndex("sender"));
			String time = date(cursor.getString(cursor.getColumnIndex("time")));
			boolean pending = cursor.getInt(cursor.getColumnIndex("pending")) > 0;
			boolean tx = sender.equals(tor.getID());


			if (sender.equals(tor.getID())) sender = "You";

			if (tx) {
				holder.left.setVisibility(View.VISIBLE);
				holder.right.setVisibility(View.GONE);
			} else {
				holder.left.setVisibility(View.GONE);
				holder.right.setVisibility(View.VISIBLE);
			}

			if (pending)
				//holder.card.setCardElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics()));
				holder.card.setCardElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()));
			else
				holder.card.setCardElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, getResources().getDisplayMetrics()));


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
			holder.time.setTextColor(color);
			holder.status.setTextColor(color);


			//holder.message.setText(content);
			if (!videoContent.equals("0")) {
				Log.i("CONTENT", content);
				holder.message.setVisibility(View.VISIBLE);
				holder.progress.setVisibility(View.GONE);
				holder.fab.setVisibility(View.GONE);
				holder.message.setMovementMethod(LinkMovementMethod.getInstance());
				holder.message.setText(Utils.linkify(ChatActivity.this, "VIDEO"));
			} else if (!photoContent.equals("0")) {
				Log.i("CONTENT", content);
				holder.photo.setVisibility(View.VISIBLE);
				holder.message.setVisibility(View.GONE);
				holder.progress.setVisibility(View.GONE);
				holder.fab.setVisibility(View.GONE);
				File receivedPhoto = new File(pathToPhotoAndVideo + "/received" + time.replaceAll(" ", "_") + ".jpeg");
				Log.i("PATH_TO_PHOTO", pathToPhotoAndVideo + "/received" + time.replaceAll(" ", "_") + ".jpeg");
				FileOutputStream out = null;
				try {
					(out = new FileOutputStream(receivedPhoto)).write(photoContent.getBytes(StandardCharsets.UTF_8));
				} catch (IOException e) {
					e.printStackTrace();
				}
				holder.photo.setImageBitmap(BitmapFactory.decodeFile(receivedPhoto.getPath()));
			} else if (!audioContent.equals("0")) {
				Log.i("AUDIO_ARRAY_LENGTH", String.valueOf(audioContent.length()));
				holder.message.setVisibility(View.GONE);
				holder.progress.setVisibility(View.VISIBLE);
				holder.fab.setVisibility(View.VISIBLE);
				File receivedAudio = new File(pathToAudio + "/received" + time.replaceAll(" ", "_") + ".3gpp");
				Log.i("PATH_TO_AUDIO", pathToAudio + "/received" + time.replaceAll(" ", "_") + ".3gpp");
				FileOutputStream out = null;
				try {
					(out = new FileOutputStream(receivedAudio)).write(audioContent.getBytes(StandardCharsets.UTF_8));
				} catch (IOException e) {
					e.printStackTrace();
				}
				if (out == null) return;
				holder.fab.setOnClickListener(view -> playStart(pathToAudio + "/received" + time.replaceAll(" ", "_") + ".3gpp"));
			} else {
				Log.i("CONTENT", content);
				holder.message.setVisibility(View.VISIBLE);
				holder.progress.setVisibility(View.GONE);
				holder.fab.setVisibility(View.GONE);
				holder.message.setMovementMethod(LinkMovementMethod.getInstance());
				holder.message.setText(Utils.linkify(ChatActivity.this, content));
			}

			holder.time.setText(time);


			holder.status.setText(status);


			if (pending) {
				holder.abort.setVisibility(View.VISIBLE);
				holder.abort.setClickable(true);
				holder.abort.setOnClickListener(v -> {
					boolean ok = db.abortOutgoingMessage(id);
					update();
					Toast.makeText(ChatActivity.this, ok ? "Pending message aborted." : "Error: Message already sent.", Toast.LENGTH_SHORT).show();
				});
			} else {
				holder.abort.setVisibility(View.GONE);
				holder.abort.setClickable(false);
				holder.abort.setOnClickListener(null);
			}
		}

		@Override
		public int getItemCount() {
			return cursor != null ? cursor.getCount() : 0;
		}

	}

}
