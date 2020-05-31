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
							handle(ls);
							try {
								ls.close();
							} catch (IOException ex) {
								ex.printStackTrace();
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
				Tor tor = Tor.getInstance(context);
				for (int i = 0; i < 20 && !tor.isReady(); i++) {
					log("TorBlog not ready");
					try {
						Thread.sleep(5000);
					} catch (InterruptedException ex) {
						return;
					}
				}
				log("TorBlog ready");
				final Client client = Client.getInstance(context);
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

	private void handle(InputStream is, OutputStream os) throws Exception {
		while (true) {
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
							else
								throw new Exception("Error reading");
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
							case 3:
								db.addUnreadIncomingMessage(senderName, db.getContactName(senderName), Tor.getInstance(context).getID(), "photo", result, System.currentTimeMillis());
								if (listener != null) listener.onChange();
								if (db.hasContact(senderName))
									Notifier.getInstance(context).onMessage();
								Log.e("TEST", "take photo");
								break;
							case 4:
								db.addUnreadIncomingMessage(senderName, db.getContactName(senderName), Tor.getInstance(context).getID(), "audio", result, System.currentTimeMillis());
								if (listener != null) listener.onChange();
								if (db.hasContact(senderName))
									Notifier.getInstance(context).onMessage();
								Log.e("TEST", "take audio");
								break;
						}
					}
				}
			}
		}
	}

	private void handle(LocalSocket s) {
		InputStream is = null;
		OutputStream os = null;
		try {
			is = s.getInputStream();
		} catch (IOException ex) {
		}
		try {
			os = s.getOutputStream();
		} catch (IOException ex) {
		}
		if (is != null && os != null) {
			try {
				handle(is, os);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		if (is != null) {
			try {
				is.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		if (os != null) {
			try {
				os.close();
			} catch (IOException ex) {
				ex.printStackTrace();
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
