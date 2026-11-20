package com.github.tvbox.osc.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CloudLiveDialog {

    private static final String[] MIRRORS = {
            "https://ghproxy.net/https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/live-sources.json",
            "https://ghfast.top/https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/live-sources.json",
            "https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/live-sources.json"
    };

    public static void show(Context context) {
        new CloudLiveDialog().showDialog(context);
    }

    private void showDialog(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_cloud_source, null);
        TextView title = view.findViewById(R.id.tvCloudTitle);
        RecyclerView recycler = view.findViewById(R.id.tvCloudRecycler);
        List<LiveItem> items = new ArrayList<>();
        LiveAdapter adapter = new LiveAdapter(context, items);
        recycler.setLayoutManager(new LinearLayoutManager(context));
        recycler.setAdapter(adapter);
        String current = Hawk.get(HawkConfig.LIVE_URL, "");
        title.setText("云端直播源库（当前: " + current + "）");
        AlertDialog dialog = new AlertDialog.Builder(context).setView(view).setCancelable(true).create();
        loadSources(items, adapter, context, dialog);
        dialog.show();
    }

    private void loadSources(List<LiveItem> items, LiveAdapter adapter, Context context, AlertDialog dialog) {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        new Thread(() -> {
            String json = fetchList();
            if (json == null || json.isEmpty()) {
                dialog.dismiss();
                return;
            }
            parse(json, items);
            adapter.notifyDataSetChanged();
            for (int i = 0; i < items.size(); i++) {
                final LiveItem item = items.get(i);
                pool.execute(() -> {
                    item.status = checkLive(item.url);
                    adapter.notifyDataSetChanged();
                });
            }
        }).start();
    }

    private String fetchList() {
        for (String mirror : MIRRORS) {
            try { String t = httpGet(mirror, 10000, 15000); if (t != null && t.trim().startsWith("{")) return t; } catch (Exception ignored) {}
        }
        return null;
    }

    private void parse(String json, List<LiveItem> items) {
        try {
            JsonObject obj = JsonParser.parseString(json.trim()).getAsJsonObject();
            if (!obj.has("sources")) return;
            JsonArray arr = obj.getAsJsonArray("sources");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject s = arr.get(i).getAsJsonObject();
                LiveItem item = new LiveItem();
                item.name = s.has("name") ? s.get("name").getAsString() : "";
                item.desc = s.has("desc") ? s.get("desc").getAsString() : "";
                item.url = s.has("url") ? s.get("url").getAsString() : "";
                item.status = "检测中…";
                items.add(item);
            }
        } catch (Exception ignored) {}
    }

    private String checkLive(String url) {
        try {
            String text = httpGet(url, 10000, 15000);
            if (text == null || text.isEmpty()) return "❌ 失效";
            int channels = text.contains("#EXTINF") ? text.split("#EXTINF").length - 1 : (text.contains("\"lives\"") ? 1 : (text.length() > 100 ? 1 : 0));
            return channels > 0 ? "✅ 有效（约" + channels + "频道）" : "❌ 无频道数据";
        } catch (Exception e) { return "❌ 无法访问"; }
    }

    private String httpGet(String url, int ct, int rt) {
        try {
            java.net.URL u = new java.net.URL(url);
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
            c.setConnectTimeout(ct); c.setReadTimeout(rt);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (c.getResponseCode() == 200) {
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder(); String l;
                while ((l = r.readLine()) != null) sb.append(l);
                r.close(); c.disconnect(); return sb.toString();
            }
            c.disconnect();
        } catch (Exception ignored) {}
        return null;
    }

    private class LiveAdapter extends RecyclerView.Adapter<LiveAdapter.ViewHolder> {
        private final Context ctx; private final List<LiveItem> items;
        LiveAdapter(Context ctx, List<LiveItem> items) { this.ctx = ctx; this.items = items; }
        @Override public int getItemCount() { return items.size(); }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) { return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_cloud_source, p, false)); }
        @Override public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
            LiveItem item = items.get(pos);
            String cur = Hawk.get(HawkConfig.LIVE_URL, "");
            h.name.setText(item.name + (item.url.equals(cur) ? "（当前）" : ""));
            h.desc.setText(item.desc + "  " + item.status);
            h.itemView.setOnClickListener(v -> {
                if (item.url == null || item.url.isEmpty()) return;
                Hawk.put(HawkConfig.LIVE_URL, item.url);
                Toast.makeText(ctx, "直播源已设置:" + item.name, Toast.LENGTH_SHORT).show();
            });
        }
        class ViewHolder extends RecyclerView.ViewHolder { TextView name, desc;
            ViewHolder(View v) { super(v); name = v.findViewById(R.id.tvItemName); desc = v.findViewById(R.id.tvItemDesc); } }
    }

    private static class LiveItem { String name, desc, url, status; }
}