package com.gavin.scoretv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

final class Song {
    final String id;
    final String title;
    final String artist;
    final List<String> pages;

    Song(String id, String title, String artist, List<String> pages) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.pages = pages;
    }

    static Song fromJson(JSONObject object, String baseUrl) throws Exception {
        String id = object.getString("id");
        String title = object.optString("title", "未命名歌曲");
        String artist = object.optString("artist", "");
        JSONArray pageArray = object.optJSONArray("pages");
        List<String> pages = new ArrayList<>();
        if (pageArray != null) {
            // 服务端可能部署在子路径（如 https://example.com/guitar），图片地址是带前缀的绝对路径，
            // 必须按站点根解析，不能把前缀当相对路径拼到 baseUrl 后面。
            String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            URL baseLink = new URL(base);
            for (int i = 0; i < pageArray.length(); i++) {
                String path = pageArray.getJSONObject(i).getString("url");
                pages.add(new URL(baseLink, path).toString());
            }
        }
        return new Song(id, title, artist, pages);
    }

    int screenCount() {
        return (pages.size() + 1) / 2;
    }
}
