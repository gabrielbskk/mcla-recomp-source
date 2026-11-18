package org.mcla.recomp;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Edit the normalized Xbox 360 touch HUD & vertical analog pedals layout. */
public final class MclaTouchEditorActivity extends Activity {
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable pill(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(Math.max(1, dp(1)), stroke);
        return d;
    }

    private Button styledButton(String text, boolean primary) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(12.5f);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? 0xFF04131D : 0xFFEAF6FF);
        b.setBackground(pill(primary ? 0xFF38BDF8 : 0xFF172636,
                primary ? 0xFF38BDF8 : 0xFF2D465E, 12));
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        b.setMinHeight(dp(36));
        b.setMinimumHeight(dp(36));
        return b;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF080E16);

        MclaTouchControls preview = new MclaTouchControls(this, true);
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setGravity(Gravity.CENTER_HORIZONTAL);
        tools.setPadding(dp(16), dp(10), dp(16), dp(12));
        tools.setBackground(pill(0xE80B1420, 0x6638BDF8, 18));

        TextView title = new TextView(this);
        title.setGravity(Gravity.CENTER);
        title.setText("XBOX 360 HUD & ANALOG PEDALS EDITOR");
        title.setTextColor(0xFF38BDF8);
        title.setTextSize(12f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tools.addView(title);

        TextView instructions = new TextView(this);
        instructions.setGravity(Gravity.CENTER);
        instructions.setText("Drag any button, stick, or GAS/BRK pedal. Slide higher on pedals in-game for more % force.");
        instructions.setTextColor(0xFFCBD5E1);
        instructions.setTextSize(11.5f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-2, -2);
        subParams.topMargin = dp(2);
        subParams.bottomMargin = dp(8);
        tools.addView(instructions, subParams);

        // Sliders row (Size + Opacity)
        LinearLayout slidersRow = new LinearLayout(this);
        slidersRow.setOrientation(LinearLayout.HORIZONTAL);
        slidersRow.setGravity(Gravity.CENTER_VERTICAL);

        // Size column
        LinearLayout sizeCol = new LinearLayout(this);
        sizeCol.setOrientation(LinearLayout.VERTICAL);
        TextView sizeLabel = new TextView(this);
        sizeLabel.setTextColor(Color.WHITE);
        sizeLabel.setTextSize(11.5f);
        sizeLabel.setGravity(Gravity.CENTER);
        sizeLabel.setText("HUD Size: " + preview.getSizePercent() + "%");
        sizeCol.addView(sizeLabel);
        SeekBar sizeBar = new SeekBar(this);
        sizeBar.setMax(90);
        sizeBar.setProgress(preview.getSizePercent() - 60);
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                int val = 60 + progress;
                preview.setSizePercent(val);
                sizeLabel.setText("HUD Size: " + val + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        sizeCol.addView(sizeBar);
        slidersRow.addView(sizeCol, new LinearLayout.LayoutParams(0, -2, 1f));

        // Opacity column
        LinearLayout alphaCol = new LinearLayout(this);
        alphaCol.setOrientation(LinearLayout.VERTICAL);
        TextView alphaLabel = new TextView(this);
        alphaLabel.setTextColor(Color.WHITE);
        alphaLabel.setTextSize(11.5f);
        alphaLabel.setGravity(Gravity.CENTER);
        alphaLabel.setText("Opacity: " + preview.getOpacityPercent() + "%");
        alphaCol.addView(alphaLabel);
        SeekBar alphaBar = new SeekBar(this);
        alphaBar.setMax(65);
        alphaBar.setProgress(preview.getOpacityPercent() - 35);
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                int val = 35 + progress;
                preview.setOpacityPercent(val);
                alphaLabel.setText("Opacity: " + val + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        alphaCol.addView(alphaBar);
        slidersRow.addView(alphaCol, new LinearLayout.LayoutParams(0, -2, 1f));

        tools.addView(slidersRow, new LinearLayout.LayoutParams(-1, -2));

        // Action buttons row
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-2, -2);
        actionsParams.topMargin = dp(8);

        Button pedalsBtn = styledButton(
                preview.getPedalsMode() ? "Pedals (%): ON" : "Pedals (%): OFF", false);
        pedalsBtn.setOnClickListener(v -> {
            boolean next = !preview.getPedalsMode();
            preview.setPedalsMode(next);
            pedalsBtn.setText(next ? "Pedals (%): ON" : "Pedals (%): OFF");
        });
        actions.addView(pedalsBtn);

        Button resetBtn = styledButton("Reset", false);
        LinearLayout.LayoutParams gap1 = new LinearLayout.LayoutParams(-2, -2);
        gap1.leftMargin = dp(8);
        resetBtn.setOnClickListener(v -> {
            preview.resetLayout();
            sizeBar.setProgress(preview.getSizePercent() - 60);
            alphaBar.setProgress(preview.getOpacityPercent() - 35);
        });
        actions.addView(resetBtn, gap1);

        Button hideBtn = styledButton("Hide Bar", false);
        LinearLayout.LayoutParams gap2 = new LinearLayout.LayoutParams(-2, -2);
        gap2.leftMargin = dp(8);
        actions.addView(hideBtn, gap2);

        Button doneBtn = styledButton("Save & Done", true);
        LinearLayout.LayoutParams gap3 = new LinearLayout.LayoutParams(-2, -2);
        gap3.leftMargin = dp(8);
        doneBtn.setOnClickListener(v -> finish());
        actions.addView(doneBtn, gap3);

        tools.addView(actions, actionsParams);

        Button showTools = styledButton("Show HUD Tools", false);
        showTools.setVisibility(View.GONE);
        hideBtn.setOnClickListener(v -> {
            tools.setVisibility(View.GONE);
            showTools.setVisibility(View.VISIBLE);
        });
        showTools.setOnClickListener(v -> {
            showTools.setVisibility(View.GONE);
            tools.setVisibility(View.VISIBLE);
        });

        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                dp(540), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        barParams.topMargin = dp(10);
        root.addView(tools, barParams);

        FrameLayout.LayoutParams toggleParams = new FrameLayout.LayoutParams(
                -2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        toggleParams.topMargin = dp(8);
        root.addView(showTools, toggleParams);

        setContentView(root);
        applyImmersive();
    }

    private void applyImmersive() {
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }
}
