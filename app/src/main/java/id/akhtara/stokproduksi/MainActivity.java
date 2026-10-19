package id.akhtara.stokproduksi;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Stock dan Produksi by Akhtara.ID - pembungkus WebView standalone.
 *
 * - Halaman dimuat dari assets (tanpa hosting, tanpa browser).
 * - Statusbar & navigation bar DIGAMBAR sendiri oleh aplikasi (bukan oleh sistem),
 *   jadi warnanya selalu mengikuti tema di semua versi Android, termasuk Android 15+.
 * - Tekan lama tidak memunculkan menu salin/tempel.
 */
public class MainActivity extends Activity {

    private static final int REQ_IMPORT = 1001;
    private static final int REQ_EXPORT = 1002;
    private static final int DEFAULT_BAR = 0xFF161D11; // header tema Sequoia

    private final Object lock = new Object();
    private long lastBak = 0;

    private SharedPreferences prefs;
    private LinearLayout root;
    private View statusBg, navBg;
    private WebView web;
    private String pendingExport;

    private int curStatus, curNav;
    private boolean curLs, curLn, barsApplied = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("ui", MODE_PRIVATE);
        curStatus = prefs.getInt("status", DEFAULT_BAR);
        curNav = curStatus;
        curLs = prefs.getBoolean("ls", false);
        curLn = curLs;

