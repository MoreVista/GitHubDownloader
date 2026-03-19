package com.github.downloader;

import android.os.Build;
import android.util.Log;

import androidx.multidex.MultiDexApplication;

import org.conscrypt.Conscrypt;

import java.security.Security;

/**
 * アプリケーション起動時に Conscrypt を SSL プロバイダとして登録する。
 *
 * Conscrypt は Google が BoringSSL（OpenSSL派生）をベースに開発した
 * Android 用セキュリティプロバイダ。
 * insertProviderAt(provider, 1) でシステムデフォルトより優先させることで
 * API 9（Android 2.3）以上で TLS 1.2 通信が可能になる。
 *
 * MultiDexApplication を継承することで API 21 未満の multidex にも対応。
 */
public class App extends MultiDexApplication {

    private static final String TAG = "App";

    @Override
    public void onCreate() {
        super.onCreate();
        installConscrypt();
    }

    private void installConscrypt() {
        try {
            // Conscrypt を最優先のセキュリティプロバイダとして挿入
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
            Log.i(TAG, "Conscrypt installed (API " + Build.VERSION.SDK_INT + ")");
        } catch (Exception e) {
            // インストール失敗してもアプリは動作させる（古いデバイスでの保険）
            Log.e(TAG, "Conscrypt install failed", e);
        }
    }
}
