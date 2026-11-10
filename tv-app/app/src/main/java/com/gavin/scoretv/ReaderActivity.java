package com.gavin.scoretv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderActivity extends Activity {
    private static final int INK = Color.rgb(244, 239, 228);
    private static final int MUTED = Color.rgb(157, 148, 133);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private LruCache<String, Bitmap> cache;
    private String songTitle = "";
    private List<String> pages = new ArrayList<>();
    private ImageView leftImage;
    private ImageView rightImage;
    private TextView pageLabel;
    private TextView loadingLabel;
    private TextView hintLabel;
    private int pageIndex;
    private int generation;

    private final Runnable hideHint = () -> {
        if (hintLabel == null) return;
        hintLabel.animate().alpha(0f).setDuration(500).start();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        ArrayList<String> received = getIntent().getStringArrayListExtra("pages");
        if (received != null) pages = received;
        songTitle = getIntent().getStringExtra("song_title");
        if (songTitle == null) songTitle = "";

        int cacheSize = (int) (Runtime.getRuntime().maxMemory() / 8);
        cache = new LruCache<String, Bitmap>(Math.max(cacheSize, 24 * 1024 * 1024)) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };

        setContentView(buildReader());
        hideSystemUi();
        if (pages.isEmpty()) {
            loadingLabel.setText("这首歌还没有琴谱");
            return;
        }
        showPair(0);
        mainHandler.postDelayed(hideHint, 3500);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                nextScreen();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                previousScreen();
                return true;
            case KeyEvent.KEYCODE_MENU:
                hintLabel.setAlpha(1f);
                mainHandler.removeCallbacks(hideHint);
                mainHandler.postDelayed(hideHint, 3000);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    private View buildReader() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(11, 10, 9));

        LinearLayout spread = new LinearLayout(this);
        spread.setOrientation(LinearLayout.HORIZONTAL);
        spread.setGravity(Gravity.CENTER);
        spread.setPadding(dp(24), dp(36), dp(24), dp(20));

        leftImage = pageView();
        rightImage = pageView();
        spread.addView(wrapPage(leftImage), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        View gutter = new View(this);
        gutter.setBackgroundColor(Color.rgb(38, 34, 29));
        LinearLayout.LayoutParams gutterParams = new LinearLayout.LayoutParams(dp(1),
                ViewGroup.LayoutParams.MATCH_PARENT);
        gutterParams.setMargins(dp(10), dp(4), dp(10), dp(4));
        spread.addView(gutter, gutterParams);

        spread.addView(wrapPage(rightImage), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        root.addView(spread, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pageLabel = new TextView(this);
        pageLabel.setTextColor(INK);
        pageLabel.setTextSize(13);
        pageLabel.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        pageLabel.setLetterSpacing(0.08f);
        pageLabel.setGravity(Gravity.CENTER);
        pageLabel.setSingleLine(true);
        pageLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pageLabel.setMaxWidth(dp(620));
        pageLabel.setPadding(dp(17), dp(8), dp(17), dp(8));
        GradientDrawable labelBackground = new GradientDrawable();
        labelBackground.setColor(Color.argb(218, 25, 22, 18));
        labelBackground.setCornerRadius(dp(20));
        pageLabel.setBackground(labelBackground);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        labelParams.topMargin = dp(10);
        root.addView(pageLabel, labelParams);

        loadingLabel = new TextView(this);
        loadingLabel.setText("正在取谱…");
        loadingLabel.setTextColor(MUTED);
        loadingLabel.setTextSize(17);
        loadingLabel.setGravity(Gravity.CENTER);
        root.addView(loadingLabel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        hintLabel = new TextView(this);
        hintLabel.setText("↓  下一屏        ↑  上一屏        BACK  返回曲库");
        hintLabel.setTextColor(MUTED);
        hintLabel.setTextSize(12);
        hintLabel.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        hintLabel.setGravity(Gravity.CENTER);
        hintLabel.setPadding(dp(18), dp(8), dp(18), dp(8));
        GradientDrawable hintBackground = new GradientDrawable();
        hintBackground.setColor(Color.argb(220, 20, 18, 15));
        hintBackground.setCornerRadius(dp(18));
        hintLabel.setBackground(hintBackground);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintParams.bottomMargin = dp(11);
        root.addView(hintLabel, hintParams);
        return root;
    }

    private ImageView pageView() {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(Color.rgb(247, 244, 235));
        return image;
    }

    private View wrapPage(ImageView image) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(30, 27, 23));
        frame.setPadding(dp(3), dp(3), dp(3), dp(3));
        frame.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return frame;
    }

    private void nextScreen() {
        if (pages.isEmpty()) return;
        if (pageIndex + 2 >= pages.size()) {
            Toast.makeText(this, "已到最后一屏", Toast.LENGTH_SHORT).show();
            return;
        }
        showPair(pageIndex + 2);
    }

    private void previousScreen() {
        if (pageIndex == 0) {
            Toast.makeText(this, "已经是第一屏", Toast.LENGTH_SHORT).show();
            return;
        }
        showPair(Math.max(0, pageIndex - 2));
    }

    private void showPair(int start) {
        pageIndex = start;
        int end = Math.min(start + 2, pages.size());
        String counter = String.format("%02d–%02d  /  %02d", start + 1, end, pages.size());
        pageLabel.setText(songTitle.isEmpty() ? counter : songTitle + "   ·   " + counter);
        loadingLabel.setVisibility(pageIndex == 0 ? View.VISIBLE : View.GONE);
        loadingLabel.setText("正在取谱…");
        loadInto(leftImage, pages.get(start), ++generation);
        if (start + 1 < pages.size()) {
            loadInto(rightImage, pages.get(start + 1), generation);
        } else {
            rightImage.animate().cancel();
            rightImage.setImageDrawable(null);
        }
        hintLabel.setAlpha(1f);
        mainHandler.removeCallbacks(hideHint);
        mainHandler.postDelayed(hideHint, 3000);
    }

    private void loadInto(ImageView target, String address, int requestGeneration) {
        Bitmap cached = cache.get(address);
        if (cached != null) {
            target.animate().cancel();
            target.setImageBitmap(cached);
            target.setAlpha(1f);
            loadingLabel.setVisibility(View.GONE);
            return;
        }
        target.animate().alpha(0.3f).setDuration(100).start();
        io.execute(() -> {
            Bitmap bitmap;
            try {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                bitmap = ApiClient.getBitmap(address, Math.max(screenWidth / 2, 1080));
                cache.put(address, bitmap);
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (requestGeneration != generation) return;
                    loadingLabel.setVisibility(View.VISIBLE);
                    loadingLabel.setText("琴谱加载失败\n请检查曲库服务与网络");
                });
                return;
            }
            mainHandler.post(() -> {
                if (requestGeneration != generation) return;
                target.animate().cancel();
                target.setImageBitmap(bitmap);
                target.animate().alpha(1f).setDuration(160).start();
                loadingLabel.setVisibility(View.GONE);
            });
        });
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
