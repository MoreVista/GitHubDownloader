package com.github.downloader;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadService extends Service {

    private static final String TAG = "DownloadService";

    // Android 4.1-4.4 用: 全証明書信頼 + ホスト名検証スキップ
    private static TLSSocketFactory tlsSocketFactory = null;
    private static final HostnameVerifier ALLOW_ALL = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) { return true; }
    };
    static {
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.KITKAT) {
            try {
                tlsSocketFactory = new TLSSocketFactory();
            } catch (Exception e) {
                android.util.Log.w("DownloadService", "TLSSocketFactory init failed", e);
            }
        }
    }

    public static final String EXTRA_URL = "download_url";
    public static final String EXTRA_FILENAME = "filename";
    public static final String ACTION_DOWNLOAD_COMPLETE = "com.github.downloader.DOWNLOAD_COMPLETE";
    public static final String ACTION_DOWNLOAD_FAILED = "com.github.downloader.DOWNLOAD_FAILED";

    private static final int NOTIF_ID = 1001;
    private NotificationManager notifManager;
    private Handler mainHandler;
    private ExecutorService executor;

    @Override
    public void onCreate() {
        super.onCreate();
        notifManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        final String url = intent.getStringExtra(EXTRA_URL);
        final String filename = intent.getStringExtra(EXTRA_FILENAME);
        if (url != null && filename != null) {
            showProgressNotification(filename, 0);
            executor.execute(new DownloadRunnable(url, filename));
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void showProgressNotification(String filename, int progress) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "download")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("ダウンロード中")
                .setContentText(filename)
                .setProgress(100, progress, progress == 0)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        notifManager.notify(NOTIF_ID, builder.build());
    }

    private void showCompleteNotification(String filename, File file) {
        Intent installIntent = getInstallIntent(file);
        int flags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(this, 0, installIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "download")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("ダウンロード完了")
                .setContentText(filename + " - タップして開く")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        notifManager.cancel(NOTIF_ID);
        notifManager.notify(NOTIF_ID + 1, builder.build());
    }

    private void showErrorNotification(String filename, String error) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "download")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("ダウンロード失敗")
                .setContentText(filename + ": " + error)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        notifManager.cancel(NOTIF_ID);
        notifManager.notify(NOTIF_ID + 2, builder.build());
    }

    private Intent getInstallIntent(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri;
        if (Build.VERSION.SDK_INT >= 24) {
            uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(file);
        }
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private class DownloadRunnable implements Runnable {
        private final String downloadUrl;
        private final String filename;

        DownloadRunnable(String downloadUrl, String filename) {
            this.downloadUrl = downloadUrl;
            this.filename = filename;
        }

        @Override
        public void run() {
            HttpURLConnection conn = null;
            InputStream is = null;
            FileOutputStream fos = null;
            try {
                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS), "GitHubDownloader");
                if (!dir.exists()) dir.mkdirs();

                final File outFile = new File(dir, filename);

                URL u = new URL(downloadUrl);
                conn = (HttpURLConnection) u.openConnection();
                // Android 4.1-4.4: 全証明書信頼 + ホスト名検証スキップ + TLS1.1/1.2有効化
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT
                        && conn instanceof HttpsURLConnection) {
                    HttpsURLConnection https = (HttpsURLConnection) conn;
                    if (tlsSocketFactory != null) {
                        https.setSSLSocketFactory(tlsSocketFactory);
                    }
                    https.setHostnameVerifier(ALLOW_ALL);
                }
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "GitHubDownloader-Android/1.0");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);

                int code = conn.getResponseCode();
                if (code != 200) {
                    final String err = "HTTP " + code;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showErrorNotification(filename, err);
                            broadcastFailed(err);
                            stopSelf();
                        }
                    });
                    return;
                }

                final long total = conn.getContentLength();
                is = conn.getInputStream();
                fos = new FileOutputStream(outFile);

                byte[] buf = new byte[8192];
                long downloaded = 0;
                int len;
                int lastProgress = -1;
                while ((len = is.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                    downloaded += len;
                    if (total > 0) {
                        final int progress = (int) (downloaded * 100 / total);
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            final int p = progress;
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    showProgressNotification(filename, p);
                                }
                            });
                        }
                    }
                }
                fos.flush();

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showCompleteNotification(filename, outFile);
                        Intent intent = new Intent(ACTION_DOWNLOAD_COMPLETE);
                        intent.putExtra("file_path", outFile.getAbsolutePath());
                        sendBroadcast(intent);
                        stopSelf();
                    }
                });

            } catch (final IOException e) {
                Log.e(TAG, "Download error", e);
                final String err = e.getMessage() != null ? e.getMessage() : "IO error";
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showErrorNotification(filename, err);
                        broadcastFailed(err);
                        stopSelf();
                    }
                });
            } finally {
                try {
                    if (is != null) is.close();
                    if (fos != null) fos.close();
                } catch (IOException ignored) {}
                if (conn != null) conn.disconnect();
            }
        }
    }

    private void broadcastFailed(String error) {
        Intent intent = new Intent(ACTION_DOWNLOAD_FAILED);
        intent.putExtra("error", error);
        sendBroadcast(intent);
    }
}
