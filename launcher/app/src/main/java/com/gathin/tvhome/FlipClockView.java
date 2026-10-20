package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Two big cards, hours and minutes, each split by a hairline. When the minute changes the top half
 * of the old digit folds down and the bottom half of the new one unfolds, like a split-flap clock.
 *
 * The clock sits dead centre on the screen and never moves. Digits are centred on their drawn
 * shapes rather than their font advance, so a narrow "1" is as centred in its card as an "8".
 *
 * Drawn with plain canvas calls and only redrawn while a flip runs (about 0.7 s a minute); between
 * minutes nothing is invalidated.
 */
final class FlipClockView extends View {

    private static final long FLIP_MS = 700L;
    private static final int CARD = 0xFF18181A;
    private static final int DIGIT = 0xFFC4C4C8;
    private static final int TAG = 0x99C4C4C8;
    /** Share of the screen height a card takes, and the card's width as a share of its height. */
    private static final float CARD_HEIGHT = 0.62f;
    private static final float CARD_ASPECT = 0.89f;

    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint digitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint();
    private final Paint linePaint = new Paint();
    private final Rect inkBounds = new Rect();
    private final Rect flatBounds = new Rect();
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);

    private String hourOld = "";
    private String hourNew = "";
    private String minuteOld = "";
    private String minuteNew = "";
    private String tag = "";
    /** 0 at the start of a flip, 1 when settled. */
    private float flip = 1f;
    private float digitSize = -1f;

    FlipClockView(Context context) {
        super(context);
        cardPaint.setColor(CARD);
        Typeface face = getResources().getFont(R.font.outfit_medium);
        digitPaint.setTypeface(face);
        digitPaint.setColor(DIGIT);
        digitPaint.setTextAlign(Paint.Align.LEFT);
        digitPaint.setTextScaleX(0.82f);
        tagPaint.setTypeface(face);
        tagPaint.setColor(TAG);
        shadePaint.setColor(0xFF000000);
        linePaint.setColor(0xFF000000);
        setBackgroundColor(0xFF000000);
        animator.setDuration(FLIP_MS);
        animator.setInterpolator(new DecelerateInterpolator(1.4f));
        animator.addUpdateListener(a -> {
            flip = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    /** Show a time. With {@code animate} the changed cards flip; without, it just appears. */
    void setTime(String hour, String minute, String ampm, boolean animate) {
        if (hour.equals(hourNew) && minute.equals(minuteNew) && ampm.equals(tag)) return;
        animator.cancel();
        if (animate && !hourNew.isEmpty()) {
            hourOld = hourNew;
            minuteOld = minuteNew;
            hourNew = hour;
            minuteNew = minute;
            tag = ampm;
            flip = 0f;
            animator.start();
        } else {
            hourOld = hourNew = hour;
            minuteOld = minuteNew = minute;
            tag = ampm;
            flip = 1f;
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cardH = Math.min(h * CARD_HEIGHT, w * 0.31f / CARD_ASPECT);
        float cardW = cardH * CARD_ASPECT;
        float gap = cardW * 0.075f;
        float left = (w - 2 * cardW - gap) / 2f;
        float top = (h - cardH) / 2f;
        prepareText(cardH);
        drawCard(canvas, left, top, cardW, cardH, hourOld, hourNew, true);
        drawCard(canvas, left + cardW + gap, top, cardW, cardH, minuteOld, minuteNew, false);
    }

    /** Size the digits to the card once per card size. */
    private void prepareText(float cardH) {
        if (digitSize == cardH) return;
        digitSize = cardH;
        digitPaint.setTextSize(cardH * 0.78f);
        tagPaint.setTextSize(cardH * 0.064f);
        // A "1" is flat top and bottom, so its box is the digit height with no round overshoot.
        digitPaint.getTextBounds("1", 0, 1, flatBounds);
    }

    private void drawCard(Canvas c, float x, float y, float w, float h, String oldText, String newText,
                          boolean withTag) {
        float mid = y + h / 2f;
        boolean flipping = flip < 1f && !oldText.equals(newText);
        // Settled, both halves show the new digit. Mid-flip the top is already the new digit,
        // waiting under the old top half as it folds, and the bottom is still the old digit.
        drawHalf(c, x, y, w, h, newText, true, 1f, 0f);
        drawHalf(c, x, y, w, h, flipping ? oldText : newText, false, 1f, 0f);
        if (flipping) {
            if (flip < 0.5f) {
                float s = 1f - flip * 2f;
                drawHalf(c, x, y, w, h, oldText, true, s, (1f - s) * 0.6f);
            } else {
                float s = (flip - 0.5f) * 2f;
                drawHalf(c, x, y, w, h, newText, false, s, (1f - s) * 0.6f);
            }
        }
        float line = Math.max(2f, h * 0.007f);
        c.drawRect(x, mid - line / 2f, x + w, mid + line / 2f, linePaint);
        if (withTag && !tag.isEmpty()) {
            c.drawText(tag, x + w * 0.06f, y + h * 0.12f, tagPaint);
        }
    }

    /**
     * One half of a card, folded about its middle line: {@code scale} 1 is flat, 0 is edge-on.
     * {@code shade} darkens it as it turns away from the viewer.
     */
    private void drawHalf(Canvas c, float x, float y, float w, float h, String text, boolean top,
                          float scale, float shade) {
        float mid = y + h / 2f;
        float radius = w * 0.07f;
        c.save();
        if (scale != 1f) c.scale(1f, Math.max(scale, 0.001f), x, mid);
        c.clipRect(x, top ? y : mid, x + w, top ? mid : y + h);
        c.drawRoundRect(x, y, x + w, y + h, radius, radius, cardPaint);
        // Centre the drawn shapes on the card's middle, both ways: the ink box of this string
        // across, the digit height down, so the split line cuts every digit at its middle.
        digitPaint.getTextBounds(text, 0, text.length(), inkBounds);
        float tx = x + w / 2f - (inkBounds.left + inkBounds.right) / 2f;
        float ty = mid - (flatBounds.top + flatBounds.bottom) / 2f;
        c.drawText(text, tx, ty, digitPaint);
        if (shade > 0f) {
            shadePaint.setAlpha((int) (Math.min(shade, 1f) * 255));
            c.drawRect(x, y, x + w, y + h, shadePaint);
        }
        c.restore();
    }
}
