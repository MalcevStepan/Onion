/*
 * Blog.onion
 *
 * http://play.google.com/store/apps/details?id=onion.blog
 * http://onionapps.github.io/Blog.onion/
 * http://github.com/onionApps/Blog.onion
 *
 * Author: http://github.com/onionApps - http://jkrnk73uid7p5thz.onion - bitcoin:1kGXfWx8PHZEVriCNkbP5hzD15HS4AyKf
 */

package onion.blog;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class MainBlogActivity extends AppCompatActivity {

    WebView webView;

    Blog blog;

    FloatingActionButton fab;
    String blogUrl = "http://blog";
    int REQUEST_CAMERA = 24;
    int REQUEST_PICKER = 25;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        blog = Blog.getInstance(this);

        startService(new Intent(this, HostServiceBlog.class));

        setContentView(onion.blog.R.layout.activity_blog_main);
        Toolbar toolbar = (Toolbar) findViewById(onion.blog.R.id.blog_toolbar);
        setSupportActionBar(toolbar);

        fab = (FloatingActionButton) findViewById(onion.blog.R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                //        .setAction("Action", null).show();


                String editId = getEditId(webView.getUrl());
                if (editId != null) {
                    editPost(editId);
                } else {
                    addPost();
                }

            }
        });

        webView = (WebView) findViewById(onion.blog.R.id.webView);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void delete(final String id) {
                if (id == null) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(MainBlogActivity.this)
                                .setTitle("Удалить пост?")
                                .setMessage("Вы действительно хотите удалить этот пост?")
                                .setPositiveButton("Да", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            blog.deletePost(id);
                                            Snackbar.make(webView, "Пост удален.", Snackbar.LENGTH_SHORT).show();
                                        } catch (Exception ex) {
                                            Snackbar.make(webView, "Не удалось удалить пост.", Snackbar.LENGTH_SHORT).show();
                                        }
                                        webView.reload();
                                    }
                                })
                                .setNegativeButton("Нет", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                    }
                                })
                                .show();
                    }
                });
            }

            @JavascriptInterface
            public void edit(final String id) {
                if (id == null) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //addPost(null, null, null, id);
                        editPost(id);
                    }
                });
            }

            @JavascriptInterface
            public void title() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        editTitle();
                    }
                });
            }
        }, "cms");

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setPluginState(WebSettings.PluginState.OFF);
        webView.getSettings().setBlockNetworkLoads(true);
        //webView.getSettings().setBlockNetworkImage(true);
        webView.getSettings().setAppCacheEnabled(false);
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);

        webView.setWebChromeClient(new WebChromeClient() {


        });
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                updateUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                updateUrl(url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {


                String path = "";
                try {
                    path = new URL(url).getPath();
                } catch (MalformedURLException ex) {
                    ex.printStackTrace();
                }


                Response response = blog.getResponse(path, true);
                return new WebResourceResponse(
                        response.getMimeType(),
                        response.getCharset(),
                        //response.getStatusCode(),
                        //response.getStatusMessage(),
                        //new HashMap<String, String>(),
                        new ByteArrayInputStream(response.getData())
                );


                /*
                try {

                    LocalSocket s = new LocalSocket();
                    s.connect(new LocalSocketAddress(ServerBlog.getExistingInstance().getSocketName(), LocalSocketAddress.Namespace.FILESYSTEM));
                    OutputStreamWriter w = new OutputStreamWriter(s.getOutputStream());
                    w.write("GET " + path + " HTTP/1.0\r\n");
                    w.write("\r\n");
                    w.flush();
                    return new WebResourceResponse(
                            "text/html",
                            "utf-8",
                            s.getInputStream());

                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                return new WebResourceResponse(
                        "text/html",
                        "utf-8",
                        new ByteArrayInputStream("Error".getBytes()));
                */

            }
        });

        goHome();
    }

    void goHome() {
        webView.loadUrl(blogUrl);
    }

    String getEditId(String url) {

        if(url == null) url = "";

        String p = Uri.parse(url).getPath();

        Log.i("path", p);

        String[] pp = p.split("/");

        for (String s : pp) {
            Log.i("tok", pp.length + " " + s);
        }

        boolean x = (pp.length == 3 && pp[0].equals("") && pp[1].matches("^[0-9]+$") && pp[2].matches("^[a-zA-Z0-9-]+\\.htm$"));

        //boolean x = p.matches("^/[0-9]+/[a-zA-Z0-9-]+\\.htm$");

        if (x) {
            Log.i("editid", "editid");
            return pp[1];
        }

        return null;
    }

    void updateUrl(String url) {

        if(url == null) url = "";

        String editId = getEditId(url);

        fab.setImageResource(
                editId == null ?
                        onion.blog.R.drawable.ic_add_white_48dp :
                        onion.blog.R.drawable.ic_edit_white_48dp
        );

        fab.setVisibility(editId != null || Uri.parse(url).getPath().split("/").length < 3 ? View.VISIBLE : View.GONE);

    }

    void update() {
        webView.reload();
        updateUrl(webView.getUrl());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(onion.blog.R.menu.menu_main, menu);

        //if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA))
        menu.removeItem(onion.blog.R.id.action_camera);

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((TorStatusBlogView) findViewById(onion.blog.R.id.torStatusBlogView)).update();
        update();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == onion.blog.R.id.action_settings) {
            return true;
        }

        if (id == onion.blog.R.id.action_blog_title) {
            editTitle();
            return true;
        }

        if (id == onion.blog.R.id.action_photo) {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Complete action using"), REQUEST_PICKER);
            return true;
        }

        if (id == onion.blog.R.id.action_camera) {
            //getPackageManager().hasSystemFeature(CAME)
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            //takePictureIntent.resolveActivity(getPackageManager());
            startActivityForResult(takePictureIntent, REQUEST_CAMERA);
            return true;
        }

        if (id == onion.blog.R.id.action_add_post) {
            addPost();
            return true;
        }

        if (id == onion.blog.R.id.action_share) {
            share();
            return true;
        }

        if (id == onion.blog.R.id.rate) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName()));
                PackageManager pm = getPackageManager();
                for (ApplicationInfo packageInfo : pm.getInstalledApplications(0)) {
                    if (packageInfo.packageName.equals("com.android.vending"))
                        intent.setPackage("com.android.vending");
                }
                startActivity(intent);
            } catch (Throwable t) {
                Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        if (id == onion.blog.R.id.share) {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=" + getPackageName());
            intent.setType("text/plain");
            startActivity(intent);
        }

        if (id == onion.blog.R.id.about) {
            showAbout();
            return true;
        }

        if (id == onion.blog.R.id.action_style) {
            new AlertDialog.Builder(this)
                    .setTitle("Выбрать стиль")
                    .setSingleChoiceItems(blog.getStyles(), blog.getStyleIndex(), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            blog.setStyle(which);
                            update();
                            dialog.cancel();
                        }
                    })
                    .setNegativeButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .show().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            return true;
        }

        if (id == onion.blog.R.id.action_home) {
            goHome();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(getString(onion.blog.R.string.app_name))
                        //.setMessage(BuildConfig.APPLICATION_ID + "\n\nVersion: " + BuildConfig.VERSION_NAME)
                .setMessage("Version: " + onion.blog.BuildConfig.VERSION_NAME)
                .setNeutralButton("Libraries", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showLibraries();
                    }
                })
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
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
                .setTitle("Стороннее программное обеспечение, используемое в этом приложении (нажмите для просмотра лицензии) ")
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

    void share() {
        final String domain = TorBlog.readDomain(this);
        final View view = getLayoutInflater().inflate(onion.blog.R.layout.dialog_share, null);
        ((TextView) view.findViewById(onion.blog.R.id.darknet)).setText(domain);
        ((TextView) view.findViewById(onion.blog.R.id.clearnet)).setText(domain + ".to");

        ((TextView) view.findViewById(onion.blog.R.id.darkinfo)).setText(Html.fromHtml(
                "Анонимный и самый безопасный способ получить доступ к вашему блогу. Требуется веб-браузер с поддержкой TorBlog, такой как <a href='https://play.google.com/store/apps/details?id=onion.fire'>Fire.onion</a>."));
        ((TextView) view.findViewById(onion.blog.R.id.darkinfo)).setClickable(true);
        ((TextView) view.findViewById(onion.blog.R.id.darkinfo)).setMovementMethod(LinkMovementMethod.getInstance());

        view.findViewById(onion.blog.R.id.clearcopy).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("", "http://" + domain + ".to"));
                Toast.makeText(MainBlogActivity.this, "Ссылка Clearnet скопирована в буфер обмена", Toast.LENGTH_SHORT).show();
            }
        });
        view.findViewById(onion.blog.R.id.clearview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + domain + ".to")));
            }
        });
        view.findViewById(onion.blog.R.id.clearsend).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, "http://" + domain + ".to");
                intent.setType("text/plain");
                startActivity(intent);
            }
        });

        view.findViewById(onion.blog.R.id.darkcopy).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("", "http://" + domain));
                Toast.makeText(MainBlogActivity.this, "Darknet ссылка скопирована в буфер обмена", Toast.LENGTH_SHORT).show();
            }
        });
        view.findViewById(onion.blog.R.id.darkview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + domain)));
            }
        });
        view.findViewById(onion.blog.R.id.darksend).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, "http://" + domain);
                intent.setType("text/plain");
                startActivity(intent);
            }
        });

        new AlertDialog.Builder(this)
                .setView(view)
                .show();
    }

    void editTitle() {
        final View view = getLayoutInflater().inflate(onion.blog.R.layout.dialog_title, null);
        ((EditText) view.findViewById(onion.blog.R.id.title)).setText(blog.getTitle());
        new AlertDialog.Builder(this)
                .setTitle("Изменить заголовок блога")
                .setView(view)
                .setPositiveButton("Публиковать", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        blog.setTitle(((EditText) view.findViewById(onion.blog.R.id.title)).getText().toString());
                        update();
                        //hidekey();
                        Snackbar.make(webView, "Заголовок изменено.", Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Отмена", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //hidekey();
                    }
                })
                .show();
        // view.findViewById(R.id.title).requestFocus();
        //((EditText) view.findViewById(R.id.title)).selectAll();
        //showkey();
    }

    void addPost() {
        startActivity(new Intent(this, PostActivity.class));
    }

    void editPost(String id) {
        startActivity(new Intent(this, PostActivity.class).putExtra("id", id));
    }

    @Override
    public void onBackPressed() {
        //super.onBackPressed();
        webView.goBack();
    }
}
