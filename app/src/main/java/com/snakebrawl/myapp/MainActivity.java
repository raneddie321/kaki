package com.snakebrawl.myapp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;

import com.snakebrawl.myapp.game.Game;
import com.snakebrawl.myapp.game.Platform;

public final class MainActivity extends Activity implements Platform {
    private static final int[] SOUND_RES = {
            R.raw.snd_shoot, R.raw.snd_shotgun, R.raw.snd_bolt, R.raw.snd_throw, R.raw.snd_explode,
            R.raw.snd_flame, R.raw.snd_hit, R.raw.snd_eat, R.raw.snd_power, R.raw.snd_super_ready,
            R.raw.snd_super, R.raw.snd_death, R.raw.snd_kill, R.raw.snd_victory, R.raw.snd_defeat,
            R.raw.snd_click, R.raw.snd_box,
    };

    private Game game;
    private GameView view;
    private SoundPool pool;
    private final int[] soundIds = new int[SND_COUNT];
    private final boolean[] soundLoaded = new boolean[SND_COUNT];
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        allowCutoutDrawing();
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        prefs = getSharedPreferences("snakebrawl", MODE_PRIVATE);
        loadSounds();

        Typeface font;
        try {
            font = Typeface.createFromAsset(getAssets(), "fonts/LilitaOne-Regular.ttf");
        } catch (RuntimeException e) {
            font = Typeface.DEFAULT_BOLD;
        }
        game = new Game(this);
        view = new GameView(this, game, font);
        view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                applyCutoutInsets(insets);
                return insets;
            }
        });
        setContentView(view);
        hideSystemUi();
        registerBackCallback();
    }

    private void loadSounds() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build();
        pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                if (status != 0) return;
                for (int i = 0; i < SND_COUNT; i++) if (soundIds[i] == sampleId) soundLoaded[i] = true;
            }
        });
        for (int i = 0; i < SND_COUNT; i++) soundIds[i] = pool.load(this, SOUND_RES[i], 1);
    }

    /** Lets the game draw under a display cutout on Android 9+ (compiled against an older SDK). */
    private void allowCutoutDrawing() {
        if (Build.VERSION.SDK_INT < 28) return;
        try {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.getClass().getField("layoutInDisplayCutoutMode").setInt(lp, 1 /* SHORT_EDGES */);
            getWindow().setAttributes(lp);
        } catch (Throwable ignored) {
            // Older or unusual builds: the default letterboxing is fine.
        }
    }

    private void applyCutoutInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT < 28 || insets == null) return;
        try {
            Object cutout = WindowInsets.class.getMethod("getDisplayCutout").invoke(insets);
            if (cutout == null) {
                game.setInsets(0, 0, 0, 0);
                return;
            }
            Class<?> cc = cutout.getClass();
            int l = (Integer) cc.getMethod("getSafeInsetLeft").invoke(cutout);
            int t = (Integer) cc.getMethod("getSafeInsetTop").invoke(cutout);
            int r = (Integer) cc.getMethod("getSafeInsetRight").invoke(cutout);
            int b = (Integer) cc.getMethod("getSafeInsetBottom").invoke(cutout);
            game.setInsets(l, t, r, b);
        } catch (Throwable ignored) {
            // Keep default padding.
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Object controller = Window.class.getMethod("getInsetsController").invoke(getWindow());
                if (controller != null) {
                    Class<?> typeCls = Class.forName("android.view.WindowInsets$Type");
                    int bars = (Integer) typeCls.getMethod("systemBars").invoke(null);
                    Class<?> ctl = Class.forName("android.view.WindowInsetsController");
                    ctl.getMethod("setSystemBarsBehavior", int.class).invoke(controller, 2 /* show transient by swipe */);
                    ctl.getMethod("hide", int.class).invoke(controller, bars);
                }
            } catch (Throwable ignored) {
                // The legacy flags above still apply.
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (pool != null) pool.autoResume();
        view.resume();
    }

    @Override
    protected void onPause() {
        game.onPause();
        view.pause();
        if (pool != null) pool.autoPause();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!game.onBack()) super.onBackPressed();
    }

    /**
     * From target SDK 36 (Android 16) predictive back is on by default and onBackPressed is no
     * longer called, so on Android 13+ the back gesture is routed through an OnBackInvokedCallback.
     * Registered via reflection because the app is compiled against an older SDK.
     */
    private void registerBackCallback() {
        if (Build.VERSION.SDK_INT < 33) return;
        try {
            Object dispatcher = Activity.class.getMethod("getOnBackInvokedDispatcher").invoke(this);
            final Class<?> cbClass = Class.forName("android.window.OnBackInvokedCallback");
            Object callback = java.lang.reflect.Proxy.newProxyInstance(cbClass.getClassLoader(), new Class<?>[]{cbClass},
                    new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                            String name = method.getName();
                            if ("onBackInvoked".equals(name)) {
                                if (!game.onBack()) moveTaskToBack(true);
                                return null;
                            }
                            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                            if ("equals".equals(name)) return proxy == args[0];
                            if ("toString".equals(name)) return "SnakeBrawlBackCallback";
                            return null;
                        }
                    });
            dispatcher.getClass().getMethod("registerOnBackInvokedCallback", int.class, cbClass)
                    .invoke(dispatcher, 0 /* PRIORITY_DEFAULT */, callback);
        } catch (Throwable ignored) {
            // Fall back to onBackPressed.
        }
    }

    @Override
    protected void onDestroy() {
        if (pool != null) {
            pool.release();
            pool = null;
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ Platform

    @Override
    public void playSound(int id, float volume) {
        if (pool == null || id < 0 || id >= SND_COUNT || !soundLoaded[id]) return;
        float v = Math.max(0f, Math.min(1f, volume));
        pool.play(soundIds[id], v, v, 1, 0, 1f);
    }

    @Override
    public int loadInt(String key, int def) {
        return prefs.getInt(key, def);
    }

    @Override
    public void saveInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    @Override
    public boolean launchPurchase(String productId) {
        // Google Play Billing is not integrated yet: the game falls back to its test checkout.
        // To go live, start a BillingClient purchase flow here and call
        // game.onPurchaseResult(productId, true) once the purchase is verified and consumed.
        return false;
    }

    @Override
    public void vibrate(int millis) {
        if (view != null) {
            view.performHapticFeedback(millis > 100 ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }
}
