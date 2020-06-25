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
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;

import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity
		implements NavigationView.OnNavigationItemSelectedListener {

	Database db;
	Tor tor;
	TorStatusView torStatusView;
	TabLayout tabLayout;
	View contactPage, requestPage;
	RecyclerView contactRecycler, requestRecycler;
	View contactEmpty, requestEmpty;
	Cursor contactCursor, requestCursor;
	Client client;
	boolean isInActivity = true;
	private final int REQUEST_RECORD_AUDIO = 12;
	private final int RECORD_AUDIO = 0;
	private final int READ_EXTERNAL_STORAGE = 5;
	private final int ACCESS_NETWORK_STATE = 1;

	int REQUEST_QR = 12;

	void send() {
		Client.getInstance(this).startSendPendingFriends();
	}

	@SuppressLint("InflateParams")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestPermissions();

		db = Database.getInstance(this);
		tor = Tor.getInstance(this);
		client = Client.getInstance(this);

		setContentView(R.layout.activity_main);
		Toolbar toolbar = findViewById(R.id.toolbar);
		torStatusView = findViewById(R.id.torStatusView);
		setSupportActionBar(toolbar);

		FloatingActionButton fab = findViewById(R.id.fab);
		fab.setOnClickListener(view -> {
            /*Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();*/

            /*
            new AlertDialog.Builder(MainBlogActivity.this)
                    .setTitle(R.string.add_contact)
                    .setItems(new String[]{
                            getString(R.string.show_qr),
                            getString(R.string.scan_qr),
                            getString(R.string.enter_id),
                            getString(R.string.invite_friends),
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == 0) showQR();
                            if (which == 1) scanQR();
                            if (which == 2) addContact();
                            if (which == 3) inviteFriend();
                        }
                    })
                    .show();
                  */

			final Dialog[] d = new Dialog[1];

			View v = getLayoutInflater().inflate(R.layout.dialog_connect, null);
			((TextView) v.findViewById(R.id.id)).setText(Tor.getInstance(MainActivity.this).getID());
			v.findViewById(R.id.qr_show).setOnClickListener(v15 -> {
				d[0].cancel();
				showQR();
				sendStatus();
			});
			v.findViewById(R.id.qr_scan).setOnClickListener(v12 -> {
				d[0].cancel();
				scanQR();
				sendStatus();
			});
			v.findViewById(R.id.enter_id).setOnClickListener(v13 -> {
				d[0].cancel();
				addContact();
			});
			v.findViewById(R.id.share_id).setOnClickListener(v14 -> {
				d[0].cancel();
				inviteFriend();
			});
			d[0] = new AlertDialog.Builder(MainActivity.this)
					//.setTitle(R.string.add_contact)
					.setView(v)
					.show();

		});

		DrawerLayout drawer = findViewById(R.id.drawer_layout);
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
		drawer.setDrawerListener(toggle);
		toggle.syncState();

		NavigationView navigationView = findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);

		startService(new Intent(this, HostService.class));

        /*
        View empty = findViewById(R.id.nocontacts);
        ((ViewGroup)empty.getParent()).removeView(empty);
        ((ListView)findViewById(R.id.contacts)).setEmptyView(empty);
        */




        /*
        ((ListView)findViewById(R.id.contacts)).setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final String address = ((TextView) view.findViewById(R.id.address)).getText().toString();
                final String name = ((TextView) view.findViewById(R.id.name)).getText().toString();
                contactLongPress(address, name);
                return true;
            }
        });

        ((ListView)findViewById(R.id.contacts)).setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final String address = ((TextView) view.findViewById(R.id.address)).getText().toString();
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("chat:" + address), getApplicationContext(), ChatActivity.class));
            }
        });
        */

		//findViewById(R.id.myname).setOnClickListener(v -> changeName());
        /*findViewById(R.id.changename).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeName();
            }
        });*/


		//findViewById(R.id.myaddress).setOnClickListener(v -> showQR());


		contactPage = getLayoutInflater().inflate(R.layout.page_contacts, null);
		requestPage = getLayoutInflater().inflate(R.layout.page_requests, null);

		contactRecycler = contactPage.findViewById(R.id.contactRecycler);
		requestRecycler = requestPage.findViewById(R.id.requestRecycler);

		contactEmpty = contactPage.findViewById(R.id.contactEmpty);
		requestEmpty = requestPage.findViewById(R.id.requestEmpty);

		contactRecycler.setLayoutManager(new LinearLayoutManager(this));
		contactRecycler.setAdapter(new RecyclerView.Adapter<ContactViewHolder>() {
			@Override
			public ContactViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
				final ContactViewHolder viewHolder = new ContactViewHolder(getLayoutInflater().inflate(R.layout.item_contact, parent, false));
				viewHolder.itemView.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("chat:" + viewHolder.address.getText()), getApplicationContext(), ChatActivity.class)));
				viewHolder.itemView.setOnLongClickListener(v -> {
					contactLongPress(viewHolder.address.getText().toString(), viewHolder.name.getText().toString());
					return true;
				});
				return viewHolder;
			}

			@Override
			public void onBindViewHolder(ContactViewHolder holder, int position) {
				contactCursor.moveToPosition(position);
				holder.address.setText(contactCursor.getString(0));
				String name = contactCursor.getString(1);
				if (name == null || name.equals("")) name = "Anonymous";
				holder.name.setText(name);
				long n = contactCursor.getLong(2);
				if (n > 0) {
					holder.badge.setVisibility(View.VISIBLE);
					holder.count.setText(String.valueOf(n));
				} else {
					holder.badge.setVisibility(View.GONE);
				}
				//Log.i("contacts", "status is " + contactCursor.getInt(3));
				holder.status.setText(contactCursor.getInt(3) == 1 ? "Online" : "Offline");
			}

			@Override
			public int getItemCount() {
				return contactCursor != null ? contactCursor.getCount() : 0;
			}
		});

		requestRecycler.setLayoutManager(new LinearLayoutManager(this));
		requestRecycler.setAdapter(new RecyclerView.Adapter<ContactViewHolder>() {
			@Override
			public ContactViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
				final ContactViewHolder viewHolder = new ContactViewHolder(getLayoutInflater().inflate(R.layout.item_contact_request, parent, false));
				viewHolder.accept.setOnClickListener(v -> {
					String addr = viewHolder.address.getText().toString();
					db.acceptContact(addr);
					Client.getInstance(getApplicationContext()).startAskForNewMessages(addr);
					updateContactList();
				});
				viewHolder.decline.setOnClickListener(v -> {
					final String address = viewHolder.address.getText().toString();
					final String name = viewHolder.name.getText().toString();
					db.removeContact(address);
					updateContactList();
					Snackbar.make(findViewById(R.id.drawer_layout), R.string.contact_request_declined, Snackbar.LENGTH_LONG)
							.setAction(R.string.undo, v1 -> {
								db.addContact(address, false, true, name);
								sendStatus();
								updateContactList();
							})
							.show();
				});
				return viewHolder;
			}

			@Override
			public void onBindViewHolder(ContactViewHolder holder, int position) {
				requestCursor.moveToPosition(position);
				holder.address.setText(requestCursor.getString(0));
				String name = requestCursor.getString(1);
				if (name == null || name.equals("")) name = "Anonymous";
				holder.name.setText(name);
			}

			@Override
			public int getItemCount() {
				return requestCursor != null ? requestCursor.getCount() : 0;
			}
		});

		tabLayout = findViewById(R.id.tabLayout);


		final ViewPager viewPager = findViewById(R.id.viewPager);
		viewPager.setAdapter(new PagerAdapter() {
			@Override
			public int getCount() {
				return 2;
			}

			@Override
			public boolean isViewFromObject(View view, Object object) {
				return view == object;
			}

			@Override
			public Object instantiateItem(final ViewGroup container, int position) {
				View v = position == 0 ? contactPage : requestPage;
				container.addView(v);
				return v;
			}

			@Override
			public void destroyItem(ViewGroup container, int position, Object object) {
				container.removeView((View) object);
			}
		});
		tabLayout.setupWithViewPager(viewPager);
		//viewPager.addView(contactList);
		//viewPager.addView(requestList);


		//tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_people_white_36dp).setContentDescription("Contacts"));
		//tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_people_outline_white_36dp).setContentDescription("Requests"));

		//tabLayout.getTabAt(0).setIcon(R.drawable.ic_people_white_36dp).setContentDescription("Contacts");
		//tabLayout.getTabAt(1).setIcon(R.drawable.ic_people_outline_white_36dp).setContentDescription("Requests");

		Objects.requireNonNull(tabLayout.getTabAt(0)).setText(R.string.tab_contacts);
		Objects.requireNonNull(tabLayout.getTabAt(1)).setText(R.string.tab_requests);

        /*View v = tabLayout.getTabAt(1).getCustomView();
        Log.i("MainBlogActivity", v.toString());
        tabLayout.getTabAt(1).setCustomView(v);*/

        /*ImageView imageView = new ImageView(this);
        imageView.setImageResource(R.mipmap.ic_launcher);
        tabLayout.getTabAt(1).setCustomView(imageView);*/

		for (int i = 0; i < 2; i++) {
			View v = getLayoutInflater().inflate(R.layout.tab_header, null, false);
			((TextView) v.findViewById(R.id.text)).setText(Objects.requireNonNull(Objects.requireNonNull(tabLayout.getTabAt(i)).getText()).toString());
			((TextView) v.findViewById(R.id.badge)).setText("");
			v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			Objects.requireNonNull(tabLayout.getTabAt(i)).setCustomView(v);
		}

		tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
			@Override
			public void onTabSelected(TabLayout.Tab tab) {
				if (tab.getPosition() == 1) {
					db.clearNewRequests();
				}
				viewPager.setCurrentItem(tab.getPosition());
			}

			@Override
			public void onTabUnselected(TabLayout.Tab tab) {
			}

			@Override
			public void onTabReselected(TabLayout.Tab tab) {
			}
		});

		//tabLayout.getTabAt(0).setIcon(R.drawable.ic_people_white_36dp).setContentDescription("Contacts");
		//tabLayout.getTabAt(1).setIcon(R.drawable.ic_more_horiz_white_36dp).setContentDescription("Requests");

		sendStatus();
		updateContactList();


		handleIntent();

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_activity_menu, menu);
		return true;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == READ_EXTERNAL_STORAGE && !(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED))
			Toast.makeText(this, "The app was not allowed to read external storage. Hence, it cannot function properly. Please consider granting it this permission", Toast.LENGTH_LONG).show();
		if (requestCode == RECORD_AUDIO && !(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED))
			Toast.makeText(this, "The app was not allowed to record audio. Hence, it cannot function properly. Please consider granting it this permission", Toast.LENGTH_LONG).show();
		if (requestCode == REQUEST_RECORD_AUDIO && !(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED))
			Toast.makeText(this, "The app was not allowed to record audio. Hence, it cannot function properly. Please consider granting it this permission", Toast.LENGTH_LONG).show();
	}

	void sendStatus() {
		new Thread(() -> {
			while (isInActivity)
				if (tor.isReady()) {
					Log.i("MAIN", "CLEARING and UPDATE STATUS");
					client.clearStatus();
					contactCursor = db.getReadableDatabase().query("contacts", new String[]{"address", "name", "pending", "status"}, "incoming=0", null, null, null, "incoming, status, name, address");
					runOnUiThread(() -> Objects.requireNonNull(contactRecycler.getAdapter()).notifyDataSetChanged());
					client.sendStatusToFriends((byte) 1); // ONLINE
					try {
						Thread.sleep(30000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
		}).start();
	}

	void handleIntent() {

		Intent intent = getIntent();
		if (intent == null) {
			return;
		}

		Uri uri = intent.getData();
		if (uri == null) {
			return;
		}

		if (!Objects.equals(uri.getHost(), "chat.onion")) {
			return;
		}

		List<String> pp = uri.getPathSegments();
		String address = pp.size() > 0 ? pp.get(0) : null;
		String name = pp.size() > 1 ? pp.get(1) : "";
		if (address == null) {
			return;
		}

		addContact(address, name);

	}

	void updateContactList() {

		if (contactCursor != null) {
			contactCursor.close();
			contactCursor = null;
		}
		contactCursor = db.getReadableDatabase().query("contacts", new String[]{"address", "name", "pending", "status"}, "incoming=0", null, null, null, "incoming, status, name, address");
		Objects.requireNonNull(contactRecycler.getAdapter()).notifyDataSetChanged();
		contactEmpty.setVisibility(contactCursor.getCount() == 0 ? View.VISIBLE : View.INVISIBLE);

		if (requestCursor != null) {
			requestCursor.close();
			requestCursor = null;
		}
		requestCursor = db.getReadableDatabase().query("contacts", new String[]{"address", "name"}, "incoming!=0", null, null, null, "incoming, name, address");
		Objects.requireNonNull(requestRecycler.getAdapter()).notifyDataSetChanged();
		requestEmpty.setVisibility(requestCursor.getCount() == 0 ? View.VISIBLE : View.INVISIBLE);


		//updateBadge();

		int newRequests = requestCursor.getCount();
		((TextView) Objects.requireNonNull(Objects.requireNonNull(tabLayout.getTabAt(1)).getCustomView()).findViewById(R.id.badge)).setText(newRequests > 0 ? "" + newRequests : "");

	}

    /*void updateBadge() {
        int newRequests = db.getNewRequests();
        ((TextView)tabLayout.getTabAt(1).getCustomView().findViewById(R.id.badge)).setText(newRequests > 0 ? "" + newRequests : "");
    }*/

	void contactLongPress(final String address, final String name) {
		View v = getLayoutInflater().inflate(R.layout.dialog_contact, null);
		((TextView) v.findViewById(R.id.name)).setText(name);
		((TextView) v.findViewById(R.id.address)).setText(address);
		final Dialog dlg = new AlertDialog.Builder(MainActivity.this)
				.setView(v)
				.create();

		v.findViewById(R.id.openchat).setOnClickListener(v12 -> {
			dlg.hide();
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("chat:" + address), getApplicationContext(), ChatActivity.class));
		});
		v.findViewById(R.id.changename).setOnClickListener(v14 -> {
			dlg.hide();
			changeContactName(address, name);
		});
		v.findViewById(R.id.copyid).setOnClickListener(v13 -> {
			dlg.hide();
			((android.content.ClipboardManager) Objects.requireNonNull(getSystemService(Context.CLIPBOARD_SERVICE))).setText(address);
			snack(getString(R.string.id_copied_to_clipboard) + address);
		});
		v.findViewById(R.id.delete).setOnClickListener(v1 -> {
			dlg.hide();
			new AlertDialog.Builder(MainActivity.this)
					.setTitle(R.string.delete_contact_q)
					.setMessage(String.format(getString(R.string.really_delete_contact), address))
					.setPositiveButton(R.string.yes, (dialog, which) -> {
						db.removeContact(address);
						updateContactList();
					})
					.setNegativeButton(R.string.no, (dialog, which) -> {
					})
					.show();
			//db.removeContact(address);
			//updateContactList();
		});

		dlg.show();
	}


    /*void updateContactList() {
        ((ListView)findViewById(R.id.contacts)).setAdapter(new CursorAdapter(
                this,
                db.getReadableDatabase().query("contacts", null, null, null, null, null, "name, address")
        ) {
            @Override
            public void bindView(View view, final Context context, Cursor cursor) {

                final String address = cursor.getString(cursor.getColumnIndex("address"));

                String name = cursor.getString(cursor.getColumnIndex("name"));
                if(name == null || name.equals("")) name = "Anonymous";

                ((TextView)view.findViewById(R.id.address)).setText(address);
                ((TextView)view.findViewById(R.id.name)).setText(name);

                if(view.findViewById(R.id.accept) != null) {
                    view.findViewById(R.id.accept).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Database.getInstance(context).acceptContact(address);
                            update();
                        }
                    });
                }

                if(view.findViewById(R.id.decline) != null) {
                    view.findViewById(R.id.decline).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Database.getInstance(context).removeContact(address);
                            update();
                        }
                    });
                }
            }

            @Override
            public View newView(Context context, Cursor cursor, ViewGroup parent) {
                if(cursor.getInt(cursor.getColumnIndex("incoming")) != 0)
                    return LayoutInflater.from(context).inflate(R.layout.item_contact_request, parent, false);
                else
                    return LayoutInflater.from(context).inflate(R.layout.item_contact, parent, false);
            }
        });
    }*/


	void changeContactName(final String address, final String name) {
		final FrameLayout view = new FrameLayout(this);
		final EditText editText = new EditText(this);
		editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
		editText.setSingleLine();
		editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
		view.addView(editText);
		int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
		;
		view.setPadding(padding, padding, padding, padding);
		editText.setText(name);
		new AlertDialog.Builder(this)
				.setTitle(R.string.title_change_alias)
				.setView(view)
				.setPositiveButton(R.string.apply, (dialog, which) -> {
					db.setContactName(address, editText.getText().toString());
					update();
					snack(getString(R.string.snack_alias_changed));
				})
				.setNegativeButton(R.string.cancel, (dialog, which) -> {
				}).show();
	}

	void update() {
		//((TextView) findViewById(R.id.myaddress)).setText(tor.getID());
		//((TextView) findViewById(R.id.myname)).setText(db.getName().trim().isEmpty() ? "Anonymous" : db.getName());
		//updateBadge();
		runOnUiThread(this::updateContactList);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i("MAIN", "ONDESTROY");
		client.sendStatusToFriends((byte) 0);
		isInActivity = false;
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i("MAIN", "onResume");
		tor.setListener(() -> {
			update();
			send();
		});
		Server.getInstance(this).setListener(this::update);
		update();
		send();

		Notifier.getInstance(this).onResumeActivity();

		torStatusView.update();

		startService(new Intent(this, HostService.class));
	}

	@Override
	protected void onPause() {
		Log.i("MAIN", "ONPAUSE");
		isInActivity = false;
		Notifier.getInstance(this).onPauseActivity();
		tor.setListener(null);
		Server.getInstance(this).setListener(null);
		super.onPause();
	}

	@Override
	public void onBackPressed() {
		DrawerLayout drawer = findViewById(R.id.drawer_layout);
		if (drawer.isDrawerOpen(GravityCompat.START)) {
			drawer.closeDrawer(GravityCompat.START);
		} else
			super.onBackPressed();
	}

    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    */

    /*String makeUrl() {
        return "chat.onion://"
    }*/

	@SuppressWarnings("StatementWithEmptyBody")
	@Override
	public boolean onNavigationItemSelected(MenuItem item) {
		// Handle navigation view item clicks here.
		int id = item.getItemId();

        /*if (id == R.id.nav_camara) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }*/

		if (id == R.id.changealias) {
			changeName();
			return true;
		}

		if (id == R.id.qr_show) {
			showQR();
			return true;
		}

		if (id == R.id.qr_scan) {
			scanQR();
		}

		if (id == R.id.share_id) {
			inviteFriend();
		}

		if (id == R.id.copy_id) {
			((android.content.ClipboardManager) Objects.requireNonNull(getSystemService(Context.CLIPBOARD_SERVICE))).setText(tor.getID());
			snack(getString(R.string.id_copied_to_clipboard) + tor.getID());
			return true;
		}

		if (id == R.id.rate) {
			try {
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName()));
				PackageManager pm = getPackageManager();
				for (ApplicationInfo packageInfo : pm.getInstalledApplications(0)) {
					if (packageInfo.packageName.equals("com.android.vending"))
						intent.setPackage("com.android.vending");
				}
				startActivity(intent);
			} catch (Throwable t) {
				Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
			}
		}

		if (id == R.id.about) {
			showAbout();
		}

		if (id == R.id.enter_id) {
			addContact();
		}

		if (id == R.id.settings) {
			startActivity(new Intent(this, SettingsActivity.class));
		}


		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		drawer.closeDrawer(GravityCompat.START);
		return true;
	}

	void inviteFriend() {
		Intent intent = new Intent();
		intent.setAction(Intent.ACTION_SEND);

		String url = "https://play.google.com/store/apps/details?id=" + getPackageName();

		//intent.putExtra(Intent.EXTRA_TEXT, String.format("Add me on Chat.onion!\n\nID: %s\n\n%s", tor.getID(), url));

		//intent.putExtra(Intent.EXTRA_TEXT, String.format(getString(R.string.invitation_text), url,  tor.getID()));

		intent.putExtra(Intent.EXTRA_REFERRER, url);
		intent.putExtra("customAppUri", url);

		String msg = String.format(getString(R.string.invitation_text), url, tor.getID(), Uri.encode(db.getName()));

		Log.i("Message", msg.replace('\n', ' '));

		intent.putExtra(Intent.EXTRA_TEXT, msg);
		intent.setType("text/plain");

        /*intent.setType("text/html");
        intent.putExtra(
                Intent.EXTRA_TEXT,
                Html.fromHtml(new StringBuilder()
                        .append(String.format("<p><a href=\"%s\">%s</a></p>\n\n", url, url))
                        .append(String.format("<p>Let's chat securely via Chat.onion!</p>\n\n"))
                        .append(String.format("<p>My Chat.onion ID: %s</p>\n\n", tor.getID()))
                        .toString())
        );*/


		startActivity(intent);
	}

	void scanQR() {
		Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		startActivityForResult(takePictureIntent, REQUEST_QR);
	}

	void showAbout() {
		new AlertDialog.Builder(this)
				.setTitle(getString(R.string.app_name))
				//.setMessage(BuildConfig.APPLICATION_ID + "\n\nVersion: " + BuildConfig.VERSION_NAME)
				.setMessage("Version: " + BuildConfig.VERSION_NAME)
				.setNeutralButton(R.string.libraries, (dialog, which) -> showLibraries())
				.setPositiveButton(R.string.ok, (dialog, which) -> {
				})
				.show();
	}

	void showLibraries() {
		final String[] items;
		try {
			items = getResources().getAssets().list("licenses");
		} catch (IOException ex) {
			throw new Error(ex);
		}
		new AlertDialog.Builder(this)
				.setTitle("Third party software used in this app (click to view license)")
				.setItems(items, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						showLicense(items[which]);
					}
				})
				.show();
	}

	void showLicense(String name) {
		String text;
		try {
			text = Utils.str(getResources().getAssets().open("licenses/" + name));
		} catch (IOException ex) {
			throw new Error(ex);
		}
		new AlertDialog.Builder(this)
				.setTitle(name)
				.setMessage(text)
				.show();
	}

	void showQR() {
		String name = db.getName();

		//String txt = "chat-onion://" + tor.getID();
		//if (!name.isEmpty()) txt += "/" + name;

		String txt = "chat.onion " + tor.getID() + " " + name;

		QRCode qr;

		try {
			//qr = Encoder.encode(txt, ErrorCorrectionLevel.H);
			qr = Encoder.encode(txt, ErrorCorrectionLevel.M);
		} catch (Exception ex) {
			throw new Error(ex);
		}

		ByteMatrix mat = qr.getMatrix();
		int width = mat.getWidth();
		int height = mat.getHeight();
		int[] pixels = new int[width * height];
		for (int y = 0; y < height; y++) {
			int offset = y * width;
			for (int x = 0; x < width; x++) {
				pixels[offset + x] = mat.get(x, y) != 0 ? Color.BLACK : Color.WHITE;
			}
		}
		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

		bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() * 8, bitmap.getHeight() * 8, false);

		ImageView view = new ImageView(this);
		view.setImageBitmap(bitmap);

		int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
		view.setPadding(pad, pad, pad, pad);

		Rect displayRectangle = new Rect();
		Window window = getWindow();
		window.getDecorView().getWindowVisibleDisplayFrame(displayRectangle);
		int s = (int) (Math.min(displayRectangle.width(), displayRectangle.height()) * 0.9);
		view.setMinimumWidth(s);
		view.setMinimumHeight(s);
		new AlertDialog.Builder(this)
				//.setMessage(txt)
				.setView(view)
				.show();
	}

	private void requestPermissions(){
		if (ContextCompat.checkSelfPermission(this,
				Manifest.permission.RECORD_AUDIO)
				!= PackageManager.PERMISSION_GRANTED)
			ActivityCompat.requestPermissions(this,
					new String[]{Manifest.permission.RECORD_AUDIO},
					REQUEST_RECORD_AUDIO);
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
					READ_EXTERNAL_STORAGE);
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
					RECORD_AUDIO);
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED)
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_NETWORK_STATE},
					ACCESS_NETWORK_STATE);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode != RESULT_OK)
			return;
		if (requestCode == REQUEST_QR) {
			Bitmap bitmap = (Bitmap) Objects.requireNonNull(data.getExtras()).get("data");

			assert bitmap != null;
			int width = bitmap.getWidth(), height = bitmap.getHeight();
			int[] pixels = new int[width * height];
			bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
			bitmap.recycle();
			RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
			BinaryBitmap bBitmap = new BinaryBitmap(new HybridBinarizer(source));
			MultiFormatReader reader = new MultiFormatReader();

            /*try {
                Result result = reader.decode(bBitmap);
                String id = result.getText();
                Log.i("ID", id);
                if(id.length() == 16) {
                    if (!db.addContact(id, true, false)) {
                        snack("Failed to add contact");
                        return;
                    }
                    snack("Contact added");
                    updateContactList();
                    send();
                    return;
                }
            } catch(NotFoundException ex) {
                ex.printStackTrace();
            }
            snack("QR Code Invalid");
            */


			try {
				Result result = reader.decode(bBitmap);
				String str = result.getText();
				Log.i("ID", str);

				String[] tokens = str.split(" ", 3);

				if (tokens.length < 2 || !tokens[0].equals("chat.onion")) {
					snack(getString(R.string.qr_invalid));
					return;
				}

				String id = tokens[1].toLowerCase();

				if (id.length() != 16) {
					snack(getString(R.string.qr_invalid));
					return;
				}

				if (db.hasContact(id)) {
					snack(getString(R.string.contact_already_added));
					return;
				}
				String name = "";
				if (tokens.length > 2) {
					name = tokens[2];
				}
				addContact(id, name);
			} catch (Exception ex) {
				snack(getString(R.string.qr_invalid));
				ex.printStackTrace();
			}


		}
	}

	void snack(String s) {
		//Snackbar.make(findViewById(R.id.toolbar), s, Snackbar.LENGTH_SHORT).show();
		Snackbar.make(findViewById(R.id.drawer_layout), s, Snackbar.LENGTH_SHORT).show();
	}

	void changeName() {
		final FrameLayout view = new FrameLayout(this);
		final EditText editText = new EditText(this);
		editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
		editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
		editText.setSingleLine();
		editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
		view.addView(editText);
		int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
		;
		view.setPadding(padding, padding, padding, padding);
		editText.setText(db.getName());
		new AlertDialog.Builder(this)
				.setTitle(R.string.title_change_alias)
				.setView(view)
				.setPositiveButton(R.string.apply, (dialog, which) -> {
					db.setName(editText.getText().toString().trim());
					update();
					snack(getString(R.string.snack_alias_changed));
				})
				.setNegativeButton(R.string.cancel, (dialog, which) -> {
				}).show();
	}

	void addContact() {
		addContact("", "");
	}

	void addContact(String id, String alias) {

		final View view = getLayoutInflater().inflate(R.layout.dialog_add, null);
		final EditText idEd = view.findViewById(R.id.add_id);
		idEd.setText(id);
		final EditText aliasEd = view.findViewById(R.id.add_alias);
		aliasEd.setText(alias);
		new AlertDialog.Builder(this)
				.setTitle(R.string.title_add_contact)
				.setView(view)
				.setPositiveButton(R.string.ok, (dialog, which) -> {
					String id1 = idEd.getText().toString().trim();
					if (id1.length() != 16) {
						snack(getString(R.string.invalid_id));
						return;
					}
					if (id1.equals(tor.getID())) {
						snack(getString(R.string.cant_add_self));
						return;
					}
					if (!db.addContact(id1, true, false, aliasEd.getText().toString().trim())) {
						snack(getString(R.string.failed_to_add_contact));
						return;
					}
					snack(getString(R.string.contact_added));
					sendStatus();
					updateContactList();
					send();
					Objects.requireNonNull(tabLayout.getTabAt(0)).select();
				})
				.setNegativeButton(R.string.cancel, (dialog, which) -> {
				}).show();

	}

	static class ContactViewHolder extends RecyclerView.ViewHolder {
		TextView address, name, status;
		View accept, decline;
		View badge;
		TextView count;

		public ContactViewHolder(View view) {
			super(view);
			address = view.findViewById(R.id.address);
			name = view.findViewById(R.id.name);
			accept = view.findViewById(R.id.accept);
			decline = view.findViewById(R.id.decline);
			status = view.findViewById(R.id.status);
			badge = view.findViewById(R.id.badge);
			if (badge != null) count = view.findViewById(R.id.count);
		}
	}
}