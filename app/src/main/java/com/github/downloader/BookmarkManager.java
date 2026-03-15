package com.github.downloader;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ブックマーク管理クラス。
 * SharedPreferences に "owner/repo" 形式の文字列セットとして保存する。
 * 追加順を保持するため LinkedHashSet を使用。
 */
public class BookmarkManager {

    private static final String PREF_NAME = "bookmarks";
    private static final String KEY_BOOKMARKS = "repos";

    private final SharedPreferences prefs;

    public BookmarkManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** ブックマーク一覧を追加順で返す */
    public List<String> getAll() {
        Set<String> set = prefs.getStringSet(KEY_BOOKMARKS, new LinkedHashSet<String>());
        return new ArrayList<>(set);
    }

    /** 追加済みかどうか */
    public boolean contains(String owner, String repo) {
        return getAll().contains(key(owner, repo));
    }

    /** ブックマークに追加（既存なら何もしない） */
    public void add(String owner, String repo) {
        Set<String> set = new LinkedHashSet<>(getAll());
        set.add(key(owner, repo));
        save(set);
    }

    /** ブックマークから削除 */
    public void remove(String owner, String repo) {
        Set<String> set = new LinkedHashSet<>(getAll());
        set.remove(key(owner, repo));
        save(set);
    }

    /** トグル: 未登録なら追加、登録済みなら削除。追加後の状態を返す */
    public boolean toggle(String owner, String repo) {
        if (contains(owner, repo)) {
            remove(owner, repo);
            return false;
        } else {
            add(owner, repo);
            return true;
        }
    }

    /** "owner/repo" → owner と repo に分割して返す */
    public static String[] split(String entry) {
        return entry.split("/", 2);
    }

    private String key(String owner, String repo) {
        return owner + "/" + repo;
    }

    private void save(Set<String> set) {
        prefs.edit().putStringSet(KEY_BOOKMARKS, set).apply();
    }
}
