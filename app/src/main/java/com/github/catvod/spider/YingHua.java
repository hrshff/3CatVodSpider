package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

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

public class YingHua extends Spider {

    private static final String SITE_URL = "https://www.yinhuadm.cc";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Referer", SITE_URL + "/");
        return headers;
    }

    private String fetch(String url) {
        return OkHttp.string(url, getHeaders());
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return SITE_URL + url;
        return SITE_URL + "/" + url;
    }

    private String extractId(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Matcher m = Pattern.compile("/v/(\\d+)\\.html").matcher(url);
        if (m.find()) return m.group(1);
        m = Pattern.compile("/v/(\\d+)-").matcher(url);
        if (m.find()) return m.group(1);
        return "";
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("10", "日本动漫"));
        classes.add(new Class("9", "国产动漫"));
        classes.add(new Class("11", "欧美动漫"));
        classes.add(new Class("4", "动漫电影"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> list = new ArrayList<>();
        HashSet<String> idSet = new HashSet<>();

        String html = fetch(SITE_URL + "/");
        Document doc = Jsoup.parse(html);

        Elements slides = doc.select(".swiper-big .swiper-slide");
        for (Element slide : slides) {
            Element link = slide.selectFirst("a.banner");
            if (link == null) continue;

            String href = fixUrl(link.attr("href"));
            String id = extractId(href);
            if (TextUtils.isEmpty(id)) continue;

            String title = "";
            Element titleEl = slide.selectFirst(".v-title span");
            if (titleEl != null) title = titleEl.text().trim();

            String pic = "";
            String style = link.attr("style");
            if (!TextUtils.isEmpty(style)) {
                Matcher m = Pattern.compile("url\\((.*?)\\)").matcher(style);
                if (m.find()) {
                    pic = m.group(1).trim();
                    if ((pic.startsWith("\"") && pic.endsWith("\"")) ||
                        (pic.startsWith("'") && pic.endsWith("'"))) {
                        pic = pic.substring(1, pic.length() - 1);
                    }
                }
            }

            String status = "";
            Element ins = slide.selectFirst(".v-ins p");
            if (ins != null) status = ins.text().trim();

            if (idSet.contains(id)) continue;
            idSet.add(id);

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(title);
            vod.setVodPic(fixUrl(pic));
            vod.setVodRemarks(status);
            list.add(vod);
        }

        Elements items = doc.select(".module-poster-item.module-item");
        for (Element item : items) {
            String href = fixUrl(item.attr("href"));
            String title = item.attr("title");
            String id = extractId(href);
            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(title)) continue;

            String pic = "";
            Element img = item.selectFirst(".module-item-pic img");
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }

            String status = "";
            Element note = item.selectFirst(".module-item-note");
            if (note != null) status = note.text().trim();

            if (idSet.contains(id)) continue;
            idSet.add(id);

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(title);
            vod.setVodPic(fixUrl(pic));
            vod.setVodRemarks(status);
            list.add(vod);
        }

        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        int page;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }

        String url;
        if (page == 1) {
            url = SITE_URL + "/w/" + tid + ".html";
        } else {
            url = SITE_URL + "/w/" + tid + "/page/" + page + ".html";
        }

        String html = fetch(url);
        Document doc = Jsoup.parse(html);

        Elements items = doc.select(".module-poster-item.module-item");
        for (Element item : items) {
            String href = fixUrl(item.attr("href"));
            String title = item.attr("title");
            String id = extractId(href);
            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(title)) continue;

            String pic = "";
            Element img = item.selectFirst(".module-item-pic img");
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }

            String status = "";
            Element note = item.selectFirst(".module-item-note");
            if (note != null) status = note.text().trim();

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(title);
            vod.setVodPic(fixUrl(pic));
            vod.setVodRemarks(status);
            list.add(vod);
        }

        boolean hasNext = doc.select(".page-link").size() > 1 || list.size() >= 24;
        int pageCount = hasNext ? page + 1 : page;
        int total = list.size() > 0 ? page * 24 + 1 : 0;

        return Result.get().vod(list).page(page, pageCount, 24, total).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        String id = ids.get(0);

        String detailUrl = SITE_URL + "/v/" + id + ".html";
        String html = fetch(detailUrl);
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(id);

        Element h1 = doc.selectFirst("h1");
        if (h1 != null) vod.setVodName(h1.text().trim());

        Element picEl = doc.selectFirst(".module-item-pic img");
        if (picEl != null) {
            String pic = picEl.attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = picEl.attr("src");
            vod.setVodPic(fixUrl(pic));
        }

        Elements infoItems = doc.select(".module-info-item");
        for (Element item : infoItems) {
            String text = item.text();
            if (text.contains("导演")) {
                vod.setVodDirector(text.replace("导演：", "").replace("导演:", "").trim());
            } else if (text.contains("主演")) {
                vod.setVodActor(text.replace("主演：", "").replace("主演:", "").trim());
            } else if (text.contains("类型")) {
                vod.setTypeName(text.replace("类型：", "").replace("类型:", "").trim());
            } else if (text.contains("地区")) {
                vod.setVodArea(text.replace("地区：", "").replace("地区:", "").trim());
            } else if (text.contains("年份") || text.contains("年代")) {
                vod.setVodYear(text.replace("年份：", "").replace("年份:", "").replace("年代：", "").trim());
            }
        }

        Element descEl = doc.selectFirst(".module-info-introduction-content");
        if (descEl != null) {
            vod.setVodContent(descEl.text().trim());
        }

        List<String> playFroms = new ArrayList<>();
        List<String> playUrls = new ArrayList<>();

        Elements tabs = doc.select(".module-tab-item");
        Elements playlists = doc.select(".module-play-list-content");

        for (int i = 0; i < tabs.size() && i < playlists.size(); i++) {
            Element tab = tabs.get(i);
            String tabName = tab.selectFirst("span") != null
                    ? tab.selectFirst("span").text().trim()
                    : tab.text().trim();
            if (TextUtils.isEmpty(tabName) || tabName.contains("选择")) continue;
            playFroms.add(tabName);

            Element playlist = playlists.get(i);
            Elements links = playlist.select("a");
            List<String> urls = new ArrayList<>();

            for (Element link : links) {
                String name = link.text().trim();
                String href = fixUrl(link.attr("href"));
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
        if (TextUtils.isEmpty(id)) {
            return Result.get().url("http://127.0.0.1:9978/empty").parse(1).string();
        }

        String playUrl = id.startsWith("http") ? id : fixUrl(id);
        String html = fetch(playUrl);

        String playerJson = "";
        Matcher m = Pattern.compile("var player_aaaa\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL).matcher(html);
        if (m.find()) {
            playerJson = m.group(1);
        } else {
            m = Pattern.compile("var player_\\w+\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL).matcher(html);
            if (m.find()) playerJson = m.group(1);
        }

        HashMap<String, String> header = getHeaders();
        header.put("Origin", SITE_URL);

        if (!TextUtils.isEmpty(playerJson)) {
            try {
                org.json.JSONObject player = new org.json.JSONObject(playerJson);
                String url = player.optString("url", "");
                int encrypt = player.optInt("encrypt", 0);

                if (encrypt == 1) {
                    url = java.net.URLDecoder.decode(url, "UTF-8");
                } else if (encrypt == 2) {
                    url = java.net.URLDecoder.decode(
                            new String(android.util.Base64.decode(url, android.util.Base64.DEFAULT)),
                            "UTF-8");
                }

                url = fixUrl(url);

                boolean isM3u8 = url.contains(".m3u8");
                boolean isMp4 = url.contains(".mp4");
                if (isM3u8 || isMp4) {
                    return Result.get().url(url).parse(0).header(header).string();
                }
            } catch (Exception e) {
                // ignore
            }
        }

        Element iframe = Jsoup.parse(html).selectFirst("iframe");
        if (iframe != null) {
            String src = fixUrl(iframe.attr("src"));
            return Result.get().url(src).parse(1).header(header).string();
        }

        return Result.get().url(playUrl).parse(1).header(header).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();
        int page;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }

        String encodedKey = URLEncoder.encode(key, "UTF-8");
        String url = SITE_URL + "/vch/" + encodedKey + ".html";

        String html = fetch(url);
        Document doc = Jsoup.parse(html);

        Elements items = doc.select(".module-card-item.module-item");
        if (items.isEmpty()) {
            items = doc.select(".module-card-item");
        }

        for (Element item : items) {
            String title = "";
            Element titleEl = item.selectFirst(".module-card-item-title a strong");
            if (titleEl != null) title = titleEl.text().trim();
            if (TextUtils.isEmpty(title)) {
                Element link = item.selectFirst("a");
                if (link != null) title = link.attr("title");
            }

            Element link = item.selectFirst("a[href^=/v/]");
            if (link == null) link = item.selectFirst("a");
            if (link == null) continue;

            String href = fixUrl(link.attr("href"));
            String id = extractId(href);
            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(title)) continue;

            String pic = "";
            Element img = item.selectFirst(".module-card-item-poster img");
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }
            if (TextUtils.isEmpty(pic)) {
                img = item.selectFirst("img");
                if (img != null) {
                    pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                }
            }

            String status = "";
            Element note = item.selectFirst(".module-item-note");
            if (note != null) status = note.text().trim();
            if (TextUtils.isEmpty(status)) {
                Element info = item.selectFirst(".module-info-item-content");
                if (info != null) status = info.text().trim();
            }

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(title);
            vod.setVodPic(fixUrl(pic));
            vod.setVodRemarks(status);
            list.add(vod);
        }

        boolean hasNext = list.size() >= 24;
        int pageCount = hasNext ? page + 1 : page;
        int total = list.isEmpty() ? 0 : list.size();

        return Result.get().vod(list).page(page, pageCount, 24, total).string();
    }
}
