package com.iojh2021.persiandashboard;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

public class MainActivity extends Activity {
    private WebView web;
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback netCb;
    private ValueCallback<Uri[]> uploadCallback;
    private static final int FILE_PICKER = 1001;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = cb;
                try {
                    Intent i = p.createIntent();
                    startActivityForResult(i, FILE_PICKER);
                } catch (Exception e) {
                    uploadCallback = null;
                    Toast.makeText(MainActivity.this, "انتخاب فایل ممکن نیست", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new RatesBridge(), "NativeRates");
        web.loadUrl("file:///android_asset/index.html");
        try {
            cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            netCb = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) {
                    web.post(new Runnable() { public void run() {
                        web.setNetworkAvailable(true);
                        web.evaluateJavascript("window.dispatchEvent(new Event('online'))", null);
                    }});
                }
            };
            cm.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), netCb);
        } catch (Exception e) {}
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_PICKER) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    result = new Uri[n];
                    for (int i=0;i<n;i++) result[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) result = new Uri[]{data.getData()};
            }
            if (uploadCallback != null) uploadCallback.onReceiveValue(result);
            uploadCallback = null;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private class RatesBridge {
        @android.webkit.JavascriptInterface
        public void fetchRates() {
            new Thread(new Runnable() { public void run() {
                String result = null;
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://call5.tgju.org/ajax.json").openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(15000);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) PersianDashboard");
                    c.setRequestProperty("Accept", "application/json");
                    if (c.getResponseCode() == 200) {
                        java.io.InputStream in = c.getInputStream();
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                        in.close();
                        org.json.JSONObject cur = new org.json.JSONObject(bo.toString("UTF-8")).getJSONObject("current");
                        org.json.JSONObject out = new org.json.JSONObject();
                        String[] keys = {"price_dollar_rl","price_eur","price_gbp","price_aed","price_try","price_cad","price_chf","sekeb","nim","rob"};
                        for (String k : keys) if (cur.has(k)) out.put(k, cur.getJSONObject(k));
                        result = out.toString();
                    }
                    c.disconnect();
                } catch (Exception e) { result = null; }
                final String js = (result == null)
                    ? "window.onRatesError&&window.onRatesError()"
                    : "window.onRates&&window.onRates(" + result + ")";
                web.post(new Runnable() { public void run() { web.evaluateJavascript(js, null); } });
            }}).start();
        }
    }

    @Override protected void onDestroy() {
        try { if (cm != null && netCb != null) cm.unregisterNetworkCallback(netCb); } catch (Exception e) {}
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
