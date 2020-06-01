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

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class Client {

	private static Client instance;
	Tor tor;
	Database db;

	Context context;
	AtomicInteger counter = new AtomicInteger();
	StatusListener statusListener;

	public Client(Context c) {
		context = c;
		tor = Tor.getInstance(context);
		db = Database.getInstance(context);
	}

	public static Client getInstance(Context context) {
		if (instance == null)
			instance = new Client(context.getApplicationContext());
		return instance;
	}

	private void log(String s) {
		if (!BuildConfig.DEBUG) return;
		Log.i("Client", s);
	}

	private Sock connect(String address) {
		log("connect to " + address);
		return new Sock(context, address + ".onion", Tor.getHiddenServicePort());
	}

	private boolean sendAdd(String receiver) {
		return connect(receiver).writeAdd(tor.getID(), db.getName());
	}

	private boolean sendMsg(Sock sock, String receiver, String content) {
		if (sock.isClosed()) {
			return false;
		}

		String sender = tor.getID();
		if (receiver.equals(sender)) return false;

		return sock.writeMessage(sender, content);
	}

	private boolean sendAudio(Sock sock, String receiver, byte[] audio) {
		if (sock.isClosed()) {
			return false;
		}

		String sender = tor.getID();
		if (receiver.equals(sender)) return false;

		return sock.writeAudio(sender, audio);

	}
	private boolean sendVideo(Sock sock, String receiver, byte[] video) {
		if (sock.isClosed()) {
			return false;
		}

		String sender = tor.getID();
		if (receiver.equals(sender)) return false;

		return sock.writeVideo(sender, video);

	}
	private boolean sendImage(Sock sock, String receiver, byte[] image) {
		if (sock.isClosed()) {
			return false;
		}

		String sender = tor.getID();
		if (receiver.equals(sender)) return false;

		return sock.writeImage(sender, image);

	}

	public void startSendPendingFriends() {
		log("start send pending friends");
		start(this::doSendPendingFriends);
	}

	public void doSendPendingFriends() {
		log("do send pending friends");
		Database db = Database.getInstance(context);
		Cursor cur = db.getReadableDatabase().query("contacts", null, "outgoing=?", new String[]{"1"}, null, null, null);
		while (cur.moveToNext()) {
			log("try to send friend request");
			String address = cur.getString(cur.getColumnIndex("address"));
			if (sendAdd(address)) {
				db.acceptContact(address);
				log("friend request sent");
			}
		}
		cur.close();
	}

	public void doSendAllPendingMessages() throws IOException {
		log("start send all pending messages");
		log("do send all pending messages");
		Database db = Database.getInstance(context);
		Cursor cur = db.getReadableDatabase().query("contacts", null, "outgoing=0 AND incoming=0", null, null, null, null);
		while (cur.moveToNext()) {
			log("try to send friend request");
			String address = cur.getString(cur.getColumnIndex("address"));
			doSendPendingMessages(address);
		}
		cur.close();
	}

	public byte[] read(File file) throws IOException {
		byte[] buffer = new byte[(int) file.length()];
		try (InputStream ios = new FileInputStream(file)) {
			if (ios.read(buffer) == -1) {
				throw new IOException(
						"EOF reached while trying to read the whole file");
			}
		}
		return buffer;
	}

	private void doSendPendingMessages(String address) throws IOException {
		log("do send pending messages");
		Database db = Database.getInstance(context);
		Cursor cur = db.getReadableDatabase().query("messages", null, "pending=? AND receiver=?", new String[]{"1", address}, null, null, null);
		log(String.valueOf(cur.getCount()));
		if (cur.getCount() > 0) {
			Sock sock = connect(address);
			while (cur.moveToNext()) {
				log("try to send message");
				String receiver = cur.getString(cur.getColumnIndex("receiver"));
				String type = cur.getString(cur.getColumnIndex("type"));
				byte[] content = cur.getBlob(cur.getColumnIndex("content"));
				String path = new String(content);
				switch (type) {
					case "msg":
						if (sendMsg(sock, receiver, new String(content))) {
							db.markMessageAsSent(cur.getLong(cur.getColumnIndex("_id")));
							log("message " + type + " sent");
						}
						break;
					case "photo":
						if (sendImage(sock, receiver, read(new File(ChatActivity.pathToPhotoAndVideo + path)))) {
							db.markMessageAsSent(cur.getLong(cur.getColumnIndex("_id")));
							log("message " + type + " sent");
						}
						break;
					case "audio":
						if (sendAudio(sock, receiver, read(new File(ChatActivity.pathToAudio + path)))) {
							db.markMessageAsSent(cur.getLong(cur.getColumnIndex("_id")));
							log("message " + type + " sent");
						}
						break;
					case "video":
						if (sendVideo(sock, receiver, read(new File(path)))) {
							db.markMessageAsSent(cur.getLong(cur.getColumnIndex("_id")));
							log("message " + type + " sent");
						}
						break;
				}
			}
			sock.close();
		}
		cur.close();
	}

	public synchronized void startSendPendingMessages(final String address) {
		log("start send pending messages");
		start(() -> {
			try {
				doSendPendingMessages(address);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	boolean isBusy() {
		return counter.get() > 0;
	}

	private void start(final Runnable runnable) {
		new Thread() {
			@Override
			public void run() {
				{
					int n = counter.incrementAndGet();
					StatusListener l = statusListener;
					if (l != null) l.onStatusChange(n > 0);
				}
				try {
					runnable.run();
				} finally {
					int n = counter.decrementAndGet();
					StatusListener l = statusListener;
					if (l != null) l.onStatusChange(n > 0);
				}
			}
		}.start();
	}

	public void setStatusListener(StatusListener statusListener) {
		this.statusListener = statusListener;
		if (statusListener != null) {
			statusListener.onStatusChange(counter.get() > 0);
		}
	}

	public interface StatusListener {
		void onStatusChange(boolean loading);
	}

	public boolean testIfServerIsUp() {
		Sock sock = connect(tor.getID());
		boolean ret = !sock.isClosed();
		sock.close();
		return ret;
	}

	public void doAskForNewMessages(String receiver) {
		log("ask for new msg");
		connect(receiver).queryAndClose(tor.getID(), String.valueOf(System.currentTimeMillis() / 60000 * 60000));
	}

	public void startAskForNewMessages(final String receiver) {
		start(() -> doAskForNewMessages(receiver));
	}

	public void askForNewMessages() {
		Cursor cur = db.getReadableDatabase().query("contacts", null, "incoming=0", null, null, null, null);
		while (cur.moveToNext()) {
			String receiver = cur.getString(cur.getColumnIndex("address"));
			doAskForNewMessages(receiver);
		}
		cur.close();
	}
}