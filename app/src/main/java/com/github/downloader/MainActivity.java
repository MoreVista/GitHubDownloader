package com.github.downloader;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText etRepoUrl;
    private Button btnFetchReleases;
    private Button btnGetLatest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etRepoUrl = findViewById(R.id.etRepoUrl);
        btnFetchReleases = findViewById(R.id.btnFetchReleases);
        btnGetLatest = findViewById(R.id.btnGetLatest);

        btnFetchReleases.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openReleases(false);
            }
        });

        btnGetLatest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openReleases(true);
            }
        });

        // ブラウザ等から渡されたURLを処理（起動時）
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // launchMode="singleTask" のとき、既に起動中のActivityに
        // 新しいIntentが届いた場合もここで処理する
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    /**
     * ブラウザや共有メニューから渡された github.com の URL を処理する。
     * https://github.com/owner/repo  → URLバーに自動入力してリリース画面へ遷移
     * https://github.com/owner/repo/releases など深いパスも owner/repo を抽出して対応
     */
    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri data = intent.getData();

        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            String url = data.toString();
            String[] parts = parseRepoUrl(url);
            if (parts != null) {
                // URLバーに表示
                String repoUrl = "https://github.com/" + parts[0] + "/" + parts[1];
                etRepoUrl.setText(repoUrl);
                // そのまま最新リリース画面へ自動遷移
                launchReleaseList(parts[0], parts[1], true);
            } else {
                // owner/repo の形式でない場合（例: github.com トップ等）はURLだけ入力
                etRepoUrl.setText(url);
                Toast.makeText(this,
                        "リポジトリのURLを指定してください\n例: github.com/owner/repo",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openReleases(boolean latestOnly) {
        String url = etRepoUrl.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, "リポジトリURLを入力してください", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] parts = parseRepoUrl(url);
        if (parts == null) {
            Toast.makeText(this,
                    "無効なGitHub URLです\n例: https://github.com/owner/repo",
                    Toast.LENGTH_LONG).show();
            return;
        }
        launchReleaseList(parts[0], parts[1], latestOnly);
    }

    private void launchReleaseList(String owner, String repo, boolean latestOnly) {
        Intent intent = new Intent(this, ReleaseListActivity.class);
        intent.putExtra("owner", owner);
        intent.putExtra("repo", repo);
        intent.putExtra("latest_only", latestOnly);
        startActivity(intent);
    }

    /**
     * GitHub URL から owner と repo を抽出する。
     * 対応形式:
     *   https://github.com/owner/repo
     *   https://github.com/owner/repo/releases
     *   https://github.com/owner/repo/releases/tag/v1.0
     *   github.com/owner/repo
     *   owner/repo
     */
    private String[] parseRepoUrl(String url) {
        if (url == null) return null;
        url = url.trim();
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);

        // プロトコル除去
        url = url.replace("https://", "").replace("http://", "");
        // github.com/ プレフィックス除去
        if (url.startsWith("github.com/")) {
            url = url.substring("github.com/".length());
        }

        String[] parts = url.split("/");
        if (parts.length >= 2
                && !TextUtils.isEmpty(parts[0])
                && !TextUtils.isEmpty(parts[1])) {
            return new String[]{parts[0], parts[1]};
        }
        return null;
    }
}
