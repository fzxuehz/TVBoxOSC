package com.github.tvbox.osc.util;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orhanobut.hawk.Hawk;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RemotePush {

    private static final String[] MIRRORS = {
            "https://ghproxy.net/https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/push-queue.json",
            "https://ghp.ci/https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/push-queue.json",
            "https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/push-queue.json"
    };

    public static void check() {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        doCheck();
                    }
                }).start();
            }
        }, 15000);
    }

    private static void doCheck() {
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build();
        for (String mirror : MIRRORS) {
            try {
                Request req = new Request.Builder().url(mirror).header("User-Agent", "Mozilla/5.0").build();
                Response resp = client.newCall(req).execute();
                String text = resp.body() != null ? resp.body().string().trim() : "";
                resp.close();
                if (!text.startsWith("{")) continue;
                JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
                if (!obj.has("time") || !obj.has("url")) continue;
                long time = obj.get("time").getAsLong();
                String url = obj.get("url").getAsString();
                String name = obj.has("name") ? obj.get("name").getAsString() : "";
                if (time <= 0 || TextUtils.isEmpty(url)) continue;
                if (time <= Hawk.<Long>get("remote_push_time", 0L)) return;
                apply(time, url, name);
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private static void apply(final long time, final String url, final String name) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                final String old = Hawk.get(HawkConfig.API_URL, "");
                Hawk.put(HawkConfig.API_URL, url);
                ApiConfig.get().loadConfig(false, new ApiConfig.LoadConfigCallback() {
                    @Override
                    public void success() {
                        Hawk.put("remote_push_time", time);
                        toast("远程推送已更新:" + name);
                    }

                    @Override
                    public void error(String msg) {
                        Hawk.put(HawkConfig.API_URL, old);
                        toast("远程推送失败:" + msg);
                    }
                }, null);
            }
        });
    }

    private static void toast(String text) {
        try {
            Toast.makeText(App.getInstance(), text, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}