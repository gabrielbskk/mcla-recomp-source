package org.mcla.recomp;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MclaLauncherActivity extends Activity {
    private static final String TAG = "MCLA Import";
    private static final int PICK_GAME_FOLDER = 1;
    private static final int PICK_DRIVER_ZIP = 2;
    private static final int PICK_SHADER_PACK = 3;
    private static final long MAX_DRIVER_BYTES = 128L * 1024 * 1024;
    private static final long MAX_SHADER_PACK_BYTES = 64L * 1024 * 1024;
    private static final String SHADER_PACK_NAME = "545407F8.shaderpack";
    private static final String[] REQUIRED = {
        "default.xex", "xarchive_audio.rpf", "xarchive_audlo.rpf",
        "xarchive_cache.rpf", "xarchive_music.rpf",
        "attract576_16x9.bik", "attract576_4x3.bik", "attract720.bik",
        "intro576_16x9.bik", "intro576_4x3.bik", "intro720.bik",
        "nxeart", "root_directory_padding.pad"
    };

    private static final int BG_MAIN = 0xFF070C13;
    private static final int BG_CARD = 0xFF0F1926;
    private static final int BG_HERO = 0xFF111E2E;
    private static final int BG_FIELD = 0xFF162536;
    private static final int STROKE_CARD = 0xFF1E3248;
    private static final int STROKE_ACCENT = 0xFF38BDF8;
    private static final int ACCENT_CYAN = 0xFF38BDF8;
    private static final int ACCENT_GREEN = 0xFF22C55E;
    private static final int ACCENT_AMBER = 0xFFF59E0B;
    private static final int TEXT_PRIMARY = 0xFFF8FAFC;
    private static final int TEXT_MUTED = 0xFF94A3B8;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView heroSummaryChip;
    private TextView status;
    private TextView driverStatus;
    private TextView shaderStatus;
    private TextView logStatusView;
    private Button topPlayButton;
    private Button playButton;
    private Button pickButton;
    private Button driverButton;
    private Button systemButton;
    private Button shaderImportButton;
    private Button emptyStreetsButton;
    private Button aspectButton;
    private Button fpsOverlayButton;
    private Button sustainedPerfButton;
    private Button pedalsToggleButton;
    private Button exportLogButton;
    private Spinner turnipDebugSpinner;
    private ProgressBar progress;
    private boolean playPending;

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable box(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(Math.max(1, dp(1)), stroke);
        return d;
    }

    private TextView label(String text, float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(sizeSp);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private LinearLayout sectionCard(LinearLayout parent, String badge, String title, String subtitle) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(18));
        card.setBackground(box(BG_CARD, STROKE_CARD, 20));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(14);
        parent.addView(card, params);

        TextView tagView = label(badge, 11f, ACCENT_CYAN, true);
        tagView.setLetterSpacing(0.08f);
        card.addView(tagView);

        TextView titleView = label(title, 18.5f, TEXT_PRIMARY, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(2);
        card.addView(titleView, tp);

        TextView subView = label(subtitle, 12.5f, TEXT_MUTED, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(3);
        sp.bottomMargin = dp(10);
        card.addView(subView, sp);
        return card;
    }

    private void styleButton(Button button, int styleMode) {
        // styleMode: 0 = secondary neutral, 1 = primary launch, 2 = active toggle ON, 3 = accent outline
        button.setAllCaps(false);
        button.setTextSize(14f);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        if (styleMode == 1) {
            button.setTextColor(0xFF04141F);
            button.setBackground(box(ACCENT_CYAN, ACCENT_CYAN, 14));
        } else if (styleMode == 2) {
            button.setTextColor(0xFFECFDF5);
            button.setBackground(box(0xFF0E3229, 0xFF10B981, 14));
        } else if (styleMode == 3) {
            button.setTextColor(0xFFE0F2FE);
            button.setBackground(box(0xFF10283D, ACCENT_CYAN, 14));
        } else {
            button.setTextColor(TEXT_PRIMARY);
            button.setBackground(box(BG_FIELD, 0xFF28415A, 14));
        }
    }

    private void addFullButton(LinearLayout card, Button button, int styleMode) {
        styleButton(button, styleMode);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        card.addView(button, params);
    }

    private void addRowButtons(LinearLayout card, Button left, int leftStyle, Button right, int rightStyle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(8);

        styleButton(left, leftStyle);
        styleButton(right, rightStyle);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = dp(5);
        LinearLayout.LayoutParams rgt = new LinearLayout.LayoutParams(0, -2, 1f);
        rgt.leftMargin = dp(5);

        row.addView(left, lp);
        row.addView(right, rgt);
        card.addView(row, rp);
    }

    private Spinner createDarkSpinner(final String[] items) {
        Spinner spinner = new Spinner(this);
        spinner.setBackground(box(BG_FIELD, 0xFF2A445E, 13));
        spinner.setPadding(dp(12), dp(4), dp(12), dp(4));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(TEXT_PRIMARY);
                tv.setTextSize(13.5f);
                tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                tv.setPadding(dp(4), dp(8), dp(4), dp(8));
                return tv;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setBackgroundColor(0xFF0E1824);
                tv.setTextColor(TEXT_PRIMARY);
                tv.setTextSize(13.5f);
                tv.setPadding(dp(14), dp(12), dp(14), dp(12));
                return tv;
            }
        };
        spinner.setAdapter(adapter);
        return spinner;
    }

    private String versionLabel() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (info.versionName != null && !info.versionName.isEmpty()) {
                return "v" + info.versionName;
            }
        } catch (Exception ignored) {}
        return "Experimental";
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        MclaLogExporter.exportAsync(this);
        getWindow().setStatusBarColor(BG_MAIN);
        getWindow().setNavigationBarColor(BG_MAIN);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(18), dp(16), dp(28));
        layout.setBackgroundColor(BG_MAIN);

        // ====================================================================
        // HERO DASHBOARD HEADER
        // ====================================================================
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(box(BG_HERO, 0xFF284766, 22));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2);
        heroParams.bottomMargin = dp(16);
        layout.addView(hero, heroParams);

        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setOrientation(LinearLayout.HORIZONTAL);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);

        ImageView appIcon = new ImageView(this);
        int coverRes = getResources().getIdentifier("mcla_cover_badge", "drawable", getPackageName());
        if (coverRes == 0) {
            coverRes = getApplicationInfo().icon;
        }
        if (coverRes != 0) {
            appIcon.setImageResource(coverRes);
        }
        appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(74), dp(74));
        iconParams.rightMargin = dp(14);
        heroTop.addView(appIcon, iconParams);

        LinearLayout heroTextCol = new LinearLayout(this);
        heroTextCol.setOrientation(LinearLayout.VERTICAL);
        TextView expBadge = label("EXPERIMENTAL · " + versionLabel().toUpperCase(), 11f, ACCENT_AMBER, true);
        expBadge.setLetterSpacing(0.06f);
        heroTextCol.addView(expBadge);

        TextView heroTitle = label("Midnight Club: LA", 23f, TEXT_PRIMARY, true);
        LinearLayout.LayoutParams htp = new LinearLayout.LayoutParams(-1, -2);
        htp.topMargin = dp(2);
        heroTextCol.addView(heroTitle, htp);

        TextView heroSub = label("Complete Edition · Snapdragon ARM64 Recomp", 12.5f, 0xFFB8CCE0, false);
        LinearLayout.LayoutParams hsp = new LinearLayout.LayoutParams(-1, -2);
        hsp.topMargin = dp(2);
        heroTextCol.addView(heroSub, hsp);

        heroTop.addView(heroTextCol, new LinearLayout.LayoutParams(0, -2, 1f));
        hero.addView(heroTop, new LinearLayout.LayoutParams(-1, -2));

        heroSummaryChip = label("", 12f, ACCENT_CYAN, true);
        heroSummaryChip.setBackground(box(0xFF0A1420, 0xFF1E3750, 10));
        heroSummaryChip.setPadding(dp(12), dp(7), dp(12), dp(7));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-1, -2);
        chipParams.topMargin = dp(12);
        hero.addView(heroSummaryChip, chipParams);

        topPlayButton = new Button(this);
        topPlayButton.setOnClickListener(v -> startGame());
        addFullButton(hero, topPlayButton, 1);

        // ====================================================================
        // 01 · PERFORMANCE & WORLD (SNAPDRAGON / S23)
        // ====================================================================
        LinearLayout perfCard = sectionCard(layout,
                "01 · PERFORMANCE PROFILES",
                "Graphics & World Density",
                "Optimized presets for Snapdragon / Adreno Turnip syncdraw and 8 GB+ devices.");

        perfCard.addView(label("Performance Preset", 12.5f, TEXT_MUTED, true));
        final String[] perfLabels = {
            "Balanced (Recommended · High FPS + Clean Cars)",
            "Max FPS / Low Draw-Call (Best for Heavy Turnip syncdraw)",
            "Console Visuals (Original Xbox 360 Shadows & Blur)"
        };
        final String[] perfValues = {
            MclaRuntimeProfiles.PERF_BALANCED,
            MclaRuntimeProfiles.PERF_MAX_FPS,
            MclaRuntimeProfiles.PERF_CONSOLE
        };
        Spinner perfSpinner = createDarkSpinner(perfLabels);
        String currentPerf = MclaRuntimeProfiles.perfPreset(this);
        int perfIdx = 0;
        for (int i = 0; i < perfValues.length; i++) {
            if (perfValues[i].equals(currentPerf)) perfIdx = i;
        }
        perfSpinner.setSelection(perfIdx);
        perfSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                MclaRuntimeProfiles.setPerfPreset(MclaLauncherActivity.this, perfValues[pos]);
                refreshHeroSummary();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        LinearLayout.LayoutParams perfSpinParams = new LinearLayout.LayoutParams(-1, -2);
        perfSpinParams.topMargin = dp(5);
        perfSpinParams.bottomMargin = dp(4);
        perfCard.addView(perfSpinner, perfSpinParams);

        emptyStreetsButton = new Button(this);
        emptyStreetsButton.setOnClickListener(v -> {
            boolean next = !MclaRuntimeProfiles.emptyStreets(this);
            MclaRuntimeProfiles.setEmptyStreets(this, next);
            updatePerfToggles();
            refreshHeroSummary();
        });
        addFullButton(perfCard, emptyStreetsButton, 0);

        aspectButton = new Button(this);
        aspectButton.setOnClickListener(v -> {
            boolean next = !MclaRuntimeProfiles.widescreenFill(this);
            MclaRuntimeProfiles.setWidescreenFill(this, next);
            updatePerfToggles();
        });
        addFullButton(perfCard, aspectButton, 0);

        fpsOverlayButton = new Button(this);
        fpsOverlayButton.setOnClickListener(v -> {
            MclaRuntimeProfiles.setFpsOverlay(this, !MclaRuntimeProfiles.fpsOverlay(this));
            updatePerfToggles();
        });
        sustainedPerfButton = new Button(this);
        sustainedPerfButton.setOnClickListener(v -> {
            MclaRuntimeProfiles.setSustainedPerformance(this, !MclaRuntimeProfiles.sustainedPerformance(this));
            updatePerfToggles();
        });
        addRowButtons(perfCard, fpsOverlayButton, 0, sustainedPerfButton, 0);
        updatePerfToggles();

        // ====================================================================
        // 02 · XBOX 360 CONTROLS & ANALOG PEDALS
        // ====================================================================
        LinearLayout controlsCard = sectionCard(layout,
                "02 · XBOX 360 TOUCH & INPUT",
                "Controls & Analog % Pedals",
                "Xbox 360 jewel layout with vertical GAS / BRK pedals: slide higher for stronger % force, lower for gentler throttle.");

        Button touchEditor = new Button(this);
        touchEditor.setText("Customize Xbox 360 HUD & Pedals");
        touchEditor.setOnClickListener(v ->
                startActivity(new Intent(this, MclaTouchEditorActivity.class)));
        addFullButton(controlsCard, touchEditor, 3);

        pedalsToggleButton = new Button(this);
        pedalsToggleButton.setOnClickListener(v -> {
            boolean next = !MclaTouchControls.isPedalsEnabled(this);
            MclaTouchControls.setPedalsEnabled(this, next);
            updateControlsStatus();
        });
        addFullButton(controlsCard, pedalsToggleButton, 0);
        updateControlsStatus();

        TextView kmLabel = label("Physical Keyboard / Mouse Mode", 12.5f, TEXT_MUTED, true);
        LinearLayout.LayoutParams kmp = new LinearLayout.LayoutParams(-1, -2);
        kmp.topMargin = dp(12);
        controlsCard.addView(kmLabel, kmp);

        Spinner keyboardMouse = createDarkSpinner(new String[]{
            "Off (Touch HUD / Xbox & Bluetooth Gamepad)",
            "Keyboard Driving (WASD + Arrows)",
            "Keyboard + Mouse Camera"
        });
        keyboardMouse.setSelection(MclaKeyboardMouse.mode(this));
        keyboardMouse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                getSharedPreferences(MclaKeyboardMouse.PREFS, MODE_PRIVATE).edit()
                        .putInt(MclaKeyboardMouse.KEY, MclaKeyboardMouse.normalize(pos)).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        LinearLayout.LayoutParams kmSpinParams = new LinearLayout.LayoutParams(-1, -2);
        kmSpinParams.topMargin = dp(5);
        controlsCard.addView(keyboardMouse, kmSpinParams);

        // ====================================================================
        // 03 · VULKAN & TURNIP DRIVER
        // ====================================================================
        LinearLayout driverCard = sectionCard(layout,
                "03 · ADRENO VULKAN DRIVER",
                "Turnip & System Vulkan",
                "Import an AdrenoTools Turnip .zip or run on Qualcomm System Vulkan (debug callback storm fixed in v0.1.64).");

        driverStatus = label("", 13.5f, ACCENT_CYAN, true);
        driverCard.addView(driverStatus);

        driverButton = new Button(this);
        driverButton.setText("Import Turnip (.zip)");
        driverButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, PICK_DRIVER_ZIP);
        });

        systemButton = new Button(this);
        systemButton.setText("Use System Driver");
        systemButton.setOnClickListener(v -> {
            File selection = new File(driverFolder(), "selected.txt");
            if (selection.exists() && !selection.delete()) {
                status.setText("Could not switch back to the system driver.");
                return;
            }
            updateDriverStatus();
            refreshHeroSummary();
        });
        addRowButtons(driverCard, driverButton, 0, systemButton, 0);

        TextView debugLabel = label("Turnip Sync Mode (syncdraw stabilizes flickering wheels & headlights)",
                12.5f, TEXT_MUTED, true);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.topMargin = dp(12);
        driverCard.addView(debugLabel, dlp);

        final String[] tuOptions = {
            "syncdraw (Recommended · Fixes wheel & light flicker)",
            "No switch (Fastest · May flicker on Turnip)",
            "noubwc",
            "flushall",
            "nolrz",
            "sysmem"
        };
        turnipDebugSpinner = createDarkSpinner(tuOptions);
        String currentTu = getSharedPreferences("driver_debug", MODE_PRIVATE)
                .getString("tu_debug", "syncdraw");
        int selectedTu = 0;
        if ("none".equals(currentTu)) {
            selectedTu = 1;
        } else {
            for (int i = 2; i < tuOptions.length; i++) {
                if (tuOptions[i].equals(currentTu)) selectedTu = i;
            }
        }
        turnipDebugSpinner.setSelection(selectedTu);
        turnipDebugSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String value = pos == 0 ? "syncdraw" : pos == 1 ? "none" : tuOptions[pos];
                getSharedPreferences("driver_debug", MODE_PRIVATE).edit()
                        .putString("tu_debug", value).apply();
                updateDriverStatus();
                refreshHeroSummary();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        LinearLayout.LayoutParams tuSpinParams = new LinearLayout.LayoutParams(-1, -2);
        tuSpinParams.topMargin = dp(5);
        driverCard.addView(turnipDebugSpinner, tuSpinParams);
        updateDriverStatus();

        // ====================================================================
        // 04 · AUDIO ENGINE (S23 & REDMAGIC 11 AIR ANTI-CUT)
        // ====================================================================
        LinearLayout audioCard = sectionCard(layout,
                "04 · AUDIO ANTI-CUT ENGINE",
                "Stereo Downmix & Buffer Priority",
                "Lock-free 2ch SDL callback + elevated host Audio Worker / XMA Decoder thread priority to prevent audio dropouts.");

        final String[] audioLabels = {
            "Adaptive Anti-Cut (16–28 frames · Recommended)",
            "Safe / Anti-Cut (20–32 frames · 1024 buf)",
            "Low-Latency Fast Path (12–24 frames)"
        };
        final String[] audioValues = {
            MclaRuntimeProfiles.AUDIO_ADAPTIVE,
            MclaRuntimeProfiles.AUDIO_SAFE,
            MclaRuntimeProfiles.AUDIO_LOW_LATENCY
        };
        Spinner audioSpinner = createDarkSpinner(audioLabels);
        String curAudio = MclaRuntimeProfiles.audioProfile(this);
        int audioIdx = 0;
        for (int i = 0; i < audioValues.length; i++) {
            if (audioValues[i].equals(curAudio)) audioIdx = i;
        }
        audioSpinner.setSelection(audioIdx);
        audioSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                MclaRuntimeProfiles.setAudioProfile(MclaLauncherActivity.this, audioValues[pos]);
                refreshHeroSummary();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        audioCard.addView(audioSpinner, new LinearLayout.LayoutParams(-1, -2));

        // ====================================================================
        // 05 · GAME FILES, SHADER PACK & DIAGNOSTIC LOGS
        // ====================================================================
        LinearLayout dataCard = sectionCard(layout,
                "05 · DATA, SHADERS & DIAGNOSTICS",
                "Game Files & Live Log Recorder",
                "Manage your 13 extracted Xbox 360 files, optional shader pack, and automatic diagnostic logs.");

        boolean ready = hasGameFiles();
        status = label(ready
                ? "✓ Game files verified (13/13 files ready in private storage)."
                : "Select the folder containing the 13 extracted files from your MCLA copy (default.xex + RPF/BIK archives).",
                13.5f, ready ? ACCENT_GREEN : 0xFFE2E8F0, ready);
        dataCard.addView(status);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progParams = new LinearLayout.LayoutParams(-1, -2);
        progParams.topMargin = dp(8);
        dataCard.addView(progress, progParams);

        pickButton = new Button(this);
        pickButton.setText(ready ? "Re-import Game Folder" : "Select Game Folder (13 Files)");
        pickButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, PICK_GAME_FOLDER);
        });
        addFullButton(dataCard, pickButton, ready ? 0 : 3);

        shaderStatus = label("", 12.5f, TEXT_MUTED, false);
        LinearLayout.LayoutParams ssp = new LinearLayout.LayoutParams(-1, -2);
        ssp.topMargin = dp(12);
        dataCard.addView(shaderStatus, ssp);
        updateShaderPackStatus();

        shaderImportButton = new Button(this);
        shaderImportButton.setText("Import Shader Pack (.shaderpack)");
        shaderImportButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, PICK_SHADER_PACK);
        });
        addFullButton(dataCard, shaderImportButton, 0);

        logStatusView = label(MclaLogExporter.statusSummary(this), 12.5f, ACCENT_CYAN, false);
        LinearLayout.LayoutParams lsp = new LinearLayout.LayoutParams(-1, -2);
        lsp.topMargin = dp(12);
        dataCard.addView(logStatusView, lsp);

        exportLogButton = new Button(this);
        exportLogButton.setText("Export Diagnostic Log Now");
        exportLogButton.setOnClickListener(v -> {
            exportLogButton.setEnabled(false);
            logStatusView.setText("Exporting Documents/MCLA/logs/MCLA-diagnostic.txt...");
            MclaLogExporter.exportNowAsync(this, true, () -> mainHandler.post(() -> {
                logStatusView.setText(MclaLogExporter.statusSummary(this));
                exportLogButton.setEnabled(true);
            }));
        });
        addFullButton(dataCard, exportLogButton, 0);

        // Bottom Launch Card
        playButton = new Button(this);
        playButton.setOnClickListener(v -> startGame());
        addFullButton(layout, playButton, 1);

        updatePlayButtons(ready);
        refreshHeroSummary();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG_MAIN);
        scroll.addView(layout);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateControlsStatus();
        if (logStatusView != null) {
            logStatusView.setText(MclaLogExporter.statusSummary(this));
        }
        MclaLogExporter.exportNowAsync(this, false, () -> mainHandler.post(() -> {
            if (logStatusView != null) {
                logStatusView.setText(MclaLogExporter.statusSummary(this));
            }
        }));
    }

    private void updatePlayButtons(boolean ready) {
        String text = ready ? "▶  LAUNCH MIDNIGHT CLUB: LA" : "IMPORT GAME FILES TO PLAY";
        if (topPlayButton != null) {
            topPlayButton.setText(text);
            topPlayButton.setEnabled(ready);
            topPlayButton.setAlpha(ready ? 1.0f : 0.5f);
        }
        if (playButton != null) {
            playButton.setText(text);
            playButton.setEnabled(ready);
            playButton.setAlpha(ready ? 1.0f : 0.5f);
        }
    }

    private void refreshHeroSummary() {
        if (heroSummaryChip == null) return;
        boolean custom = new File(driverFolder(), "selected.txt").isFile();
        String tu = getSharedPreferences("driver_debug", MODE_PRIVATE)
                .getString("tu_debug", "syncdraw");
        String drv = custom ? ("Turnip · " + ("none".equals(tu) ? "nosync" : tu)) : "System Vulkan";
        String perf = MclaRuntimeProfiles.perfPreset(this);
        String perfShort = MclaRuntimeProfiles.PERF_MAX_FPS.equals(perf) ? "Max FPS"
                : MclaRuntimeProfiles.PERF_CONSOLE.equals(perf) ? "Console" : "Balanced";
        String audio = MclaRuntimeProfiles.audioProfile(this);
        String audioShort = MclaRuntimeProfiles.AUDIO_SAFE.equals(audio) ? "Safe"
                : MclaRuntimeProfiles.AUDIO_LOW_LATENCY.equals(audio) ? "Low-Latency" : "Adaptive Stereo";
        heroSummaryChip.setText(drv + "   •   " + perfShort
                + (MclaRuntimeProfiles.emptyStreets(this) ? " (Empty Streets)" : "")
                + "   •   " + audioShort);
    }

    private void updatePerfToggles() {
        boolean empty = MclaRuntimeProfiles.emptyStreets(this);
        if (emptyStreetsButton != null) {
            emptyStreetsButton.setText(empty
                    ? "Empty Streets: ON (No Ambient Traffic / Peds)"
                    : "Empty Streets: OFF (Normal LA Traffic)");
            styleButton(emptyStreetsButton, empty ? 2 : 0);
        }
        boolean fill = MclaRuntimeProfiles.widescreenFill(this);
        if (aspectButton != null) {
            aspectButton.setText(fill
                    ? "Display: Full Screen · Auto Aspect (720p)"
                    : "Display: 16:9 Widescreen Letterbox (720p)");
            styleButton(aspectButton, fill ? 2 : 0);
        }
        boolean fps = MclaRuntimeProfiles.fpsOverlay(this);
        if (fpsOverlayButton != null) {
            fpsOverlayButton.setText(fps ? "FPS HUD: ON" : "FPS HUD: OFF");
            styleButton(fpsOverlayButton, fps ? 2 : 0);
        }
        boolean sustained = MclaRuntimeProfiles.sustainedPerformance(this);
        if (sustainedPerfButton != null) {
            sustainedPerfButton.setText(sustained ? "Thermal Guard: ON" : "Thermal Guard: OFF");
            styleButton(sustainedPerfButton, sustained ? 2 : 0);
        }
    }

    private void updateControlsStatus() {
        if (pedalsToggleButton == null) return;
        boolean pedals = MclaTouchControls.isPedalsEnabled(this);
        pedalsToggleButton.setText(pedals
                ? "Analog Pedals (%): ON (Slide Up = Stronger)"
                : "Analog Pedals (%): OFF (Classic LT / RT)");
        styleButton(pedalsToggleButton, pedals ? 2 : 0);
    }

    private File driverFolder() {
        return new File(getFilesDir(), "gpu-drivers");
    }

    private void updateDriverStatus() {
        if (driverStatus == null) return;
        boolean custom = new File(driverFolder(), "selected.txt").isFile();
        String suffix = "";
        if (custom) {
            String debug = getSharedPreferences("driver_debug", MODE_PRIVATE)
                    .getString("tu_debug", "syncdraw");
            if (new File(getFilesDir(), "driver.env").isFile()) {
                suffix = " · driver.env override";
            } else {
                suffix = ("none".equals(debug) || debug.isEmpty())
                        ? " · no TU_DEBUG switch" : " · TU_DEBUG=" + debug;
            }
        }
        driverStatus.setText(custom
                ? "Active Driver: Turnip Freedreno" + suffix
                : "Active Driver: Qualcomm / System Vulkan");
        if (systemButton != null) {
            systemButton.setEnabled(custom);
            systemButton.setAlpha(custom ? 1.0f : 0.5f);
        }
        if (turnipDebugSpinner != null) {
            turnipDebugSpinner.setEnabled(custom);
            turnipDebugSpinner.setAlpha(custom ? 1.0f : 0.55f);
        }
    }

    private File shaderPackFolder() {
        return new File(getFilesDir(), "shaderpack");
    }

    private File shaderPackFile() {
        return new File(shaderPackFolder(), SHADER_PACK_NAME);
    }

    private void updateShaderPackStatus() {
        if (shaderStatus == null) return;
        boolean installed = shaderPackFile().isFile();
        shaderStatus.setText(installed
                ? "Shader pack: 545407F8.shaderpack installed privately"
                : "Shader pack: optional (not imported)");
    }

    private boolean hasGameFiles() {
        File folder = new File(getFilesDir(), "game");
        for (String name : REQUIRED) {
            File file = new File(folder, name);
            if (!file.isFile() || file.length() == 0) return false;
        }
        return true;
    }

    private void startGame() {
        if (playPending || !hasGameFiles()) return;
        playPending = true;
        updatePlayButtons(false);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            openGame();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (playPending && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            openGame();
        }
    }

    private void openGame() {
        playPending = false;
        startActivity(new Intent(this, MclaActivity.class));
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK_DRIVER_ZIP) {
            Uri uri = data.getData();
            if (uri == null) return;
            driverButton.setEnabled(false);
            updatePlayButtons(false);
            driverStatus.setText("Importing Turnip driver...");
            new Thread(() -> importDriver(uri), "MCLA driver import").start();
            return;
        }
        if (requestCode == PICK_SHADER_PACK) {
            Uri uri = data.getData();
            if (uri == null) return;
            shaderImportButton.setEnabled(false);
            shaderStatus.setText("Importing shader pack...");
            new Thread(() -> importShaderPack(uri), "MCLA shader pack import").start();
            return;
        }
        if (requestCode != PICK_GAME_FOLDER) return;
        Uri tree = data.getData();
        if (tree == null) return;
        if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {
            Log.w(TAG, "The folder picker did not report a read grant; trying the URI anyway");
        }
        try {
            getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            Log.w(TAG, "Could not persist folder access; trying the current grant", e);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        pickButton.setEnabled(false);
        progress.setIndeterminate(false);
        progress.setMax(REQUIRED.length);
        progress.setProgress(0);
        progress.setVisibility(View.VISIBLE);
        status.setText("Checking game files...");
        new Thread(() -> importGame(tree), "MCLA game import").start();
    }

    private Map<String, Uri> children(Uri tree, String parentId) throws Exception {
        Uri list = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        Map<String, Uri> found = new HashMap<>();
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = getContentResolver().query(list, projection, null, null, null)) {
            if (cursor == null) throw new Exception("Could not read the selected folder.");
            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                String name = cursor.getString(1);
                String type = cursor.getString(2);
                if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                        || name.contains("/") || name.contains("\\")) continue;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(type)) continue;
                found.put(name, DocumentsContract.buildDocumentUriUsingTree(tree, id));
            }
        }
        return found;
    }

    private void importGame(Uri tree) {
        File folder = new File(getFilesDir(), "game");
        try {
            String rootId = DocumentsContract.getTreeDocumentId(tree);
            Map<String, Uri> files = children(tree, rootId);
            for (String name : REQUIRED) {
                if (!files.containsKey(name)) throw new Exception("Missing file: " + name);
            }
            if (!folder.isDirectory() && !folder.mkdirs())
                throw new Exception("Could not create the private game folder.");

            byte[] buffer = new byte[1024 * 1024];
            for (int index = 0; index < REQUIRED.length; index++) {
                String name = REQUIRED[index];
                final int fileNumber = index + 1;
                mainHandler.post(() -> status.setText("Copying " + name + " ("
                        + fileNumber + "/" + REQUIRED.length + ")..."));
                File target = new File(folder, name);
                File part = new File(folder, name + ".partial");
                try {
                    try (InputStream input = getContentResolver().openInputStream(files.get(name));
                         FileOutputStream output = new FileOutputStream(part)) {
                        if (input == null) throw new Exception("Could not open " + name);
                        int count;
                        long copied = 0;
                        long nextReport = 64L * 1024 * 1024;
                        while ((count = input.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                            copied += count;
                            if (copied >= nextReport) {
                                final long copiedMiB = copied / (1024 * 1024);
                                mainHandler.post(() -> status.setText("Copying " + name + " ("
                                        + fileNumber + "/" + REQUIRED.length + ", "
                                        + copiedMiB + " MiB)..."));
                                nextReport += 64L * 1024 * 1024;
                            }
                        }
                        output.getFD().sync();
                    }
                    if (!part.renameTo(target))
                        throw new Exception("Could not save " + name);
                } catch (Exception e) {
                    part.delete();
                    throw e;
                }
                mainHandler.post(() -> progress.setProgress(fileNumber));
            }
            if (!hasGameFiles()) throw new Exception("The copy finished with incomplete files.");
            mainHandler.post(() -> {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                progress.setVisibility(View.GONE);
                pickButton.setEnabled(true);
                pickButton.setText("Re-import Game Folder");
                styleButton(pickButton, 0);
                status.setTextColor(ACCENT_GREEN);
                status.setText("✓ Game files verified (13/13 files ready). Tap LAUNCH!");
                updatePlayButtons(true);
            });
        } catch (SecurityException e) {
            Log.w(TAG, "No access to the selected folder", e);
            showImportFailure("No permission to read that folder. Select it again "
                    + "and grant access in the Android picker.");
        } catch (Exception e) {
            showImportFailure("Import failed: " + e.getMessage());
        }
    }

    private void importDriver(Uri uri) {
        File folder = driverFolder();
        String driverName = "libvulkan_freedreno.so";
        File part = new File(folder, driverName + ".partial");
        File target = new File(folder, driverName);
        try {
            if (!folder.isDirectory() && !folder.mkdirs())
                throw new Exception("Could not create the driver folder.");
            boolean found = false;
            try (InputStream raw = getContentResolver().openInputStream(uri)) {
                if (raw == null) throw new Exception("Could not open the ZIP.");
                try (ZipInputStream zip = new ZipInputStream(raw)) {
                    ZipEntry entry;
                    int entriesSeen = 0;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (++entriesSeen > 512)
                            throw new Exception("The ZIP contains too many entries.");
                        String name = entry.getName();
                        if (entry.isDirectory() || name == null
                                || name.startsWith("/") || name.contains("..") || name.contains("\\")
                                || !(name.equals(driverName) || name.endsWith("/" + driverName))) continue;
                        if (found) throw new Exception("The ZIP contains more than one driver.");
                        found = true;
                        long copied = 0;
                        byte[] buffer = new byte[64 * 1024];
                        try (FileOutputStream output = new FileOutputStream(part)) {
                            int count;
                            while ((count = zip.read(buffer)) != -1) {
                                copied += count;
                                if (copied > MAX_DRIVER_BYTES)
                                    throw new Exception("Driver exceeds 128 MiB.");
                                output.write(buffer, 0, count);
                            }
                            output.getFD().sync();
                        }
                    }
                }
            }
            if (!found) throw new Exception("The ZIP does not contain " + driverName + ".");
            validateArm64Library(part);
            if (!part.renameTo(target)) throw new Exception("Could not save the driver.");
            target.setReadable(false, false);
            target.setWritable(false, false);
            target.setExecutable(false, false);
            target.setReadable(true, true);
            target.setWritable(true, true);
            target.setExecutable(true, true);
            File selection = new File(folder, "selected.txt");
            try (FileOutputStream output = new FileOutputStream(selection)) {
                output.write((driverName + "\n").getBytes("UTF-8"));
                output.getFD().sync();
            }
            mainHandler.post(() -> {
                updateDriverStatus();
                refreshHeroSummary();
            });
        } catch (Exception e) {
            Log.e(TAG, "Driver import failed", e);
            mainHandler.post(() -> driverStatus.setText("Driver import failed: " + e.getMessage()));
        } finally {
            part.delete();
            mainHandler.post(() -> {
                driverButton.setEnabled(true);
                updatePlayButtons(hasGameFiles());
            });
        }
    }

    private void importShaderPack(Uri uri) {
        File folder = shaderPackFolder();
        File part = new File(folder, SHADER_PACK_NAME + ".partial");
        File target = shaderPackFile();
        try {
            if (!folder.isDirectory() && !folder.mkdirs())
                throw new Exception("Could not create the shader pack folder.");
            byte[] header = new byte[16];
            int headerRead = 0;
            long copied = 0;
            try (InputStream input = getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(part)) {
                if (input == null) throw new Exception("Could not open the shader pack.");
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (headerRead < header.length) {
                        int take = Math.min(count, header.length - headerRead);
                        System.arraycopy(buffer, 0, header, headerRead, take);
                        headerRead += take;
                    }
                    copied += count;
                    if (copied > MAX_SHADER_PACK_BYTES)
                        throw new Exception("Shader pack exceeds 64 MiB.");
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (headerRead < header.length || header[0] != 'M' || header[1] != 'C' ||
                    header[2] != 'L' || header[3] != 'A' || header[4] != 'S' ||
                    header[5] != 'P' || header[6] != 'K' || header[7] != '1')
                throw new Exception("Not an MCLA shader pack.");
            int title = (header[12] & 0xff) | ((header[13] & 0xff) << 8)
                    | ((header[14] & 0xff) << 16) | ((header[15] & 0xff) << 24);
            if (title != 0x545407F8)
                throw new Exception("The shader pack is for another game.");
            if (!part.renameTo(target))
                throw new Exception("Could not save the shader pack.");
            mainHandler.post(this::updateShaderPackStatus);
        } catch (Exception e) {
            Log.e(TAG, "Shader pack import failed", e);
            mainHandler.post(() -> shaderStatus.setText("Shader pack import failed: " + e.getMessage()));
        } finally {
            part.delete();
            mainHandler.post(() -> shaderImportButton.setEnabled(true));
        }
    }

    private static void validateArm64Library(File file) throws Exception {
        byte[] header = new byte[20];
        try (FileInputStream input = new FileInputStream(file)) {
            if (input.read(header) != header.length || header[0] != 0x7f ||
                    header[1] != 'E' || header[2] != 'L' || header[3] != 'F' ||
                    header[4] != 2 || header[5] != 1 ||
                    (header[18] & 0xff) != 183 || header[19] != 0)
                throw new Exception("The driver is not a valid ARM64 ELF library.");
        }
    }

    private void showImportFailure(String message) {
        mainHandler.post(() -> {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            progress.setVisibility(View.GONE);
            pickButton.setEnabled(true);
            status.setTextColor(ACCENT_AMBER);
            status.setText(message);
        });
    }
}
