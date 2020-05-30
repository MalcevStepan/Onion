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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import onion.blog.TorBlog;

public class TorStatusBlogView extends LinearLayout {

    public TorStatusBlogView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void update() {
        TorBlog tor = TorBlog.getInstance(getContext());

        setVisibility(!tor.isReady() ? View.VISIBLE : View.GONE);

        String status = tor.getStatus();
        int i = status.indexOf(']');
        if (i >= 0) status = status.substring(i + 1);
        status = status.trim();

        TextView view = (TextView) findViewById(onion.blog.R.id.status);

        String prefix = "Bootstrapped";
        if (status.contains("%") && status.length() > prefix.length() && status.startsWith(prefix)) {
            status = status.substring(prefix.length());
            status = status.trim();
            view.setText(status);
        } else if (view.length() == 0) {
            view.setText("Запуск...");
        }
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            TorBlog tor = TorBlog.getInstance(getContext());
            tor.setLogListener(new TorBlog.LogListener() {
                @Override
                public void onLog() {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            update();
                        }
                    });
                }
            });
            update();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        TorBlog tor = TorBlog.getInstance(getContext());
        tor.setLogListener(null);
        if (!isInEditMode()) {
            super.onDetachedFromWindow();
        }
    }
}