        Window w = getWindow();
        requestHighRefreshRate();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            w.getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            View d = w.getDecorView();
            d.setSystemUiVisibility(d.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        // susunan: [blok warna statusbar] [WebView] [blok warna navigation bar]
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        statusBg = new View(this);
        navBg = new View(this);
        web = new WebView(this);
        root.addView(statusBg, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0));
        root.addView(web, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(navBg, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0));
        setContentView(root);

        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets sb = ins.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                Insets ime = ins.getInsets(WindowInsets.Type.ime());
                top = sb.top;
                bottom = Math.max(sb.bottom, ime.bottom);
            } else {
                top = ins.getSystemWindowInsetTop();
                bottom = ins.getSystemWindowInsetBottom();
            }
            setH(statusBg, top);
            setH(navBg, bottom);
            if (Build.VERSION.SDK_INT >= 30) return WindowInsets.CONSUMED;
            return ins.consumeSystemWindowInsets();
        });

        applyBars(curStatus, curLs, curNav, curLn);
        setupWebView();
        root.requestApplyInsets();
        web.loadUrl("file:///android_asset/index.html");
    }

    /** Minta refresh rate tertinggi layar (90/120 Hz) agar animasi dan scroll semulus mungkin. */
    @SuppressWarnings("deprecation")
    private void requestHighRefreshRate() {
        try {
            if (Build.VERSION.SDK_INT < 23) return;
            Display d = getWindowManager().getDefaultDisplay();
            if (d == null) return;
            Display.Mode cur = d.getMode();
            Display.Mode best = cur;
            for (Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() == cur.getPhysicalWidth()
                        && m.getPhysicalHeight() == cur.getPhysicalHeight()
                        && m.getRefreshRate() > best.getRefreshRate()) {
                    best = m;
                }
            }
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.preferredDisplayModeId = best.getModeId();
            getWindow().setAttributes(lp);
        } catch (Throwable ignored) {
        }
    }

    private void setupWebView() {
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);

        // tekan lama: tidak ada pop up salin / tempel / pilih teks
        web.setLongClickable(false);
        web.setHapticFeedbackEnabled(false);
        web.setOnLongClickListener(v -> true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return true; // tetap di dalam aplikasi
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.requestFocus();
    }

    private static void setH(View v, int h) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        if (lp.height != h) {
            lp.height = h;
            v.setLayoutParams(lp);
        }
    }

    /** Warna statusbar & navigation bar dipaksa mengikuti tema (dipanggil dari JavaScript). */
    private void applyBars(int status, boolean lightStatus, int nav, boolean lightNav) {
        if (barsApplied && status == curStatus && nav == curNav
                && lightStatus == curLs && lightNav == curLn) return;
        barsApplied = true;
        curStatus = status;
        curNav = nav;
        curLs = lightStatus;
        curLn = lightNav;

        statusBg.setBackgroundColor(status);
        navBg.setBackgroundColor(nav);
        root.setBackgroundColor(nav);
        web.setBackgroundColor(nav);

        // lightXxx = latar terang -> ikon sistem dibuat gelap
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                int val = (lightStatus ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0)
                        | (lightNav ? WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0);
                c.setSystemBarsAppearance(val, mask);
            }
        } else {
            View d = getWindow().getDecorView();
            int f = d.getSystemUiVisibility();
            if (Build.VERSION.SDK_INT >= 23) {
                f = lightStatus ? (f | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
                        : (f & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
            if (Build.VERSION.SDK_INT >= 26) {
                f = lightNav ? (f | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
                        : (f & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            }
            d.setSystemUiVisibility(f);
        }
        prefs.edit().putInt("status", status).putBoolean("ls", lightStatus).apply();
    }

    private static int parse(String c, int def) {
        try {
            return Color.parseColor(c);
        } catch (Exception e) {
            return def;
        }
    }

    private static String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    /** Jembatan JavaScript <-> Android (window.Android) */
    private class Bridge {

        @JavascriptInterface
        public void setBars(final String status, final boolean lightStatus,
                            final String nav, final boolean lightNav) {
            final int s = parse(status, curStatus);
            final int n = parse(nav, curNav);
            runOnUiThread(() -> applyBars(s, lightStatus, n, lightNav));
        }

        @JavascriptInterface
        public String loadData() {
            synchronized (lock) {
                try {
                    File f = new File(getFilesDir(), "data.json");
                    if (!f.exists() || f.length() == 0) return null;
                    try (FileInputStream in = new FileInputStream(f)) {
                        return readAll(in);
                    }
                } catch (Exception e) {
                    return null;
                }
            }
        }

        @JavascriptInterface
        public void saveData(String json) {
            if (json == null || json.length() < 2) return;
            synchronized (lock) {
                try {
                    File dir = getFilesDir();
                    File cur = new File(dir, "data.json");
                    File tmp = new File(dir, "data.json.tmp");
                    try (FileOutputStream out = new FileOutputStream(tmp)) {
                        out.write(json.getBytes(StandardCharsets.UTF_8));
                        out.getFD().sync();
                    }
                    long now = System.currentTimeMillis();
                    if (cur.exists() && now - lastBak > 10 * 60 * 1000L) {
                        File bak = new File(dir, "data.bak");
                        if (bak.exists()) bak.delete();
                        cur.renameTo(bak);
                        lastBak = now;
                    }
                    if (!tmp.renameTo(cur)) {
                        cur.delete();
                        tmp.renameTo(cur);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        @JavascriptInterface
        public void exportFile(String name, String json) {
            pendingExport = json;
            final String nm = (name == null || name.isEmpty()) ? "backup.json" : name;
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, nm);
                startActivityForResult(i, REQ_EXPORT);
            });
        }

        @JavascriptInterface
        public void importFile() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_IMPORT);
            });
        }

        @JavascriptInterface
        public void share(final String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, "Kirim backup"));
            });
        }

        @JavascriptInterface
        public void vibrate(String json) {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v == null || !v.hasVibrator()) return;
                String t = json == null ? "" : json.trim();
                if (t.startsWith("[")) {
                    JSONArray a = new JSONArray(t);
                    long[] pat = new long[a.length() + 1];
                    pat[0] = 0;
                    for (int k = 0; k < a.length(); k++) pat[k + 1] = a.getLong(k);
                    if (Build.VERSION.SDK_INT >= 26) {
                        v.vibrate(VibrationEffect.createWaveform(pat, -1));
                    } else {
                        v.vibrate(pat, -1);
                    }
                } else {
                    long ms = Math.max(1L, (long) Double.parseDouble(t));
                    if (Build.VERSION.SDK_INT >= 26) {
                        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(ms);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            if (req == REQ_EXPORT && pendingExport != null) {
                try (OutputStream os = getContentResolver().openOutputStream(u, "wt")) {
                    os.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                }
                pendingExport = null;
                toast("Backup disimpan");
            } else if (req == REQ_IMPORT) {
                String txt;
                try (InputStream is = getContentResolver().openInputStream(u)) {
                    txt = readAll(is);
                }
                web.evaluateJavascript("window.onImport&&window.onImport(" + JSONObject.quote(txt) + ")", null);
            }
        } catch (Exception e) {
            toast("Gagal: " + e.getMessage());
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript(
                "(function(){try{return window.__back?window.__back():0}catch(e){return 0}})()",
                v -> {
                    if (!"1".equals(v)) {
                        web.evaluateJavascript("try{flushSave()}catch(e){}", null);
                        finish();
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) {
            web.evaluateJavascript("try{flushSave()}catch(e){}", null);
            web.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            root.removeView(web);
            web.destroy();
        }
        super.onDestroy();
    }
}
