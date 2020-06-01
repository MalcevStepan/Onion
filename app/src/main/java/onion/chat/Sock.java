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
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

public class Sock {

	static final int timeout = 60000;
	Socket sock;
	InputStream reader;
	OutputStream writer;

	public Sock(Context context, String host, int p) {

		log(host);

		sock = new Socket();

		try {

			Tor tor = Tor.getInstance(context);

			try {
				sock.connect(new InetSocketAddress("127.0.0.1", tor.getPort()), timeout);
			} catch (SocketTimeoutException ex3) {
				log("timeout");
				try {
					sock.close();
				} catch (IOException ex2) {
				}
			} catch (IOException ex) {
				log("failed to open socket");
				try {
					sock.close();
				} catch (IOException ex2) {
				}
			} catch (Exception ex) {
				log("sock connect err");
				try {
					sock.close();
				} catch (IOException ex2) {
				}
			}

			sock.setSoTimeout(timeout);

			// connect to proxy
			{
				//    ByteArrayOutputStream os = new ByteArrayOutputStream();

				OutputStream os = sock.getOutputStream();

				os.write(4); // socks 4a
				os.write(1); // stream

				//Log.i(TAG, "proto " + u.getProtocol());
				//if (p < 0 && u.getProtocol().equals("http")) p = 80;
				//if (p < 0 && u.getProtocol().equals("https")) p = 443;
				//Log.i(TAG, "port " + p);
				os.write((p >> 8) & 0xff);
				os.write((p >> 0) & 0xff);

				os.write(0);
				os.write(0);
				os.write(0);
				os.write(1);

				os.write(0);

				os.write(host.getBytes());
				os.write(0);

				os.flush();

				//    sock.
			}


			// get proxy response
			{
				InputStream is = sock.getInputStream();

				byte[] h = new byte[8];
				is.read(h);

				if (h[0] != 0) {
					log("unknown error");
					try {
						sock.close();
					} catch (IOException ex2) {
					}
					return;
				}

				if (h[1] != 0x5a) {

					if (h[1] == 0x5b) {
						log("request rejected or failed");
						try {
							sock.close();
						} catch (IOException ex2) {
						}
						return;
					}

					log("unknown error");
					try {
						sock.close();
					} catch (IOException ex2) {
					}
					return;
				}

			}


			reader = sock.getInputStream();
			writer = sock.getOutputStream();

		} catch (SocketTimeoutException ex3) {
			log("timeout");
			try {
				sock.close();
			} catch (IOException ex2) {
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			log("failed to connect to tor");
			try {
				sock.close();
			} catch (IOException ex2) {
			}
		}

	}

	private void log(String s) {
		if (!BuildConfig.DEBUG) return;
		Log.i("Sock", s);
	}

	public void writeBytes(byte[] array) {
		if (writer != null) {
			try {
				writer.write(ByteBuffer.allocate(4).putInt(array.length).array());
				writer.write(array);
			} catch (SocketTimeoutException ex) {
				log("timeout");
				try {
					sock.close();
				} catch (IOException ignored) {
				}
			} catch (Exception ignored) {
			}
		}
	}

	public boolean writeMessage(String sender, String message) {
		try {
			if (writer != null)
				writer.write(new byte[]{1});
			else
				return false;
			if (writeBytes(sender, message.getBytes())) {
				flush();
				return true;
			} else
				return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean writeAudio(String sender, byte[] audio) {
		try {
			if (writer != null)
				writer.write(new byte[]{4});
			else
				return false;
			if (writeBytes(sender, audio)) {
				flush();
				return true;
			} else
				return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean writeVideo(String sender, byte[] video) {
		try {
			if (writer != null)
				writer.write(new byte[]{2});
			else
				return false;
			if (writeBytes(sender, video)) {
				flush();
				return true;
			} else
				return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean writeImage(String sender, byte[] image) {
		try {
			if (writer != null)
				writer.write(new byte[]{3});
			else
				return false;
			if (writeBytes(sender, image)) {
				flush();
				return true;
			} else
				return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean writeAdd(String sender, String name) {
		try {
			if (writer != null)
				writer.write(new byte[]{0});
			else {
				close();
				return false;
			}
			if (writeBytes(sender, name.getBytes())) {
				flush();
				close();
				return true;
			} else {
				close();
				return false;
			}
		} catch (IOException e) {
			e.printStackTrace();
			close();
			return false;
		}
	}

	public boolean writeNewMsg(String sender, String time) {
		try {
			if (writer != null)
				writer.write(new byte[]{2});
			else {
				close();
				return false;
			}
			if (writeBytes(sender, time.getBytes())) {
				flush();
				close();
				return true;
			} else {
				close();
				return false;
			}
		} catch (IOException e) {
			e.printStackTrace();
			close();
			return false;
		}
	}

	public boolean writeBytes(String sender, byte[] data) {
		writeBytes(sender.getBytes());
		writeBytes(data);
		return true;
	}

	public boolean queryAndClose(String sender, String time) {
		boolean x = writeNewMsg(sender, time);
		close();
		return x;
	}

	public void flush() {
		if (writer != null) {
			try {
				writer.flush();
			} catch (SocketTimeoutException ex) {
				log("timeout");
				try {
					sock.close();
				} catch (IOException ignored) {
				}
			} catch (Exception ignored) {
			}
		}
	}

	public void close() {
		flush();

		if (sock != null)
			try {
				sock.close();
			} catch (Exception ignored) {
			}

		reader = null;
		writer = null;
		sock = null;
	}

	public boolean isClosed() {
		return sock.isClosed();
	}
}