package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;

import java.util.Calendar;
import java.util.Locale;

/**
 * Screensaver: a split-flap clock, two big cards on black, dead centre. The cards flip once a
 * minute. No network, no images, nothing running between minutes except the timer for the next flip.
 *
 * Pick it with {@code settings put secure screensaver_components com.gathin.tvhome/.FlipClockDream}.
 */
public class FlipClockDream extends DreamService {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FlipClockView clock;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            show(true);
            scheduleNext();
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        clock = new FlipClockView(this);
        setContentView(clock);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        show(false);
        scheduleNext();
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tick);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void show(boolean animate) {
        Calendar now = Calendar.getInstance();
        boolean twentyFour = DateFormat.is24HourFormat(this);
        int h = twentyFour ? now.get(Calendar.HOUR_OF_DAY) : now.get(Calendar.HOUR);
        if (!twentyFour && h == 0) h = 12;
        String hour = twentyFour ? String.format(Locale.ROOT, "%02d", h) : Integer.toString(h);
        String minute = String.format(Locale.ROOT, "%02d", now.get(Calendar.MINUTE));
        String tag = twentyFour ? "" : (now.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
        clock.setTime(hour, minute, tag, animate);
    }

    /** Wake just after the next minute starts, so the flip lands on the minute rather than before it. */
    private void scheduleNext() {
        long now = System.currentTimeMillis();
        handler.postDelayed(tick, 60_000L - now % 60_000L + 50L);
    }
}
