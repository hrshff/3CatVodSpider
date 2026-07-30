package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 蘑菇影视 (www.5o5k.com)
 * TVBox Java Spider - 嗷呜模式升级版
 *
 * 升级内容：
 * 1. extends AowuSpider（统一基类）
 * 2. 删除自建 getHeaders()/fetch()/abs()，使用基类能力
 * 3. ext 支持对象配置（site/sites/hosts）
 * 4. 动态 UA + CookieJar + SSL 绕过 + 自定义 DNS
 */
public class Mogu extends AowuSpider {

    private static final Pattern ID_PATTERN = Pattern.compile("/voddetail/(\d+)\.html");

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("20", "电影"));
        classes.add(new Class("35", "连续剧"));
        classes.add(new Class("43", "综艺"));
        classes.add(new Class("48", "动漫"));
        classes.add(new Class("54", "影视解说"));
        classes.add(new Class("55", "短剧"));
        classes.add(new Class("63", "预告片"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        String html = fetch(getActiveSite());
        Document doc = Jsoup.parse(html);
        return Result.string(parsePosterItems(doc)).string();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}

        String url;
        if (page == 1) {
            url = getActiveSite() + "/vodshow/" + tid + "-----------.html";
        } else {
            url = getActiveSite() + "/vodshow/" + tid + "-----------" + page + ".html";
        }

        String html = fetch(url);
        Document doc = Jsoup.parse(html);
        List<Vod> list = parsePosterItems(doc);

        boolean hasNext = doc.select(".module-paper-item").size() > 0 || list.size() >= 24;

        return Result.get().vod(list).page(page, hasNext ? page + 1 : page, 24, list.size() > 0 ? page * 24 + 1 : 0).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        String id = ids.get(0);

        String detailUrl = getActiveSite() + "/voddetail/" + id + ".html";
        String html = fetch(detailUrl);
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(id);

        Element h1 = doc.selectFirst("h1");
        if (h1 != null) vod.setVodName(h1.text().trim());

        String pic = "";
        Element ogImg = doc.selectFirst("meta[property=og:image]");
        if (ogImg != null) pic = ogImg.attr("content");
        if (TextUtils.isEmpty(pic)) {
            Element img = doc.selectFirst(".module-item-pic img");
            if (img != null) pic = img.attr("data-original");
        }
        vod.setVodPic(abs(pic));

        // 信息字段
        Elements infoItems = doc.select(".module-info-item");
        for (Element item : infoItems) {
            String label = item.selectFirst(".module-info-item-title") != null
                ? item.selectFirst(".module-info-item-title").text() : "";
            String value = item.selectFirst(".module-info-item-content") != null
                ? item.selectFirst(".module-info-item-content").text() : "";

            if (label.contains("导演")) vod.setVodDirector(value);
            else if (label.contains("主演")) vod.setVodActor(value);
            else if (label.contains("类型")) vod.setTypeName(value);
            else if (label.contains("地区")) vod.setVodArea(value);
            else if (label.contains("年份")) vod.setVodYear(value);
        }

        // 简介
        Element desc = doc.selectFirst(".module-info-introduction .module-info-item-content");
        if (desc != null) vod.setVodContent(desc.text().trim());

        // 播放源
        List<String> playFroms = new ArrayList<>();
        List<String> playUrls = new ArrayList<>();

        Elements tabs = doc.select(".module-tab-item");
        for (int i = 0; i < tabs.size(); i++) {
            Element tab = tabs.get(i);
            String tabName = tab.selectFirst("span") != null ? tab.selectFirst("span").text().trim() : ("线路" + (i + 1));
            playFroms.add(tabName);
        }
        if (playFroms.isEmpty()) playFroms.add("默认线路");

        Elements playlists = doc.select(".module-play-list");
        for (int i = 0; i < playlists.size(); i++) {
            Element playlist = playlists.get(i);
            Elements links = playlist.select("a");
            List<String> urls = new ArrayList<>();
            for (Element link : links) {
                String name = link.text().trim();
                String href = abs(link.attr("href"));
                if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(href)) {
                    urls.add(name + "$" + href);
                }
            }
            playUrls.add(String.join("#", urls));
        }

        vod.setVodPlayFrom(String.join("$$$", playFroms));
        vod.setVodPlayUrl(String.join("$$$", playUrls));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) return Result.get().url("").string();

        String playUrl = id.startsWith("http") ? id : abs(id);
        String html = fetch(playUrl);
        Document doc = Jsoup.parse(html);

        // player_aaaa 解密（保留原有逻辑）
        String url = "";
        Element script = doc.selectFirst("script:containsData(player_aaaa)");
        if (script != null) {
            String scriptText = script.data();
            Matcher m = Pattern.compile(""url":"([^"]+)"").matcher(scriptText);
            if (m.find()) url = m.group(1).replace("\/", "/");
        }

        HashMap<String, String> header = new HashMap<>();
        header.put("Referer", playUrl);
        header.put("Origin", getActiveSite());

        if (!TextUtils.isEmpty(url)) {
            return Result.get().url(url).parse(0).header(header).string();
        }

        return Result.get().url(playUrl).parse(1).header(header).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}

        String encodedKey = URLEncoder.encode(key, "UTF-8");
        String url = getActiveSite() + "/vodsearch/" + encodedKey + "----------" + page + ".html";

        String html = fetch(url);
        Document doc = Jsoup.parse(html);
        List<Vod> list = parsePosterItems(doc);

        boolean hasNext = doc.select(".module-paper-item").size() > 0 || list.size() >= 24;
        return Result.get().vod(list).page(page, hasNext ? page + 1 : page, 24, list.size()).string();
    }

    // ========== 私有解析工具 ==========

    private List<Vod> parsePosterItems(Document doc) {
        List<Vod> list = new ArrayList<>();
        HashSet<String> idSet = new HashSet<>();

        Elements items = doc.select(".module-poster-item");
        for (Element item : items) {
            String href = abs(item.attr("href"));
            Matcher m = ID_PATTERN.matcher(href);
            if (!m.find()) continue;
            String id = m.group(1);
            if (idSet.contains(id)) continue;

            String title = "";
            Element titleEl = item.selectFirst(".module-poster-item-title");
            if (titleEl != null) title = titleEl.text().trim();

            String pic = "";
            Element img = item.selectFirst("img");
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }

            String remark = "";
            Element note = item.selectFirst(".module-item-note");
            if (note != null) remark = note.text().trim();

            idSet.add(id);
            list.add(new Vod(id, title, abs(pic), remark));
        }
        return list;
    }
}
