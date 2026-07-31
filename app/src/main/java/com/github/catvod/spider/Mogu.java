package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 蘑菇影视 (www.5o5k.com)
 * TVBox Java Spider
 */
public class Mogu extends Spider {

    private static final String HOST = "https://www.5o5k.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private HashMap<String, String> getHeaders(String referer) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Referer", referer);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        return headers;
    }

    private String fetch(String url) {
        return OkHttp.string(url, getHeaders(HOST + "/"));
    }

    private String abs(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        url = url.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "https:" + url;
        return HOST + url;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();

        classes.put(new JSONObject().put("type_id", "20").put("type_name", "电影"));
        classes.put(new JSONObject().put("type_id", "35").put("type_name", "连续剧"));
        classes.put(new JSONObject().put("type_id", "43").put("type_name", "综艺"));
        classes.put(new JSONObject().put("type_id", "48").put("type_name", "动漫"));
        classes.put(new JSONObject().put("type_id", "54").put("type_name", "影视解说"));
        classes.put(new JSONObject().put("type_id", "55").put("type_name", "短剧"));
        classes.put(new JSONObject().put("type_id", "63").put("type_name", "预告片"));

        result.put("class", classes);
        result.put("filters", new JSONObject());
        result.put("list", new JSONArray());

        return result.toString();
    }

    @Override
    public String homeVideoContent() throws Exception {
        String html = fetch(HOST);
        Document doc = Jsoup.parse(html);
        JSONArray list = parsePosterItems(doc);
        JSONObject result = new JSONObject();
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = 1;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception ignored) {}

        String url;
        if (page == 1) {
            url = HOST + "/vodshow/" + tid + "-----------.html";
        } else {
            url = HOST + "/vodshow/" + tid + "-----------" + page + ".html";
        }

        String html = fetch(url);
        Document doc = Jsoup.parse(html);
        JSONArray list = parsePosterItems(doc);

        boolean hasNext = doc.select(".module-paper-item").size() > 0 || list.length() >= 24;

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", hasNext ? page + 1 : page);
        result.put("limit", 24);
        result.put("total", list.length() > 0 ? page * 24 + 1 : 0);
        result.put("list", list);

        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        JSONArray list = new JSONArray();

        for (String id : ids) {
            if (id == null || id.isEmpty()) continue;

            String html = fetch(id);
            Document doc = Jsoup.parse(html);

            String vodName = "";
            Element h1 = doc.selectFirst("h1");
            if (h1 != null) vodName = h1.text().trim();
            if (vodName.isEmpty()) {
                Element metaName = doc.selectFirst("meta[itemprop=name]");
                if (metaName != null) vodName = metaName.attr("content");
            }

            String vodPic = "";
            Element metaImg = doc.selectFirst("meta[itemprop=image]");
            if (metaImg != null) vodPic = metaImg.attr("content");

            String vodContent = "";
            Element metaDesc = doc.selectFirst("meta[itemprop=description]");
            if (metaDesc != null) vodContent = metaDesc.attr("content");

            String vodActor = "";
            Element metaActor = doc.selectFirst("meta[itemprop=actor]");
            if (metaActor != null) vodActor = metaActor.attr("content");

            String vodDirector = "";
            Element metaDirector = doc.selectFirst("meta[itemprop=director]");
            if (metaDirector != null) vodDirector = metaDirector.attr("content");

            String vodArea = "";
            Element metaArea = doc.selectFirst("meta[itemprop=contentLocation]");
            if (metaArea != null) vodArea = metaArea.attr("content");

            String vodClass = "";
            Element metaClass = doc.selectFirst("meta[itemprop=class]");
            if (metaClass != null) vodClass = metaClass.attr("content");

            String vodYear = "";
            Element metaDate = doc.selectFirst("meta[itemprop=uploadDate]");
            if (metaDate != null) {
                String date = metaDate.attr("content");
                if (date.length() >= 4) vodYear = date.substring(0, 4);
            }

            List<String> froms = new ArrayList<>();
            List<String> urls = new ArrayList<>();

            Elements tabs = doc.select(".module-tab-item");
            Elements playLists = doc.select(".module-play-list");

            for (int i = 0; i < tabs.size(); i++) {
                Element tab = tabs.get(i);
                String sourceName = "";
                Element span = tab.selectFirst("span");
                if (span != null) sourceName = span.text().trim();
                if (sourceName.isEmpty()) sourceName = "源" + (i + 1);

                List<String> playLinks = new ArrayList<>();
                if (i < playLists.size()) {
                    Element playList = playLists.get(i);
                    Elements links = playList.select(".module-play-list-link");
                    for (Element link : links) {
                        String href = link.attr("href");
                        String epName = "";
                        Element epSpan = link.selectFirst("span");
                        if (epSpan != null) epName = epSpan.text().trim();
                        if (epName.isEmpty()) epName = link.text().trim();
                        if (!href.isEmpty() && !epName.isEmpty()) {
                            playLinks.add(epName + "$" + abs(href));
                        }
                    }
                }

                if (!playLinks.isEmpty()) {
                    froms.add(sourceName);
                    urls.add(String.join("#", playLinks));
                }
            }

            JSONObject vod = new JSONObject();
            vod.put("vod_id", id);
            vod.put("vod_name", vodName);
            vod.put("vod_pic", vodPic);
            vod.put("vod_content", vodContent);
            vod.put("vod_actor", vodActor);
            vod.put("vod_director", vodDirector);
            vod.put("vod_area", vodArea);
            vod.put("vod_class", vodClass);
            vod.put("vod_year", vodYear);
            vod.put("vod_play_from", String.join("$$$", froms));
            vod.put("vod_play_url", String.join("$$$", urls));

            list.put(vod);
        }

        JSONObject result = new JSONObject();
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (id == null || id.trim().isEmpty()) {
            JSONObject result = new JSONObject();
            result.put("parse", 1);
            result.put("url", "http://127.0.0.1:9978/empty");
            result.put("msg", "无效播放地址");
            return result.toString();
        }

        String html = fetch(id);

        Pattern pattern = Pattern.compile("var player_\\w+\\s*=\\s*\\{.*?\\};", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);

        if (!matcher.find()) {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", "");
            result.put("msg", "未找到播放器配置");
            return result.toString();
        }

        try {
            String playerStr = matcher.group();

            Pattern urlPattern = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
            Matcher urlMatcher = urlPattern.matcher(playerStr);
            if (!urlMatcher.find()) {
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("url", "");
                result.put("msg", "未找到播放URL");
                return result.toString();
            }

            String mediaUrl = urlMatcher.group(1);

            int encrypt = 0;
            Pattern encPattern = Pattern.compile("\\\"encrypt\\\"\\s*:\\s*(\\d+)");
            Matcher encMatcher = encPattern.matcher(playerStr);
            if (encMatcher.find()) {
                encrypt = Integer.parseInt(encMatcher.group(1));
            }

            if (encrypt == 1) {
                mediaUrl = java.net.URLDecoder.decode(mediaUrl, "UTF-8");
            }

            boolean isM3u8 = mediaUrl.contains(".m3u8");
            boolean isMp4 = mediaUrl.contains(".mp4");
            int parse = (isM3u8 || isMp4) ? 0 : 1;

            JSONObject header = new JSONObject();
            header.put("User-Agent", UA);
            header.put("Referer", id);

            JSONObject result = new JSONObject();
            result.put("parse", parse);
            result.put("url", mediaUrl);
            result.put("header", header.toString());

            return result.toString();

        } catch (Exception e) {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", "");
            result.put("msg", "解析失败: " + e.getMessage());
            return result.toString();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = 1;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception ignored) {}

        String encodedKey = URLEncoder.encode(key, "UTF-8");
        String url;
        if (page == 1) {
            url = HOST + "/vodsearch/" + encodedKey + "-------------.html";
        } else {
            url = HOST + "/vodsearch/" + encodedKey + "----------" + page + "---.html";
        }

        String html = fetch(url);
        Document doc = Jsoup.parse(html);
        JSONArray list = parseSearchItems(doc);

        boolean hasNext = doc.select(".module-paper-item").size() > 0 || list.length() >= 24;

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", hasNext ? page + 1 : page);
        result.put("limit", 24);
        result.put("total", list.length());
        result.put("list", list);

        return result.toString();
    }

    private JSONArray parsePosterItems(Document doc) throws Exception {
        JSONArray list = new JSONArray();
        Elements items = doc.select(".module-poster-item");

        for (Element item : items) {
            String href = item.attr("href");
            String title = item.attr("title");
            String img = "";
            Element imgEl = item.selectFirst("img");
            if (imgEl != null) {
                img = imgEl.attr("data-original");
                if (img.isEmpty()) img = imgEl.attr("src");
            }
            String note = "";
            Element noteEl = item.selectFirst(".module-item-note");
            if (noteEl != null) note = noteEl.text().trim();

            if (href.isEmpty() || title.isEmpty()) continue;

            JSONObject vod = new JSONObject();
            vod.put("vod_id", abs(href));
            vod.put("vod_name", title);
            vod.put("vod_pic", abs(img));
            vod.put("vod_remarks", note);
            list.put(vod);
        }

        return list;
    }

    private JSONArray parseSearchItems(Document doc) throws Exception {
        JSONArray list = new JSONArray();
        Elements items = doc.select(".module-card-item");

        for (Element item : items) {
            Element a = item.selectFirst("a.module-card-item-poster");
            if (a == null) a = item.selectFirst("a[href^=/voddetail/]");

            String href = "";
            if (a != null) href = a.attr("href");

            String title = "";
            Element titleEl = item.selectFirst(".module-card-item-title");
            if (titleEl != null) title = titleEl.text().trim();
            if (title.isEmpty() && a != null) title = a.attr("title");

            String img = "";
            Element imgEl = item.selectFirst("img");
            if (imgEl != null) {
                img = imgEl.attr("data-original");
                if (img.isEmpty()) img = imgEl.attr("src");
            }

            String note = "";
            Element noteEl = item.selectFirst(".module-item-note");
            if (noteEl != null) note = noteEl.text().trim();

            if (href.isEmpty() || title.isEmpty()) continue;

            JSONObject vod = new JSONObject();
            vod.put("vod_id", abs(href));
            vod.put("vod_name", title);
            vod.put("vod_pic", abs(img));
            vod.put("vod_remarks", note);
            list.put(vod);
        }

        return list;
    }
}
