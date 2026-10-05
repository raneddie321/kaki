package com.snakebrawl.myapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import com.snakebrawl.myapp.game.Game;

/** Hardware-accelerated view that drives the game loop from onDraw. */
final class GameView extends View {
    private final Game game;
    private final AndroidGfx gfx;
    private long lastNanos;
    private boolean running;

    GameView(Context context, Game game, Typeface font) {
        super(context);
        this.game = game;
        this.gfx = new AndroidGfx(font);
        setFocusable(true);
        setKeepScreenOn(true);
    }

    void resume() {
        running = true;
        lastNanos = 0;
        postInvalidateOnAnimation();
    }

    void pause() {
        running = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        game.resize(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 1f / 60f : (now - lastNanos) / 1e9f;
        lastNanos = now;
        gfx.begin(canvas, getWidth(), getHeight());
        game.frame(dt, gfx);
        if (running) postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                game.touchDown(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) game.touchMove(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex();
                game.touchUp(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                game.touchCancelAll();
                break;
            default:
                break;
        }
        return true;
    }
}
