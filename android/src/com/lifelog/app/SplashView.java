package com.lifelog.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 开屏：黑底 + 中心微光 + logo 放大进场 + 光环扩散 + 蓝色光带 + 「今天 x/y」。
 * mode 2 = 完整（等数据，显示进度数字），mode 1 = 快速（只进场就退）。
 */
public class SplashView extends FrameLayout {
    private final int mode;
    private final long born = System.currentTimeMillis();
    private boolean gone = false;

    private final ImageView logo;
    private final RingView ring;
    private final LineView line;
    private final TextView count;
    private final TextView name;

    public SplashView(Context c, int mode, int iconRes) {
        super(c);
        this.mode = mode;
        setBackgroundColor(Color.rgb(5, 8, 14));
        setWillNotDraw(false);
        setClickable(true);

        float d = getResources().getDisplayMetrics().density;

        ring = new RingView(c);
        LayoutParams rp = new LayoutParams(-1, -1);
        addView(ring, rp);

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        LayoutParams bp = new LayoutParams(-1, -1);
        addView(box, bp);

        logo = new ImageView(c);
        if (iconRes != 0) {
            try {
                logo.setImageResource(iconRes);
            } catch (Exception e) {
            }
        }
        int sz = (int) (112 * d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
        lp.bottomMargin = (int) (22 * d);
        box.addView(logo, lp);

        name = new TextView(c);
        name.setText("今日待办");
        name.setTextColor(Color.rgb(226, 232, 244));
        name.setTextSize(15);
        name.setLetterSpacing(0.08f);
        name.setGravity(Gravity.CENTER);
        box.addView(name);

        count = new TextView(c);
        count.setTextColor(Color.rgb(128, 176, 255));
        count.setTextSize(13.5f);
        count.setGravity(Gravity.CENTER);
        count.setAlpha(0f);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.topMargin = (int) (10 * d);
        box.addView(count, cp);

        line = new LineView(c);
        LinearLayout.LayoutParams lpp = new LinearLayout.LayoutParams(
                (int) (72 * d), (int) (2 * d));
        lpp.topMargin = (int) (26 * d);
        box.addView(line, lpp);

        // 进场：logo 0.7→1.0 + 淡入，名字轻微上浮
        logo.setScaleX(0.7f);
        logo.setScaleY(0.7f);
        logo.setAlpha(0f);
        logo.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320)
                .setInterpolator(new DecelerateInterpolator()).start();

        name.setAlpha(0f);
        name.setTranslationY(8 * d);
        name.animate().alpha(1f).translationY(0).setStartDelay(120).setDuration(300).start();

        line.setPivotX(0f);
        line.setScaleX(0.04f);
        line.animate().scaleX(0.62f).setStartDelay(150).setDuration(700).start();

        ring.pulse();
        postDelayed(new Runnable() {
            public void run() {
                logo.animate().scaleX(1.02f).scaleY(1.02f).setDuration(380)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        }, 340);

        // 硬兜底：2.2 秒一定退
        postDelayed(new Runnable() {
            public void run() {
                fadeOut();
            }
        }, 2200);
    }

    private long minMs() {
        return mode == 1 ? 420 : 650;
    }

    /** 数据就绪：光带走满 + 光环再扩散 + 数字浮入，然后停留再退 */
    public void handoff(final String text) {
        if (gone) return;
        final long wait = Math.max(0, minMs() - (System.currentTimeMillis() - born));
        postDelayed(new Runnable() {
            public void run() {
                if (gone) return;
                if (mode == 1) {
                    fadeOut();
                    return;
                }
                ring.pulse();
                line.animate().scaleX(1f).setDuration(260)
                        .setInterpolator(new DecelerateInterpolator()).start();
                count.setText(text == null ? "" : text);
                count.setTranslationY(10 * getResources().getDisplayMetrics().density);
                count.animate().alpha(1f).translationY(0).setDuration(220).start();
                logo.animate().scaleX(1.03f).scaleY(1.03f).setDuration(240).start();
                postDelayed(new Runnable() {
                    public void run() {
                        fadeOut();
                    }
                }, 700);
            }
        }, wait);
    }

    public void fadeOut() {
        if (gone) return;
        gone = true;
        animate().alpha(0f).scaleX(1.03f).scaleY(1.03f).setDuration(260)
                .withEndAction(new Runnable() {
                    public void run() {
                        try {
                            ((android.view.ViewGroup) getParent()).removeView(SplashView.this);
                        } catch (Exception e) {
                        }
                    }
                }).start();
    }

    @Override
    protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(w / 2f, h * 0.52f, w * 0.78f,
                new int[]{Color.argb(56, 34, 74, 138), Color.argb(0, 5, 8, 14)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
    }

    /** 一圈细环，从 0.6 倍扩到 2.2 倍并淡出 */
    static class RingView extends View {
        private final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float scale = 0f, alpha = 0f;

        RingView(Context c) {
            super(c);
            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeWidth(c.getResources().getDisplayMetrics().density);
            pt.setColor(Color.rgb(90, 162, 255));
        }

        void pulse() {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(680);
            a.setInterpolator(new DecelerateInterpolator());
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator an) {
                    float v = (Float) an.getAnimatedValue();
                    scale = 0.6f + v * 1.6f;
                    alpha = (1f - v) * 0.55f;
                    invalidate();
                }
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            if (alpha <= 0.01f) return;
            pt.setAlpha((int) (alpha * 255));
            float r = Math.min(getWidth(), getHeight()) * 0.16f * scale;
            cv.drawCircle(getWidth() / 2f, getHeight() / 2f, r, pt);
        }
    }

    /** 2dp 光带，蓝→青渐变 */
    static class LineView extends View {
        private final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

        LineView(Context c) {
            super(c);
            pt.setStrokeCap(Paint.Cap.ROUND);
            pt.setStrokeWidth(c.getResources().getDisplayMetrics().density * 2f);
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            int w = getWidth(), h = getHeight();
            if (w <= 0) return;
            pt.setShader(new LinearGradient(0, 0, w, 0,
                    new int[]{Color.rgb(74, 140, 255), Color.rgb(52, 211, 153)},
                    null, Shader.TileMode.CLAMP));
            cv.drawLine(0, h / 2f, w, h / 2f, pt);
        }
    }
}
