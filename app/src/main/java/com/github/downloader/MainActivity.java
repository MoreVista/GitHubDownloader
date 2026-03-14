package com.github.downloader;

import android.content.Intent;
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

        // Example repos for quick test
        etRepoUrl.setHint("例: https://github.com/owner/repo");

        btnFetchReleases.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = etRepoUrl.getText().toString().trim();
                if (TextUtils.isEmpty(url)) {
                    Toast.makeText(MainActivity.this, "リポジトリURLを入力してください", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] parts = parseRepoUrl(url);
                if (parts == null) {
                    Toast.makeText(MainActivity.this, "無効なGitHub URLです\n例: https://github.com/owner/repo", Toast.LENGTH_LONG).show();
                    return;
                }
                Intent intent = new Intent(MainActivity.this, ReleaseListActivity.class);
                intent.putExtra("owner", parts[0]);
                intent.putExtra("repo", parts[1]);
                intent.putExtra("latest_only", false);
                startActivity(intent);
            }
        });

        btnGetLatest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = etRepoUrl.getText().toString().trim();
                if (TextUtils.isEmpty(url)) {
                    Toast.makeText(MainActivity.this, "リポジトリURLを入力してください", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] parts = parseRepoUrl(url);
                if (parts == null) {
                    Toast.makeText(MainActivity.this, "無効なGitHub URLです\n例: https://github.com/owner/repo", Toast.LENGTH_LONG).show();
                    return;
                }
                Intent intent = new Intent(MainActivity.this, ReleaseListActivity.class);
                intent.putExtra("owner", parts[0]);
                intent.putExtra("repo", parts[1]);
                intent.putExtra("latest_only", true);
                startActivity(intent);
            }
        });
    }

    /**
     * Parse GitHub URL to extract owner and repo name.
     * Supports:
     *   https://github.com/owner/repo
     *   github.com/owner/repo
     *   owner/repo
     */
    private String[] parseRepoUrl(String url) {
        // Remove trailing slash
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        // Strip protocol
        url = url.replace("https://", "").replace("http://", "");
        // Strip github.com prefix
        if (url.startsWith("github.com/")) {
            url = url.substring("github.com/".length());
        }
        String[] parts = url.split("/");
        if (parts.length >= 2 && !TextUtils.isEmpty(parts[0]) && !TextUtils.isEmpty(parts[1])) {
            return new String[]{parts[0], parts[1]};
        }
        return null;
    }
}
