package pl.rentmor.protokol;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 701;
    private static final String APP_URL = "https://rentmor.pl/protocol.php";
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getColor(R.color.navy));
        getWindow().setNavigationBarColor(getColor(R.color.navy));
        webView = new WebView(this);
        webView.setBackgroundColor(getColor(R.color.page));
        setContentView(webView);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setLoadsImagesAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return routeUrl(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return routeUrl(Uri.parse(url));
            }
            private boolean routeUrl(Uri uri) {
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
                if ("https".equals(scheme) && (host.equals("rentmor.pl") || host.equals("www.rentmor.pl"))) return false;
                if ("http".equals(scheme) && (host.equals("rentmor.pl") || host.equals("www.rentmor.pl"))) {
                    webView.loadUrl("https://rentmor.pl" + (uri.getPath() == null ? "/" : uri.getPath()) + (uri.getQuery() == null ? "" : "?" + uri.getQuery()));
                    return true;
                }
                if ("tel".equals(scheme) || "mailto".equals(scheme) || "geo".equals(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException ignored) { }
                    return true;
                }
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException ignored) { }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException ex) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Brak aplikacji do wyboru zdjęć lub plików.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null && !cookie.isEmpty()) request.addRequestHeader("Cookie", cookie);
                if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
                request.addRequestHeader("Referer", APP_URL);
                request.setMimeType(mimeType == null ? "application/pdf" : mimeType);
                request.setTitle("Protokół RentMor");
                request.setDescription("Pobieranie dokumentu PDF");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setAllowedOverMetered(true);
                request.setAllowedOverRoaming(false);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "RentMor_protokol.pdf");
                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager != null) {
                    manager.enqueue(request);
                    Toast.makeText(MainActivity.this, "Pobieranie PDF rozpoczęte. Plik znajdziesz w folderze Pobrane.", Toast.LENGTH_LONG).show();
                }
            } catch (Exception ex) {
                Toast.makeText(MainActivity.this, "Nie udało się rozpocząć pobierania PDF.", Toast.LENGTH_LONG).show();
            }
        });

        if (savedInstanceState == null) webView.loadUrl(APP_URL);
        else webView.restoreState(savedInstanceState);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}