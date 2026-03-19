package com.github.downloader;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Android 4.4 以下（API <= 20）向け TLSSocketFactory。
 *
 * Conscrypt が Security プロバイダとして登録されていれば
 * SSLContext.getInstance("TLS") は自動的に Conscrypt の実装を使う。
 * これにより API 9（Android 2.3）以上で TLS 1.2 通信が可能。
 *
 * 加えて、古い端末のルート証明書ストアが不足している問題に対応するため
 * 全証明書を信頼する TrustManager を使用する。
 *
 * 参考: https://qiita.com/ntsk/items/9f31fc7b44c04ea45e0b
 */
public class TLSSocketFactory extends SSLSocketFactory {

    private final SSLSocketFactory delegate;

    /** 全証明書を信頼する TrustManager（古い端末の証明書ストア不足対策） */
    private static final TrustManager[] TRUST_ALL = new TrustManager[]{
        new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {}
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {}
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }
    };

    public TLSSocketFactory() throws KeyManagementException, NoSuchAlgorithmException {
        // Conscrypt が登録済みなら自動的に Conscrypt の TLS 実装が使われる
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, TRUST_ALL, new SecureRandom());
        delegate = ctx.getSocketFactory();
    }

    @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
    @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }

    @Override
    public Socket createSocket() throws IOException {
        return tls(delegate.createSocket());
    }
    @Override
    public Socket createSocket(Socket s, String host, int port, boolean auto) throws IOException {
        return tls(delegate.createSocket(s, host, port, auto));
    }
    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return tls(delegate.createSocket(host, port));
    }
    @Override
    public Socket createSocket(String host, int port, InetAddress local, int localPort)
            throws IOException, UnknownHostException {
        return tls(delegate.createSocket(host, port, local, localPort));
    }
    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return tls(delegate.createSocket(host, port));
    }
    @Override
    public Socket createSocket(InetAddress addr, int port, InetAddress local, int localPort)
            throws IOException {
        return tls(delegate.createSocket(addr, port, local, localPort));
    }

    private Socket tls(Socket socket) {
        if (socket instanceof SSLSocket) {
            SSLSocket ssl = (SSLSocket) socket;
            // TLS 1.2 を明示的に有効化（Conscrypt 経由なら 1.3 も使える）
            ssl.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.1", "TLSv1"});
        }
        return socket;
    }
}
