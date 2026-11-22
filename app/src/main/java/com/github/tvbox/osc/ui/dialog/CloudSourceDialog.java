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
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CloudSourceDialog {

    private static final String[] MIRRORS = {
            "https://ghproxy.net/https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/sources.json",
            "https://ghfast.top/https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/sources.json",
            "https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/sources.json"
    };

    public static void show(Context context) {
        new CloudSourceDialog().showDialog(context);
    }

    private void showDialog(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_cloud, null);
        TextView title = view.findViewById(R.id.tvTitle);
        RecyclerView recycler = view.findViewById(R.id.tvRecycler);
        List<CloudItem> items = new ArrayList<>();
        CloudAdapter adapter = new CloudAdapter(context, items);
        recycler.setLayoutManager(new LinearLayoutManager(context));
        recycler.setAdapter(adapter);
        String current = Hawk.get(HawkConfig.API_URL, "");
        title.setText("云端源库（当前: " + current + "）");
        AlertDialog dialog = new AlertDialog.Builder(context).setView(view).setCancelable(true).create();
        loadSources(items, adapter, context, dialog);
        dialog.show();
    }

    private void loadSources(List<CloudItem> items, CloudAdapter adapter, Context context, AlertDialog dialog) {
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
                final CloudItem item = items.get(i);
                pool.execute(() -> {
                    item.status = checkOne(item.url);
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

    private void parse(String json, List<CloudItem> items) {
        try {
            JsonObject obj = JsonParser.parseString(json.trim()).getAsJsonObject();
            if (!obj.has("sources")) return;
            JsonArray arr = obj.getAsJsonArray("sources");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject s = arr.get(i).getAsJsonObject();
                CloudItem item = new CloudItem();
                item.name = s.has("name") ? s.get("name").getAsString() : "";
                item.desc = s.has("desc") ? s.get("desc").getAsString() : "";
                item.url = s.has("url") ? s.get("url").getAsString() : "";
                item.status = "检测中…";
                items.add(item);
            }
        } catch (Exception ignored) {}
    }

    private String checkOne(String url) {
        try {
            String cfg = httpGet(url, 10000, 15000);
            if (cfg == null || cfg.isEmpty()) return "❌ 失效";
            if (!cfg.contains("\"sites\"") && !cfg.contains("spider")) return "❌ 非影视源";
            List<String> apis = extractApis(cfg, 4);
            if (apis.isEmpty()) return "🟡 配置可读（爬虫站）";
            int ok = 0;
            for (String api : apis) {
                try {
                    String u = api + (api.contains("?") ? "&" : "?") + "ac=videolist&wd=" + java.net.URLEncoder.encode("战", "UTF-8");
                    String resp = httpGet(u, 8000, 10000);
                    if (resp != null && (resp.contains("vod") || resp.contains("video") || resp.contains("list"))) ok++;
                } catch (Exception ignored) {}
            }
            return ok > 0 ? "✅ 可播放(" + ok + "/" + apis.size() + "站)" : "🟡 站点未过（配置可读）";
        } catch (Exception e) { return "❌ 无法访问"; }
    }

    private List<String> extractApis(String cfg, int max) {
        List<String> apis = new ArrayList<>();
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{[^{}]*?\"api\"\\s*:\\s*\"(https?://[^\"]+)\"[^{}]*?\\}").matcher(cfg);
            while (m.find() && apis.size() < max) {
                if (m.group().contains("csp_")) continue;
                apis.add(m.group(1));
            }
        } catch (Exception ignored) {}
        return apis;
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

    private class CloudAdapter extends RecyclerView.Adapter<CloudAdapter.ViewHolder> {
        private final Context ctx; private final List<CloudItem> items;
        CloudAdapter(Context ctx, List<CloudItem> items) { this.ctx = ctx; this.items = items; }
        @Override public int getItemCount() { return items.size(); }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) { return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_cloud, p, false)); }
        @Override public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
            CloudItem item = items.get(pos);
            String cur = Hawk.get(HawkConfig.API_URL, "");
            h.name.setText(item.name + (item.url.equals(cur) ? "（当前）" : ""));
            h.desc.setText(item.desc + "  " + item.status);
            h.itemView.setOnClickListener(v -> {
                if (item.url == null || item.url.isEmpty()) return;
                String old = Hawk.get(HawkConfig.API_URL, "");
                Hawk.put(HawkConfig.API_URL, item.url);
                Toast.makeText(ctx, "正在切换:" + item.name, Toast.LENGTH_SHORT).show();
                ApiConfig.get().loadConfig(false, new ApiConfig.LoadConfigCallback() {
                    @Override public void retry() { }
                    @Override public void success() { Toast.makeText(ctx, "已切换:" + item.name, Toast.LENGTH_SHORT).show(); }
                    @Override public void error(String msg) { Hawk.put(HawkConfig.API_URL, old); Toast.makeText(ctx, "切换失败:" + msg, Toast.LENGTH_SHORT).show(); }
                }, null);
            });
        }
        class ViewHolder extends RecyclerView.ViewHolder { TextView name, desc;
            ViewHolder(View v) { super(v); name = v.findViewById(R.id.tvName); desc = v.findViewById(R.id.tvDesc); } }
    }

    private static class CloudItem { String name, desc, url, status; }
}