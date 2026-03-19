package com.github.downloader;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

public class GitHubApiClient {

    private static final String TAG = "GitHubApiClient";
    private static final String API_BASE = "https://api.github.com";
    private static final int TIMEOUT = 15000;
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Android 4.1-4.4 用: 全証明書を信頼するSSLSocketFactory
    private static TLSSocketFactory tlsSocketFactory = null;
    // Android 4.1-4.4 用: ホスト名検証をスキップ
    private static final HostnameVerifier ALLOW_ALL = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) { return true; }
    };

    static {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                tlsSocketFactory = new TLSSocketFactory();
            } catch (KeyManagementException | NoSuchAlgorithmException e) {
                Log.w(TAG, "TLSSocketFactory init failed", e);
            }
        }
    }

    public interface ReleaseCallback {
        void onSuccess(List<GitHubRelease> releases);
        void onError(String message);
    }

    public void fetchReleases(final String owner, final String repo,
                              final boolean latestOnly, final ReleaseCallback callback) {
        final String endpoint = latestOnly
                ? API_BASE + "/repos/" + owner + "/" + repo + "/releases/latest"
                : API_BASE + "/repos/" + owner + "/" + repo + "/releases?per_page=30";

        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL u = new URL(endpoint);
                    conn = (HttpURLConnection) u.openConnection();

                    // Android 4.1-4.4: 全証明書信頼 + ホスト名検証スキップ
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                            && conn instanceof HttpsURLConnection) {
                        HttpsURLConnection https = (HttpsURLConnection) conn;
                        if (tlsSocketFactory != null) {
                            https.setSSLSocketFactory(tlsSocketFactory);
                        }
                        https.setHostnameVerifier(ALLOW_ALL);
                    }

                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    conn.setRequestProperty("User-Agent", "GitHubDownloader-Android/1.0");
                    conn.setConnectTimeout(TIMEOUT);
                    conn.setReadTimeout(TIMEOUT);

                    final int code = conn.getResponseCode();
                    if (code == 404) {
                        postError(callback, "リポジトリまたはリリースが見つかりません (404)");
                        return;
                    }
                    if (code == 403) {
                        postError(callback, "APIレート制限に達しました。しばらく待ってから試してください (403)");
                        return;
                    }
                    if (code != 200) {
                        postError(callback, "APIエラー: HTTP " + code);
                        return;
                    }

                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    final String json = sb.toString();
                    final List<GitHubRelease> releases = new ArrayList<>();
                    if (latestOnly) {
                        releases.add(parseRelease(new JSONObject(json)));
                    } else {
                        JSONArray arr = new JSONArray(json);
                        for (int i = 0; i < arr.length(); i++) {
                            releases.add(parseRelease(arr.getJSONObject(i)));
                        }
                    }

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() { callback.onSuccess(releases); }
                    });

                } catch (final IOException e) {
                    Log.e(TAG, "Network error", e);
                    postError(callback, "ネットワークエラー: " + e.getMessage());
                } catch (final JSONException e) {
                    Log.e(TAG, "JSON parse error", e);
                    postError(callback, "レスポンスの解析に失敗しました: " + e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        });
    }

    private void postError(final ReleaseCallback callback, final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() { callback.onError(msg); }
        });
    }

    private GitHubRelease parseRelease(JSONObject obj) throws JSONException {
        GitHubRelease rel = new GitHubRelease();
        rel.tagName = obj.optString("tag_name", "");
        rel.name = obj.optString("name", rel.tagName);
        rel.body = obj.optString("body", "");
        rel.publishedAt = obj.optString("published_at", "");
        rel.prerelease = obj.optBoolean("prerelease", false);
        rel.assets = new ArrayList<>();
        JSONArray arr = obj.optJSONArray("assets");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject a = arr.getJSONObject(i);
                GitHubRelease.Asset asset = new GitHubRelease.Asset();
                asset.name = a.optString("name", "");
                asset.browserDownloadUrl = a.optString("browser_download_url", "");
                asset.size = a.optLong("size", 0);
                asset.contentType = a.optString("content_type", "");
                rel.assets.add(asset);
            }
        }
        return rel;
    }
}
