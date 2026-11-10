package com.gavin.scoretv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "scoretv";
    private static final String KEY_SERVER = "server_url";
    private static final String DEFAULT_SERVER = "https://dify.eborry.com/guitar";
    private static final int INK = Color.rgb(244, 239, 228);
    private static final int MUTED = Color.rgb(157, 148, 133);
    private static final int ACCENT = Color.rgb(224, 100, 56);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Song> songs = new ArrayList<>();
    private SharedPreferences preferences;
    private ListView songList;
    private SongAdapter adapter;
    private TextView loadStatus;
    private TextView serverLabel;
    private TextView connectionLabel;
    private TextView songCount;
    private String lastSongId;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildScreen());
        hideSystemUi();
        if (serverUrl().isEmpty()) {
            loadStatus.setText("尚未配置服务器\n按 MENU 设置曲库地址");
            loadStatus.setVisibility(View.VISIBLE);
            songList.post(this::showServerDialog);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (!serverUrl().isEmpty()) fetchSongs();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
            showServerDialog();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(17, 16, 14));

        LinearLayout masthead = new LinearLayout(this);
        masthead.setOrientation(LinearLayout.HORIZONTAL);
        masthead.setGravity(Gravity.CENTER_VERTICAL);
        masthead.setPadding(dp(64), dp(42), dp(64), dp(30));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("GUITAR SCORE ARCHIVE  /  01", 12, ACCENT);
        eyebrow.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        eyebrow.setLetterSpacing(0.16f);
        TextView title = text("弦谱", 54, INK);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        title.setPadding(0, dp(9), 0, 0);
        TextView subtitle = text("在电视上，安静地看谱。", 16, MUTED);
        subtitle.setPadding(0, dp(6), 0, 0);
        identity.addView(eyebrow);
        identity.addView(title);
        identity.addView(subtitle);
        masthead.addView(identity, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout network = new LinearLayout(this);
        network.setOrientation(LinearLayout.VERTICAL);
        network.setGravity(Gravity.END);
        connectionLabel = text("○  等待连接", 13, MUTED);
        connectionLabel.setGravity(Gravity.END);
        serverLabel = text("", 12, MUTED);
        serverLabel.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        serverLabel.setGravity(Gravity.END);
        serverLabel.setPadding(0, dp(8), 0, 0);
        network.addView(connectionLabel);
        network.addView(serverLabel);
        masthead.addView(network);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(62, 57, 49));
        root.addView(masthead, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        FrameLayout content = new FrameLayout(this);
        songList = new ListView(this);
        songList.setDivider(null);
        songList.setDividerHeight(0);
        songList.setSelector(R.drawable.focus_card);
        songList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        songList.setVerticalScrollBarEnabled(false);
        songList.setPadding(dp(64), dp(18), dp(64), dp(18));
        songList.setClipToPadding(false);
        songList.setCacheColorHint(Color.TRANSPARENT);
        adapter = new SongAdapter();
        songList.setAdapter(adapter);
        songList.setOnItemClickListener((parent, view, position, id) -> openSong(songs.get(position)));
        content.addView(songList, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        loadStatus = text("正在读取曲库…", 19, MUTED);
        loadStatus.setGravity(Gravity.CENTER);
        loadStatus.setPadding(dp(70), dp(50), dp(70), dp(50));
        content.addView(loadStatus, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(64), dp(18), dp(64), dp(26));
        TextView keys = text("↓ ↑  选择       OK  打开琴谱       MENU  服务器设置", 13, MUTED);
        keys.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        footer.addView(keys, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        songCount = text("", 12, MUTED);
        footer.addView(songCount);
        root.addView(footer);
        return root;
    }

    private void fetchSongs() {
        if (loading) return;
        loading = true;
        String base = serverUrl();
        serverLabel.setText(base);
        connectionLabel.setText("○  正在连接");
        connectionLabel.setTextColor(MUTED);
        if (songs.isEmpty()) {
            loadStatus.setVisibility(View.VISIBLE);
            loadStatus.setText("正在连接曲库…");
        } else {
            loadStatus.setVisibility(View.GONE);
        }
        io.execute(() -> {
            try {
                String body = ApiClient.getText(base + "/api/songs");
                JSONArray array = new JSONArray(body);
                List<Song> result = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.getJSONObject(i);
                    result.add(Song.fromJson(object, base));
                }
                mainHandler.post(() -> {
                    loading = false;
                    connectionLabel.setText("●  曲库已连接");
                    connectionLabel.setTextColor(Color.rgb(112, 157, 112));
                    songs.clear();
                    songs.addAll(result);
                    adapter.notifyDataSetChanged();
                    if (result.isEmpty()) {
                        loadStatus.setText("曲库还是空的\n请先在后台添加歌曲与琴谱");
                        loadStatus.setVisibility(View.VISIBLE);
                    } else {
                        loadStatus.setVisibility(View.GONE);
                        int position = 0;
                        if (lastSongId != null) {
                            for (int i = 0; i < result.size(); i++) {
                                if (lastSongId.equals(result.get(i).id)) {
                                    position = i;
                                    break;
                                }
                            }
                        }
                        songList.setSelection(position);
                        songList.requestFocus();
                    }
                    updateCount();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    loading = false;
                    connectionLabel.setText("●  曲库未连接");
                    connectionLabel.setTextColor(Color.rgb(210, 106, 70));
                    if (songs.isEmpty()) {
                        loadStatus.setText("无法读取曲库\n\n" + base + "\n请确认服务已启动、地址可访问、网络通畅\n按 MENU 修改服务器地址");
                        loadStatus.setVisibility(View.VISIBLE);
                    } else {
                        Toast.makeText(MainActivity.this, "曲库连接失败，当前显示上次读取的列表", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void updateCount() {
        songCount.setText(songs.isEmpty() ? "曲库为空" : songs.size() + " 首歌曲");
    }

    private void openSong(Song song) {
        if (song.pages.isEmpty()) {
            Toast.makeText(this, "这首歌还没有琴谱图片", Toast.LENGTH_SHORT).show();
            return;
        }
        lastSongId = song.id;
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra("song_id", song.id);
        intent.putExtra("song_title", song.title);
        intent.putStringArrayListExtra("pages", new ArrayList<>(song.pages));
        startActivity(intent);
    }

    private void showServerDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(26), dp(8), dp(26), 0);
        TextView help = text("填写曲库服务地址，例如：\nhttps://dify.eborry.com/guitar\nhttp://192.168.1.100:8787\n\n电视不能填写 localhost。", 14, MUTED);
        help.setLineSpacing(0, 1.25f);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setText(serverUrl());
        input.setHint("https://dify.eborry.com/guitar");
        input.setSelectAllOnFocus(true);
        box.addView(help);
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("连接曲库服务")
                .setView(box)
                .setPositiveButton("保存并连接", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String normalized = input.getText().toString().trim();
                    if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                        normalized = "http://" + normalized;
                    }
                    while (normalized.endsWith("/")) {
                        normalized = normalized.substring(0, normalized.length() - 1);
                    }
                    if (!normalized.matches("https?://[^\\s/]+.*")) {
                        input.setError("请输入有效的服务器地址");
                        return;
                    }
                    preferences.edit().putString(KEY_SERVER, normalized).apply();
                    dialog.dismiss();
                    fetchSongs();
                }));
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        input.requestFocus();
    }

    private String serverUrl() {
        return preferences.getString(KEY_SERVER, DEFAULT_SERVER);
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

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class SongAdapter extends BaseAdapter {
        @Override
        public int getCount() { return songs.size(); }

        @Override
        public Object getItem(int position) { return songs.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout wrapper;
            if (convertView == null) {
                wrapper = new LinearLayout(MainActivity.this);
                wrapper.setOrientation(LinearLayout.VERTICAL);
                wrapper.setPadding(0, dp(4), 0, dp(4));

                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(30), dp(22), dp(30), dp(22));

                TextView number = text("", 18, ACCENT);
                number.setTypeface(Typeface.create("monospace", Typeface.BOLD));
                number.setGravity(Gravity.TOP);
                number.setPadding(0, dp(7), dp(24), 0);
                number.setTag("number");
                row.addView(number, new LinearLayout.LayoutParams(dp(70),
                        ViewGroup.LayoutParams.MATCH_PARENT));

                LinearLayout detail = new LinearLayout(MainActivity.this);
                detail.setOrientation(LinearLayout.VERTICAL);
                TextView title = text("", 32, INK);
                title.setTypeface(Typeface.create("serif", Typeface.BOLD));
                title.setTag("title");
                TextView meta = text("", 14, MUTED);
                meta.setPadding(0, dp(8), 0, 0);
                meta.setTag("meta");
                detail.addView(title);
                detail.addView(meta);
                row.addView(detail, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView arrow = text("打开  ›", 14, MUTED);
                arrow.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
                arrow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
                arrow.setTag("arrow");
                row.addView(arrow, new LinearLayout.LayoutParams(dp(120),
                        ViewGroup.LayoutParams.MATCH_PARENT));

                wrapper.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(132)));
            } else {
                wrapper = (LinearLayout) convertView;
            }

            Song song = songs.get(position);
            LinearLayout row = (LinearLayout) wrapper.getChildAt(0);
            ((TextView) row.findViewWithTag("number")).setText(String.format("%02d", position + 1));
            ((TextView) row.findViewWithTag("title")).setText(song.title);
            String artist = song.artist.isEmpty() ? "未填写作者" : song.artist;
            ((TextView) row.findViewWithTag("meta")).setText(
                    artist + "   ·   " + song.pages.size() + " 页 / " + song.screenCount() + " 屏");
            return wrapper;
        }
    }
}
