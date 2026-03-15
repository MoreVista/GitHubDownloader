package com.github.downloader;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BookmarkActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private BookmarkAdapter adapter;
    private BookmarkManager bookmarkManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmark);
        setTitle("ブックマーク");

        bookmarkManager = new BookmarkManager(this);
        recyclerView = findViewById(R.id.recyclerView);
        tvEmpty = findViewById(R.id.tvEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<String> bookmarks = bookmarkManager.getAll();
        if (bookmarks.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter = new BookmarkAdapter(bookmarks);
            recyclerView.setAdapter(adapter);
        }
    }

    // ---- Adapter ----
    private class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.VH> {

        private final List<String> items;

        BookmarkAdapter(List<String> items) { this.items = items; }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bookmark, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, final int position) {
            final String entry = items.get(position);
            final String[] parts = BookmarkManager.split(entry);
            final String owner = parts[0];
            final String repo = parts.length > 1 ? parts[1] : "";

            holder.tvRepo.setText(entry);

            // タップ → 最新リリース画面へ
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(BookmarkActivity.this, ReleaseListActivity.class);
                    intent.putExtra("owner", owner);
                    intent.putExtra("repo", repo);
                    intent.putExtra("latest_only", false);
                    startActivity(intent);
                }
            });

            // 長押し → 削除確認ダイアログ
            holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    new AlertDialog.Builder(BookmarkActivity.this)
                            .setTitle("ブックマークを削除")
                            .setMessage(entry + " を削除しますか？")
                            .setPositiveButton("削除", (dialog, which) -> {
                                bookmarkManager.remove(owner, repo);
                                items.remove(position);
                                notifyItemRemoved(position);
                                notifyItemRangeChanged(position, items.size());
                                if (items.isEmpty()) refresh();
                                Toast.makeText(BookmarkActivity.this,
                                        "削除しました", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("キャンセル", null)
                            .show();
                    return true;
                }
            });

            // ゴミ箱ボタン
            holder.btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(BookmarkActivity.this)
                            .setTitle("ブックマークを削除")
                            .setMessage(entry + " を削除しますか？")
                            .setPositiveButton("削除", (dialog, which) -> {
                                bookmarkManager.remove(owner, repo);
                                items.remove(position);
                                notifyItemRemoved(position);
                                notifyItemRangeChanged(position, items.size());
                                if (items.isEmpty()) refresh();
                            })
                            .setNegativeButton("キャンセル", null)
                            .show();
                }
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvRepo;
            ImageButton btnDelete;
            VH(View v) {
                super(v);
                tvRepo = v.findViewById(R.id.tvRepo);
                btnDelete = v.findViewById(R.id.btnDelete);
            }
        }
    }
}
