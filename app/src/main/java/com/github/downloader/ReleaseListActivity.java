package com.github.downloader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ReleaseListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private ReleaseAdapter adapter;
    private List<GitHubRelease> releases = new ArrayList<>();

    private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DownloadService.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                Toast.makeText(ReleaseListActivity.this,
                        "ダウンロード完了！通知をタップしてインストール", Toast.LENGTH_LONG).show();
            } else if (DownloadService.ACTION_DOWNLOAD_FAILED.equals(intent.getAction())) {
                String error = intent.getStringExtra("error");
                Toast.makeText(ReleaseListActivity.this,
                        "ダウンロード失敗: " + error, Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_release_list);

        String owner = getIntent().getStringExtra("owner");
        String repo = getIntent().getStringExtra("repo");
        boolean latestOnly = getIntent().getBooleanExtra("latest_only", false);

        setTitle(owner + "/" + repo);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);

        adapter = new ReleaseAdapter(releases);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        fetchReleases(owner, repo, latestOnly);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(DownloadService.ACTION_DOWNLOAD_COMPLETE);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_FAILED);
        registerReceiver(downloadReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(downloadReceiver);
    }

    private void fetchReleases(String owner, String repo, boolean latestOnly) {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);

        new GitHubApiClient().fetchReleases(owner, repo, latestOnly, new GitHubApiClient.ReleaseCallback() {
            @Override
            public void onSuccess(List<GitHubRelease> result) {
                progressBar.setVisibility(View.GONE);
                if (result.isEmpty()) {
                    tvEmpty.setText("リリースが見つかりませんでした");
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    releases.clear();
                    releases.addAll(result);
                    adapter.notifyDataSetChanged();
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String message) {
                progressBar.setVisibility(View.GONE);
                tvEmpty.setText("エラー: " + message);
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void startDownload(String url, String filename) {
        Intent intent = new Intent(this, DownloadService.class);
        intent.putExtra(DownloadService.EXTRA_URL, url);
        intent.putExtra(DownloadService.EXTRA_FILENAME, filename);
        startService(intent);
        Toast.makeText(this, "ダウンロード開始: " + filename, Toast.LENGTH_SHORT).show();
    }

    // ---- Adapter ----
    private class ReleaseAdapter extends RecyclerView.Adapter<ReleaseAdapter.ReleaseViewHolder> {
        private final List<GitHubRelease> data;

        ReleaseAdapter(List<GitHubRelease> data) {
            this.data = data;
        }

        @Override
        public ReleaseViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_release, parent, false);
            return new ReleaseViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ReleaseViewHolder holder, int position) {
            GitHubRelease release = data.get(position);
            holder.tvTagName.setText(release.tagName);
            holder.tvName.setText(release.name);

            String date = release.publishedAt.replace("T", " ").replace("Z", "");
            holder.tvDate.setText("公開日: " + date);

            if (release.prerelease) {
                holder.tvPrerelease.setVisibility(View.VISIBLE);
            } else {
                holder.tvPrerelease.setVisibility(View.GONE);
            }

            // Show release notes (truncated)
            if (release.body != null && !release.body.isEmpty()) {
                String body = release.body.length() > 200
                        ? release.body.substring(0, 200) + "..."
                        : release.body;
                holder.tvBody.setText(body);
                holder.tvBody.setVisibility(View.VISIBLE);
            } else {
                holder.tvBody.setVisibility(View.GONE);
            }

            // Clear and re-add asset buttons
            holder.assetsContainer.removeAllViews();
            if (release.assets == null || release.assets.isEmpty()) {
                TextView noAssets = new TextView(holder.itemView.getContext());
                noAssets.setText("アセットなし");
                noAssets.setTextColor(0xFF888888);
                holder.assetsContainer.addView(noAssets);
            } else {
                for (final GitHubRelease.Asset asset : release.assets) {
                    Button btn = new Button(holder.itemView.getContext());
                    btn.setText("⬇ " + asset.name + "  (" + asset.getFormattedSize() + ")");
                    btn.setTextSize(12f);
                    btn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startDownload(asset.browserDownloadUrl, asset.name);
                        }
                    });
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    params.setMargins(0, 4, 0, 4);
                    btn.setLayoutParams(params);
                    holder.assetsContainer.addView(btn);
                }
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class ReleaseViewHolder extends RecyclerView.ViewHolder {
            TextView tvTagName, tvName, tvDate, tvPrerelease, tvBody;
            LinearLayout assetsContainer;

            ReleaseViewHolder(View itemView) {
                super(itemView);
                tvTagName = itemView.findViewById(R.id.tvTagName);
                tvName = itemView.findViewById(R.id.tvName);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvPrerelease = itemView.findViewById(R.id.tvPrerelease);
                tvBody = itemView.findViewById(R.id.tvBody);
                assetsContainer = itemView.findViewById(R.id.assetsContainer);
            }
        }
    }
}
