package com.cj.pulse;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import org.json.JSONObject;

/** Owns playback independently of the Activity. All lifecycle changes run on the main thread. */
public final class MetroService extends Service {
    public static final String ACTION_START = "com.cj.pulse.START";
    public static final String ACTION_RESTART = "com.cj.pulse.RESTART";
    public static final String ACTION_STOP = "com.cj.pulse.STOP";
    public static final String ACTION_UPDATE = "com.cj.pulse.UPDATE";
    public static final String ACTION_TOGGLE = "com.cj.pulse.TOGGLE";
    public static final String ACTION_REMOTE = "com.cj.pulse.REMOTE";
    private static final String CHANNEL = "pulse_playback";
    private static final int NOTIFICATION_ID = 31;
    public static volatile MetroService instance;
    public static volatile State state = new State();

    /** A fresh object is published on every update; readers must not modify it. */
    public static class State {
        public boolean playing, muted, countingIn;
        public int beat, bpm = 120;
        public long bars, elapsedMs;
        public String error = "";
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private AudioEngine engine;
    private Config config = new Config();
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private PowerManager.WakeLock wakeLock;
    private MediaSession session;
    private NotificationManager notifications;
    private long generation, lastNotification;
    private int metadataBpm = -1;
    private boolean foreground, receiverRegistered, destroyed;

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) finishPlayback("");
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        notifications = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "메트로놈 재생", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("화면을 꺼도 계속 재생하고 알림에서 정지합니다.");
        channel.setSound(null, null);
        channel.enableVibration(false);
        notifications.createNotificationChannel(channel);
        audioManager = getSystemService(AudioManager.class);
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                            || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) finishPlayback("");
                }, main).build();
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Pulse:Playback");
        wakeLock.setReferenceCounted(false);
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisyReceiver, filter);
        receiverRegistered = true;
        session = new MediaSession(this, "Pulse");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(openAppIntent());
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { startPlayback(config); }
            @Override public void onPause() { finishPlayback(""); }
            @Override public void onStop() { finishPlayback(""); }
            @Override public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event == null) return super.onMediaButtonEvent(intent);
                int key = event.getKeyCode();
                if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0)
                    return key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || key == KeyEvent.KEYCODE_HEADSETHOOK
                            || key == KeyEvent.KEYCODE_MEDIA_PLAY || key == KeyEvent.KEYCODE_MEDIA_PAUSE
                            || key == KeyEvent.KEYCODE_MEDIA_STOP;
                switch (key) {
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    case KeyEvent.KEYCODE_HEADSETHOOK:
                        if (engine == null) startPlayback(config); else finishPlayback(""); return true;
                    case KeyEvent.KEYCODE_MEDIA_PLAY: startPlayback(config); return true;
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    case KeyEvent.KEYCODE_MEDIA_STOP: finishPlayback(""); return true;
                    default: return super.onMediaButtonEvent(intent);
                }
            }
        }, main);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) { finishPlayback(""); return START_NOT_STICKY; }
        Config incoming = readConfig(intent);
        if (ACTION_UPDATE.equals(action)) {
            config = incoming;
            if (engine != null) { engine.update(config, intent.getBooleanExtra("explicitTempo", true)); updateSession(true); }
            else stopSelfResult(startId);
        } else if (ACTION_START.equals(action) || ACTION_RESTART.equals(action)) {
            if (ACTION_RESTART.equals(action)) restartPlayback(incoming);
            else startPlayback(incoming);
        } else if (ACTION_TOGGLE.equals(action)) {
            if (engine == null) startPlayback(incoming); else finishPlayback("");
        } else if (engine == null) {
            stopSelfResult(startId);
        }
        return START_NOT_STICKY;
    }

    private Config readConfig(Intent intent) {
        String encoded = intent == null ? null : intent.getStringExtra("config");
        if (encoded == null) {
            if (engine != null) return config.copy();
            encoded = getSharedPreferences("pulse", MODE_PRIVATE).getString("config", "{}");
        }
        try { return Config.fromJson(new JSONObject(encoded)); }
        catch (Exception ignored) { return config.copy(); }
    }

    private void restartPlayback(Config next) {
        if (engine != null) {
            // Invalidate the old callback before stopping; keep this service alive.
            ++generation;
            AudioEngine old = engine;
            engine = null;
            old.stop();
            releaseResources();
        }
        startPlayback(next);
    }

    private void startPlayback(Config next) {
        if (destroyed) return;
        config = next.copy();
        config.sanitize();
        if (engine != null) { engine.update(config); return; }
        final long token = ++generation;
        State beginning = new State();
        beginning.playing = true;
        beginning.bpm = config.bpm;
        beginning.countingIn = config.countIn > 0;
        state = beginning;
        try {
            // Android 15+ requires foreground status before requesting audio focus.
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NOTIFICATION_ID, notification());
            foreground = true;
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                finishPlayback("오디오를 사용할 수 없습니다. 다른 앱 재생을 멈춘 뒤 다시 시도하세요.");
                return;
            }
            wakeLock.acquire();
            session.setActive(true);
            updateSession(true);
            engine = new AudioEngine(config, new AudioEngine.Listener() {
                @Override public void onState(AudioEngine.Snapshot snapshot) {
                    main.post(() -> {
                        if (destroyed || token != generation || engine == null) return;
                        State nextState = new State();
                        nextState.playing = true;
                        nextState.muted = snapshot.muted;
                        nextState.countingIn = snapshot.countingIn;
                        nextState.beat = snapshot.beat;
                        nextState.bpm = snapshot.bpm;
                        nextState.bars = snapshot.bars;
                        nextState.elapsedMs = snapshot.elapsedMs;
                        state = nextState;
                        if (metadataBpm != snapshot.bpm) updateSession(true);
                        long now = SystemClock.elapsedRealtime();
                        if (now - lastNotification >= 1000) {
                            lastNotification = now;
                            notifications.notify(NOTIFICATION_ID, notification());
                        }
                    });
                }
                @Override public void onStopped(String error) {
                    main.post(() -> {
                        if (!destroyed && token == generation) finishPlayback(error);
                    });
                }
            });
            engine.start();
        } catch (RuntimeException error) {
            finishPlayback("재생을 시작할 수 없습니다: " + error.getClass().getSimpleName());
        }
    }

    private void finishPlayback(String error) {
        ++generation;
        AudioEngine old = engine;
        engine = null;
        if (old != null) old.stop();
        State previous = state;
        State ended = new State();
        ended.bpm = previous.bpm;
        ended.bars = previous.bars;
        ended.elapsedMs = previous.elapsedMs;
        ended.beat = previous.beat;
        ended.error = error == null ? "" : error;
        state = ended;
        if (previous.playing || old != null) {
            // Preserve settings most recently saved by the Activity, changing only final BPM.
            android.content.SharedPreferences prefs = getSharedPreferences("pulse", MODE_PRIVATE);
            Config finalConfig = config.copy();
            try { finalConfig = Config.fromJson(new JSONObject(prefs.getString("config", config.toJson().toString()))); }
            catch (Exception ignored) { }
            finalConfig.bpm = Config.clamp(ended.bpm, 10, 400);
            finalConfig.sanitize();
            config = finalConfig;
            prefs.edit().putString("config", finalConfig.toJson().toString()).apply();
        }
        releaseResources();
        stopSelf();
    }

    private void releaseResources() {
        if (session != null) { updateSession(false); session.setActive(false); }
        if (audioManager != null && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; }
    }

    private void updateSession(boolean playing) {
        if (session == null) return;
        State current = state;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP
                | PlaybackState.ACTION_PLAY_PAUSE;
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_STOPPED,
                        current.elapsedMs, playing ? 1f : 0f).build());
        metadataBpm = current.bpm;
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,
                "Pulse · " + current.bpm + " BPM")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, config.meter() + " 메트로놈").build());
    }

    private PendingIntent openAppIntent() {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 30, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent actionIntent(String action, int request) {
        return PendingIntent.getService(this, request, new Intent(this, MetroService.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification notification() {
        State current = state;
        String status = current.countingIn ? "카운트인" : current.muted ? "무음 훈련" : "재생 중";
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("Pulse Metronome")
                .setContentText(current.bpm + " BPM · " + config.meter() + " · " + status)
                .setContentIntent(openAppIntent()).setOngoing(true).setOnlyAlertOnce(true)
                .setShowWhen(false).setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "정지",
                        actionIntent(ACTION_STOP, 31)).build());
        if (session != null) builder.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0));
        return builder.build();
    }

    @Override public void onDestroy() {
        destroyed = true;
        ++generation;
        AudioEngine old = engine;
        engine = null;
        if (old != null) old.stop();
        if (state.playing) {
            State oldState = state;
            State stopped = new State();
            stopped.bpm = oldState.bpm;
            stopped.bars = oldState.bars;
            stopped.elapsedMs = oldState.elapsedMs;
            state = stopped;
        }
        releaseResources();
        if (receiverRegistered) { unregisterReceiver(noisyReceiver); receiverRegistered = false; }
        if (session != null) { session.release(); session = null; }
        main.removeCallbacksAndMessages(null);
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
