package com.yass.neonmusic;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 17;

    private final List<Song> allSongs = new ArrayList<>();
    private final List<Song> visibleSongs = new ArrayList<>();
    private final Handler handler = new Handler();
    private final Random random = new Random();

    private LinearLayout listContainer;
    private TextView titleView;
    private TextView artistView;
    private TextView durationView;
    private TextView currentTimeView;
    private TextView playButton;
    private TextView shuffleButton;
    private TextView repeatButton;
    private SeekBar seekBar;
    private VisualizerView visualizerView;
    private ProgressBar loadingView;

    private MediaPlayer player;
    private int currentIndex = -1;
    private boolean shuffleEnabled = false;
    private boolean repeatEnabled = false;
    private boolean userSeeking = false;

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 650);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#070814"));
        window.setNavigationBarColor(Color.parseColor("#070814"));
        if (Build.VERSION.SDK_INT >= 28) {
            window.getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }

        buildUi();
        handler.post(progressTicker);

        if (hasAudioPermission()) {
            loadSongs();
        } else {
            requestAudioPermission();
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#070814"), Color.parseColor("#161A3D"), Color.parseColor("#0A5C72")}));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(154));
        scrollView.addView(content, new ScrollView.LayoutParams(-1, -2));

        content.addView(header());
        content.addView(nowPlayingCard());
        content.addView(chips());
        content.addView(searchBox());

        TextView listTitle = label("آهنگ‌های دستگاه", 18, true, "#FFFFFF");
        listTitle.setPadding(0, dp(18), 0, dp(10));
        content.addView(listTitle);

        loadingView = new ProgressBar(this);
        loadingView.setIndeterminate(true);
        content.addView(loadingView, centeredParams(dp(52), dp(52)));

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer, new LinearLayout.LayoutParams(-1, -2));

        root.addView(scrollView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(bottomPlayer(), bottomParams());
        setContentView(root);
    }

    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(18));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        TextView appName = label("Neon Player", 30, true, "#FFFFFF");
        TextView sub = label("موزیک‌پلیر ", 13, false, "#B8C7E8");
        texts.addView(appName);
        texts.addView(sub);

        TextView badge = pill("♫", "#70F0FF", "#1B3350");
        badge.setTextSize(28);
        row.addView(badge, new LinearLayout.LayoutParams(dp(64), dp(64)));
        return row;
    }

    private View nowPlayingCard() {
        LinearLayout card = panel();
        card.setPadding(dp(18), dp(18), dp(18), dp(18));

        TextView tag = pill("در حال پخش", "#70F0FF", "#213A55");
        card.addView(tag, new LinearLayout.LayoutParams(dp(112), dp(34)));

        visualizerView = new VisualizerView(this);
        LinearLayout.LayoutParams visualParams = new LinearLayout.LayoutParams(-1, dp(118));
        visualParams.setMargins(0, dp(14), 0, dp(12));
        card.addView(visualizerView, visualParams);

        titleView = label("یک آهنگ انتخاب کن", 24, true, "#FFFFFF");
        artistView = label("موزیک‌های گوشی بعد از اجازه دسترسی نمایش داده می‌شوند", 13, false, "#B8C7E8");
        card.addView(titleView);
        card.addView(artistView);
        return card;
    }

    private View chips() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(14), 0, 0);
        scroll.addView(row);

        row.addView(infoChip("اسکن خودکار فایل‌های صوتی"));
        row.addView(infoChip("جست‌وجوی سریع"));
        row.addView(infoChip("Shuffle / Repeat"));
        row.addView(infoChip("طراحی Dark Neon"));
        return scroll;
    }

    private TextView infoChip(String text) {
        TextView chip = pill(text, "#E8F7FF", "#1C2748");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(38));
        params.setMargins(dp(8), 0, 0, 0);
        chip.setLayoutParams(params);
        return chip;
    }

    private View searchBox() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("جست‌وجو بر اساس نام آهنگ یا خواننده");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#8FA2CA"));
        input.setTextSize(14);
        input.setPadding(dp(16), 0, dp(16), 0);
        input.setBackground(round("#18213F", 18, "#2C3E69"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
        params.setMargins(0, dp(18), 0, 0);
        input.setLayoutParams(params);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSongs(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        return input;
    }

    private View bottomPlayer() {
        LinearLayout playerBar = panel();
        playerBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        playerBar.setBackground(round("#EC121A2F", 22, "#334B77"));

        LinearLayout times = new LinearLayout(this);
        times.setGravity(Gravity.CENTER_VERTICAL);
        currentTimeView = label("00:00", 12, false, "#B8C7E8");
        durationView = label("00:00", 12, false, "#B8C7E8");
        times.addView(currentTimeView);
        times.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        times.addView(durationView);
        playerBar.addView(times, new LinearLayout.LayoutParams(-1, dp(20)));

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
            @Override public void onStartTrackingTouch(SeekBar seekBar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                if (player != null && player.getDuration() > 0) {
                    player.seekTo(player.getDuration() * bar.getProgress() / 1000);
                }
            }
        });
        playerBar.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(34)));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        shuffleButton = control("⤨");
        TextView previousButton = control("‹‹");
        playButton = control("▶");
        playButton.setTextSize(24);
        TextView nextButton = control("››");
        repeatButton = control("↻");

        shuffleButton.setOnClickListener(v -> {
            shuffleEnabled = !shuffleEnabled;
            updateToggleColors();
        });
        repeatButton.setOnClickListener(v -> {
            repeatEnabled = !repeatEnabled;
            updateToggleColors();
        });
        previousButton.setOnClickListener(v -> previous());
        playButton.setOnClickListener(v -> togglePlay());
        nextButton.setOnClickListener(v -> next());

        controls.addView(shuffleButton);
        controls.addView(previousButton);
        controls.addView(playButton, new LinearLayout.LayoutParams(dp(58), dp(50)));
        controls.addView(nextButton);
        controls.addView(repeatButton);
        playerBar.addView(controls, new LinearLayout.LayoutParams(-1, dp(56)));
        return playerBar;
    }

    private void loadSongs() {
        loadingView.setVisibility(View.VISIBLE);
        allSongs.clear();
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
        Uri collection = Build.VERSION.SDK_INT >= 29
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        try (Cursor cursor = getContentResolver().query(
                collection,
                projection,
                selection,
                null,
                MediaStore.Audio.Media.TITLE + " ASC")) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String title = clean(cursor.getString(titleColumn), "Unknown track");
                    String artist = clean(cursor.getString(artistColumn), "Unknown artist");
                    long duration = cursor.getLong(durationColumn);
                    Uri uri = ContentUris.withAppendedId(collection, id);
                    allSongs.add(new Song(title, artist, duration, uri));
                }
            }
        }

        visibleSongs.clear();
        visibleSongs.addAll(allSongs);
        loadingView.setVisibility(View.GONE);
        renderList();
        if (allSongs.isEmpty()) {
            showEmptyState();
        }
    }

    private void renderList() {
        listContainer.removeAllViews();
        for (int i = 0; i < visibleSongs.size(); i++) {
            Song song = visibleSongs.get(i);
            int index = allSongs.indexOf(song);
            listContainer.addView(songRow(song, index, i));
        }
    }

    private View songRow(Song song, int allIndex, int displayIndex) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(round(displayIndex % 2 == 0 ? "#151E3B" : "#101832", 16, "#26395E"));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(78));
        rowParams.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowParams);

        TextView artwork = pill("♪", "#70F0FF", "#23365A");
        artwork.setTextSize(24);
        row.addView(artwork, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setPadding(dp(12), 0, dp(12), 0);
        TextView name = label(song.title, 16, true, "#FFFFFF");
        TextView artist = label(song.artist + "  •  " + format(song.duration), 12, false, "#AAB9D8");
        meta.addView(name);
        meta.addView(artist);
        row.addView(meta, new LinearLayout.LayoutParams(0, -2, 1));

        TextView action = pill(allIndex == currentIndex && isPlaying() ? "⏸" : "▶", "#FFFFFF", "#2B78FF");
        row.addView(action, new LinearLayout.LayoutParams(dp(44), dp(44)));
        row.setOnClickListener(v -> playAt(allIndex));
        return row;
    }

    private void filterSongs(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        visibleSongs.clear();
        if (q.isEmpty()) {
            visibleSongs.addAll(allSongs);
        } else {
            for (Song song : allSongs) {
                if (song.title.toLowerCase(Locale.ROOT).contains(q)
                        || song.artist.toLowerCase(Locale.ROOT).contains(q)) {
                    visibleSongs.add(song);
                }
            }
        }
        renderList();
    }

    private void playAt(int index) {
        if (index < 0 || index >= allSongs.size()) return;
        currentIndex = index;
        Song song = allSongs.get(index);
        releasePlayer();
        player = new MediaPlayer();
        try {
            player.setDataSource(this, song.uri);
            player.setOnPreparedListener(mp -> {
                mp.start();
                updateNowPlaying(song);
            });
            player.setOnCompletionListener(mp -> {
                if (repeatEnabled) {
                    playAt(currentIndex);
                } else {
                    next();
                }
            });
            player.prepareAsync();
            titleView.setText(song.title);
            artistView.setText("در حال آماده‌سازی...");
            visualizerView.setActive(true);
        } catch (IOException | RuntimeException e) {
            Toast.makeText(this, "این فایل قابل پخش نیست", Toast.LENGTH_SHORT).show();
            releasePlayer();
        }
        renderList();
    }

    private void updateNowPlaying(Song song) {
        titleView.setText(song.title);
        artistView.setText(song.artist);
        durationView.setText(format(song.duration));
        playButton.setText("⏸");
        visualizerView.setActive(true);
        renderList();
    }

    private void togglePlay() {
        if (player == null) {
            if (!allSongs.isEmpty()) playAt(0);
            return;
        }
        if (player.isPlaying()) {
            player.pause();
            playButton.setText("▶");
            visualizerView.setActive(false);
        } else {
            player.start();
            playButton.setText("⏸");
            visualizerView.setActive(true);
        }
        renderList();
    }

    private void next() {
        if (allSongs.isEmpty()) return;
        int nextIndex = shuffleEnabled ? random.nextInt(allSongs.size()) : currentIndex + 1;
        if (nextIndex >= allSongs.size()) nextIndex = 0;
        playAt(nextIndex);
    }

    private void previous() {
        if (allSongs.isEmpty()) return;
        int previousIndex = currentIndex <= 0 ? allSongs.size() - 1 : currentIndex - 1;
        playAt(previousIndex);
    }

    private void updateProgress() {
        if (player == null || userSeeking) return;
        try {
            int duration = player.getDuration();
            int position = player.getCurrentPosition();
            if (duration > 0) {
                seekBar.setProgress(position * 1000 / duration);
                currentTimeView.setText(format(position));
                durationView.setText(format(duration));
            }
            visualizerView.setActive(player.isPlaying());
        } catch (IllegalStateException ignored) {
        }
    }

    private void updateToggleColors() {
        shuffleButton.setTextColor(Color.parseColor(shuffleEnabled ? "#70F0FF" : "#FFFFFF"));
        repeatButton.setTextColor(Color.parseColor(repeatEnabled ? "#70F0FF" : "#FFFFFF"));
    }

    private boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    private void showEmptyState() {
        TextView empty = label("هیچ فایل صوتی پیدا نشد. چند آهنگ روی گوشی بریز و دوباره برنامه را باز کن.", 15, false, "#B8C7E8");
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(18), dp(22), dp(18), dp(22));
        empty.setBackground(round("#151E3B", 16, "#26395E"));
        listContainer.addView(empty, new LinearLayout.LayoutParams(-1, -2));
    }

    private boolean hasAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        requestPermissions(new String[]{permission}, PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs();
        } else {
            loadingView.setVisibility(View.GONE);
            Toast.makeText(this, "برای نمایش آهنگ‌ها باید اجازه دسترسی بدهی", Toast.LENGTH_LONG).show();
            showEmptyState();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.release();
            } catch (RuntimeException ignored) {
            }
            player = null;
        }
        playButton.setText("▶");
        visualizerView.setActive(false);
    }

    private LinearLayout panel() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackground(round("#D9121A2F", 24, "#334B77"));
        return view;
    }

    private TextView control(String text) {
        TextView view = pill(text, "#FFFFFF", "#1C2748");
        view.setTextSize(19);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
        params.setMargins(dp(5), 0, dp(5), 0);
        view.setLayoutParams(params);
        return view;
    }

    private TextView label(String text, int sp, boolean bold, String color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(Color.parseColor(color));
        view.setGravity(Gravity.RIGHT);
        view.setMaxLines(2);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView pill(String text, String textColor, String fillColor) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.parseColor(textColor));
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setBackground(round(fillColor, 18, "#00000000"));
        return view;
    }

    private GradientDrawable round(String color, int radius, String strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(color));
        drawable.setCornerRadius(dp(radius));
        if (!"#00000000".equals(strokeColor)) {
            drawable.setStroke(dp(1), Color.parseColor(strokeColor));
        }
        return drawable;
    }

    private FrameLayout.LayoutParams bottomParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(142));
        params.gravity = Gravity.BOTTOM;
        params.setMargins(dp(14), 0, dp(14), dp(12));
        return params;
    }

    private LinearLayout.LayoutParams centeredParams(int width, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty() || "<unknown>".equalsIgnoreCase(value)) return fallback;
        return value.trim();
    }

    private String format(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private static class Song {
        final String title;
        final String artist;
        final long duration;
        final Uri uri;

        Song(String title, String artist, long duration, Uri uri) {
            this.title = title;
            this.artist = artist;
            this.duration = duration;
            this.uri = uri;
        }
    }

    public static class VisualizerView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();
        private boolean active;
        private final Runnable animator = new Runnable() {
            @Override
            public void run() {
                invalidate();
                if (active) postDelayed(this, 120);
            }
        };

        public VisualizerView(android.content.Context context) {
            super(context);
        }

        void setActive(boolean active) {
            if (this.active == active) return;
            this.active = active;
            removeCallbacks(animator);
            invalidate();
            if (active) post(animator);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            paint.setShader(new LinearGradient(0, 0, width, 0,
                    new int[]{Color.parseColor("#70F0FF"), Color.parseColor("#FF4FD8"), Color.parseColor("#B8FF6A")},
                    null,
                    Shader.TileMode.CLAMP));
            int bars = 28;
            float gap = width / (bars * 2.2f);
            float barWidth = gap * 1.15f;
            for (int i = 0; i < bars; i++) {
                float level = active ? 0.18f + random.nextFloat() * 0.78f : 0.2f + (i % 6) * 0.06f;
                float left = i * gap * 2f + gap;
                float top = height - (height * level);
                canvas.drawRoundRect(left, top, left + barWidth, height, barWidth, barWidth, paint);
            }
            paint.setShader(null);
            paint.setColor(Color.parseColor("#18FFFFFF"));
            canvas.drawCircle(width * 0.2f, height * 0.24f, height * 0.34f, paint);
            canvas.drawCircle(width * 0.82f, height * 0.42f, height * 0.25f, paint);
        }
    }
}
