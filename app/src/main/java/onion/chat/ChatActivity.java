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
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class ChatActivity extends AppCompatActivity {

	public static final int RECORD_AUDIO = 0;
	private MediaRecorder mediaRecorder;
	private MediaPlayer mediaPlayer;
	private File media;
	ChatAdapter adapter;
	RecyclerView recycler;
	Cursor cursor;
	Database db;
	Tor tor;
	String address;
	Client client;

	String myname = "", othername = "";

	long idLastLast = -1;

	long rep = 0;
	Timer timer;

	void update() {
		Cursor oldCursor = cursor;

		myname = db.getName().trim();
		othername = db.getContactName(address).trim();

		//cursor = db.getReadableDatabase().query("messages", null, "((sender=? AND receiver=?) OR (sender=? AND receiver=?)) AND sender != '' AND receiver != ''", new String[] { tor.getID(), address, address, tor.getID() }, null, null, "time ASC");

		String a = tor.getID();
		String b = address;
		//cursor = db.getReadableDatabase().query("messages", null, "(sender=? AND receiver=?) OR (sender=? AND receiver=?)", new String[] { a, b, b, a }, null, null, "time ASC");
		//cursor = db.getReadableDatabase().rawQuery("SELECT * FROM (SELECT * FROM messages WHERE ((sender=? AND receiver=?) OR (sender=? AND receiver=?)) ORDER BY time DESC LIMIT 64) ORDER BY time ASC", new String[]{a, b, b, a});
		cursor = db.getMessages(a, b);

		cursor.moveToLast();
		long idLast = -1;

		int i = cursor.getColumnIndex("_id");
		if (i >= 0 && cursor.getCount() > 0) {
			idLast = cursor.getLong(i);
		}

		//if(oldCursor == null || cursor.getCount() != oldCursor.getCount())
		if (idLast != idLastLast) {
			idLastLast = idLast;

			if (oldCursor == null || oldCursor.getCount() == 0)
				recycler.scrollToPosition(Math.max(0, cursor.getCount() - 1));
			else
				recycler.smoothScrollToPosition(Math.max(0, cursor.getCount() - 1));

			//client.startSendPendingMessages(address);
		}

		adapter.notifyDataSetChanged();

		if (oldCursor != null)
			oldCursor.close();

		findViewById(R.id.noMessages).setVisibility(cursor.getCount() > 0 ? View.GONE : View.VISIBLE);
	}

	void sendPendingAndUpdate() {
		//if(!client.isBusy()) {
		client.startSendPendingMessages(address);
		//}
		update();
	}

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

		media = new File(this.getCacheDir().getAbsolutePath() + "/Media/");
		media.mkdir();

		client = Client.getInstance(this);

		address = getIntent().getDataString();

		if (address.contains(":"))
			address = address.substring(address.indexOf(':') + 1);

		Log.i("ADDRESS", address);

		String name = db.getContactName(address);
		if (name.isEmpty()) {
			getSupportActionBar().setTitle(address);
		} else {
			getSupportActionBar().setTitle(name);
			getSupportActionBar().setSubtitle(address);
		}

		recycler = findViewById(R.id.recycler);

		recycler.setLayoutManager(new LinearLayoutManager(this));

		adapter = new ChatAdapter();
		recycler.setAdapter(adapter);

		final View micro = findViewById(R.id.micro);
		final View send = findViewById(R.id.send);
		final View attach = findViewById(R.id.attachment);
		final View redCircle = findViewById(R.id.redCircle);
		final EditText edit = findViewById(R.id.editmessage);
		final Animation redCircleAnim = AnimationUtils.loadAnimation(this, R.anim.red_circle_anim);
		// SENDING MESSAGE
		send.setOnClickListener(view -> {
			String sender = tor.getID();
			if (sender == null || sender.trim().equals("")) {
				sendPendingAndUpdate();
				return;
			}

			String message = edit.getText().toString();
			message = message.trim();
			if (message.equals("")) return;

			db.addPendingOutgoingMessage(sender, address, message);

			((EditText) findViewById(R.id.editmessage)).setText("");

			sendPendingAndUpdate();

			//recycler.scrollToPosition(cursor.getCount() - 1);

			recycler.smoothScrollToPosition(Math.max(0, cursor.getCount() - 1));

			rep = 0;
		});

		// RECORDING AND SENDING AUDIO MESSAGE
		micro.setOnTouchListener((view, motionEvent) -> {
			if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
				ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
						RECORD_AUDIO);
			else
				switch (motionEvent.getActionMasked()) {
					case MotionEvent.ACTION_DOWN:
						recordStart();
						redCircle.setVisibility(View.VISIBLE);
						redCircle.startAnimation(redCircleAnim);
						edit.setVisibility(View.INVISIBLE);
						attach.setVisibility(View.INVISIBLE);
						send.setVisibility(View.INVISIBLE);
						break;
					case MotionEvent.ACTION_UP:
						recordStop();
						redCircle.setVisibility(View.INVISIBLE);
						edit.setVisibility(View.VISIBLE);
						attach.setVisibility(View.VISIBLE);
						send.setVisibility(View.VISIBLE);
						break;
				}
			return false;
		});

		startService(new Intent(this, HostService.class));


		@SuppressLint("CutPasteId") final EditText editmessage = findViewById(R.id.editmessage);
		final float a = 0.5f;
		send.setAlpha(a);
		send.setClickable(false);
		editmessage.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (s.toString().trim().length() == 0) {
					send.setAlpha(a);
					send.setClickable(false);
				} else {
					send.setAlpha(0.7f);
					send.setClickable(true);
				}
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

	}

	public void recordStart() {
		try {
			releaseRecorder();
			Log.i("LOADING", "released");
			File record = new File(media.getAbsolutePath() + "/record.3gpp");
			Log.i("LOADING", "createdFile");
			if (record.exists()) {
				record.delete();
			}

			mediaRecorder = new MediaRecorder();
			Log.i("LOADING", "new media");
			mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
			mediaRecorder.setOutputFile(record.getAbsolutePath());
			Log.i("LOADING", "setOutput");
			mediaRecorder.prepare();
			Log.i("LOADING", "prepared");
			mediaRecorder.start();
			Log.i("LOADING", "start");
			Toast.makeText(this, "Recording", Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void recordStop() {
		if (mediaRecorder != null) {
			mediaRecorder.stop();
		}
	}

	public void playStart() {
		try {
			releasePlayer();
			mediaPlayer = new MediaPlayer();
			mediaPlayer.setDataSource(media.getAbsolutePath() + "/record.3gpp");
			mediaPlayer.prepare();
			mediaPlayer.start();
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

		Server.getInstance(this).setListener(() -> runOnUiThread(() -> update()));

		Tor.getInstance(this).setListener(() -> runOnUiThread(() -> {
			if (!client.isBusy()) {
				sendPendingAndUpdate();
			}
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
		public TextView message, time, status;
		public View left, right;
		public CardView card;
		public View abort;

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
		}
	}

	class ChatAdapter extends RecyclerView.Adapter<ChatHolder> {

		@Override
		public ChatHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			return new ChatHolder(getLayoutInflater().inflate(R.layout.item_message, parent, false));
		}

		@Override
		public void onBindViewHolder(ChatHolder holder, int position) {
			if (cursor == null) return;

			cursor.moveToFirst();
			cursor.moveToPosition(position);

			final long id = cursor.getLong(cursor.getColumnIndex("_id"));
			String content = cursor.getString(cursor.getColumnIndex("content"));
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


			holder.message.setMovementMethod(LinkMovementMethod.getInstance());
			holder.message.setText(Utils.linkify(ChatActivity.this, content));


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
