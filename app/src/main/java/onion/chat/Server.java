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
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Server {

	private static Server instance;
	String socketName;
	private Context context;
	private String TAG = "ServerBlog";
	private Listener listener = null;
	private LocalServerSocket serverSocket;
	private LocalSocket ls;
	private Tor tor;
	private Client client;
	private String sender = "";

	public Server(Context c) {
		context = c;
		log("start listening");
		try {
			socketName = new File(context.getFilesDir(), "socket").getAbsolutePath();
			ls = new LocalSocket();
			ls.bind(new LocalSocketAddress(socketName, LocalSocketAddress.Namespace.FILESYSTEM));
			serverSocket = new LocalServerSocket(ls.getFileDescriptor());
			socketName = "unix:" + socketName;
			log(socketName);
		} catch (Exception ex) {
			throw new Error(ex);
		}
		log("started listening");
		new Thread() {
			@Override
			public void run() {
				while (true) {
					LocalServerSocket ss = serverSocket;
					if (ss == null) break;
					log("waiting for connection");
					final LocalSocket ls;
					try {
						ls = ss.accept();
						if (BuildConfig.DEBUG) log("accept");
					} catch (IOException ex) {
						throw new Error(ex);
					}
					if (ls == null) {
						log("no socket");
						continue;
					}
					log("new connection");
					new Thread() {
						@Override
						public void run() {
							try {
								handle(ls.getInputStream());
								ls.close();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}.start();
				}
			}
		}.start();
		new Thread() {
			@Override
			public void run() {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException ex) {
					return;
				}
				tor = Tor.getInstance(context);
				for (int i = 0; i < 20 && !tor.isReady(); i++) {
					log("TorBlog not ready");
					try {
						Thread.sleep(5000);
					} catch (InterruptedException ex) {
						return;
					}
				}
				log("TorBlog ready");
				client = Client.getInstance(context);
				for (int i = 0; i < 20 && !client.testIfServerIsUp(); i++) {
					log("Hidden server descriptors not yet propagated");
					try {
						Thread.sleep(5000);
					} catch (InterruptedException ex) {
						return;
					}
				}
				log("Hidden service registered");
				client.askForNewMessages();
			}
		}.start();
	}

	public static Server getInstance(Context context) {
		if (instance == null) {
			instance = new Server(context.getApplicationContext());
		}
		return instance;
	}

	private void log(String s) {
		if (!BuildConfig.DEBUG) return;
		Log.i(TAG, s);
	}

	public void setListener(Listener l) {
		listener = l;
		if (listener != null)
			listener.onChange();
	}

	private void handle(InputStream is) throws Exception {
		byte[] info = new byte[1];
		if (is.read(info) == 1) {
			String senderName = null;
			byte[] senderNameLength = new byte[4];
			if (is.read(senderNameLength) == 4) {
				int length = ByteBuffer.wrap(senderNameLength).getInt();
				if (length > 0) {
					byte[] result = new byte[length];
					while (length > 0) {
						int k;
						if ((k = is.read(result, result.length - length, length)) > 0)
							length -= k;
						else throw new Exception("Error reading");
					}
					senderName = new String(result);
				}
			}

			byte[] bLength = new byte[4];
			if (is.read(bLength) == 4) {
				int length = ByteBuffer.wrap(bLength).getInt();
				if (length > 0) {
					byte[] result = new byte[length];
					while (length > 0) {
						int k;
						if ((k = is.read(result, result.length - length, length)) > 0)
							length -= k;
						else
							throw new Exception("Error reading");
					}

					Database db = Database.getInstance(context);
					FileOutputStream out;
					long time = System.currentTimeMillis();
					switch (info[0]) {
						case 0:
							String name = new String(result).trim();
							if (!name.equals("")) {
								db.addContact(senderName, false, true, name);
								if (listener != null) listener.onChange();
							}
							Log.e("TEST", name);
							break;
						case 1:
							db.addUnreadIncomingMessage(senderName, db.getContactName(senderName), Tor.getInstance(context).getID(), "msg", result, System.currentTimeMillis());
							if (listener != null) listener.onChange();
							if (db.hasContact(senderName))
								Notifier.getInstance(context).onMessage();
							Log.e("TEST", new String(result));
							break;
						case 2:
							Client.getInstance(context).startSendPendingMessages(senderName);
							break;
						case 5:
							new File(ChatActivity.pathToPhotoAndVideo + "/" + senderName).mkdir();
							File video = new File(ChatActivity.pathToPhotoAndVideo + "/" + senderName + "/video" + time + ".mp4");
							out = new FileOutputStream(video);
							out.write(result);
							out.flush();
							out.close();
							db.addUnreadIncomingMessage(senderName, db.getContactName(senderName), Tor.getInstance(context).getID(), "video", video.getPath().getBytes(), System.currentTimeMillis());
							if (listener != null) listener.onChange();
							if (db.hasContact(senderName))
								Notifier.getInstance(context).onMessage();
							Log.e("TEST", "take video");
							Client.getInstance(context).startSendPendingMessages(senderName);
							break;
						case 3:
							new File(ChatActivity.pathToPhotoAndVideo + "/" + senderName).mkdir();
							File photo = new File(ChatActivity.pathToPhotoAndVideo + "/" + senderName + "/photo" + time + ".jpeg");
							out = new FileOutputStream(photo);
							out.write(result);
							out.flush();
							out.close();
							db.addUnreadIncomingMessage(senderName, db.getContactName(senderName), Tor.getInstance(context).getID(), "photo", photo.getPath().getBytes(), System.currentTimeMillis());
							if (listener != null) listener.onChange();
							if (db.hasContact(senderName))
								Notifier.getInstance(context).onMessage();
							Log.e("TEST", "take photo");
							break;
						case 4:
							new File(ChatActivity.pathToAudio + "/" + senderName).mkdir();
							File audio = new File(ChatActivity.pathToAudio + "/" + senderName + "/received" + time + ".3gpp");
							out = new FileOutputStream(audio);
							out.write(result);
							out.flush();
							out.close();
							db.addUnreadIncomingMessage(senderName, db.getContactName(senderName), Tor.getInstance(context).getID(), "audio", audio.getPath().getBytes(), System.currentTimeMillis());
							if (listener != null) listener.onChange();
							if (db.hasContact(senderName))
								Notifier.getInstance(context).onMessage();
							Log.e("TEST", "take audio");
							break;
						case 6:
							Log.i("SERVER", "GETTING STATUS");
							assert senderName != null;
							db.setStatus(senderName, result[0]);
							if (listener != null) listener.onChange();
							if (result[0] == 1) {
								Log.i("SERVER", "SENDING STATUS");
								client.sendStatusToFriend(senderName, (byte) 1);//ONLINE
							}
							break;
						case 7:
							db.setStatus(senderName, result[0]);
							if (listener != null) listener.onChange();
							break;
						case -2:
							db.addUnreadIncomingMessage(senderName, db.getContactName(senderName), Tor.getInstance(context).getID(), "incomingCall", result, System.currentTimeMillis());
							if (listener != null) listener.onChange();
							if (db.hasContact(senderName))
								Notifier.getInstance(context).onMessage();
							break;
					}
					is.close();
					is = null;
					System.gc();
				}
			}
		}
	}

	public String getSocketName() {
		return socketName;
	}

	public interface Listener {
		void onChange();
	}
}
