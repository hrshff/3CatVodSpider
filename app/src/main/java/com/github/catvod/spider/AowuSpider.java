package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;

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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 嗷呜模式统一基类（委托版）
 * 
 * 依赖：AowuHttp（网络）+ AowuCrypto（加密）+ AowuProxy（本地代理）
 */
public abstract class AowuSpider extends Spider {

    protected String siteUrl;
    protected List<String> backupSites;
    protected JSONObject siteConfig;
    protected String spiderKey;

    // 站点级 ID 提取正则（子类可覆盖）
    protected Pattern ID_PATTERN = Pattern.compile("/(?:vod|content|detail|v|voddetail)/(\\d+)\\.html");

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        try {
            parseExt(extend);
        } catch (Exception e) {
            android.util.Log.e("AowuSpider", "parseExt failed: " + e.getMessage() + ", extend=" + extend);
            // Fallback: treat extend as plain site URL (模式1 回退)
            if (extend != null && extend.startsWith("http")) {
                siteUrl = extend;
                backupSites = new ArrayList<>();
                backupSites.add(siteUrl);
                android.util.Log.d("AowuSpider", "Fallback to plain URL: " + siteUrl);
            } else {
                throw e; // 无法回退，继续抛出
            }
        }
    }

    /**
     * 解析 ext 配置（4 种模式）
     * 模式1: 简单字符串 URL
     * 模式2: JSON 对象
     * 模式3: 远程 URL（下载失败自动回退为模式1）
     * 模式4: 加密字符串
     */
    protected void parseExt(String extend) throws Exception {
        if (TextUtils.isEmpty(extend)) return;

        String jsonStr = extend;

        // 模式4: 加密字符串
        if (extend.length() > 64 && !extend.startsWith("http") && !extend.startsWith("{")) {
            jsonStr = AowuCrypto.decrypt(extend);
        }

        // 模式3: 远程 URL（先尝试下载，失败则回退为模式1）
        if (jsonStr.startsWith("http")) {
            try {
                String remote = AowuHttp.get().get(jsonStr, null);
                siteConfig = new JSONObject(remote);
                android.util.Log.d("AowuSpider", "Remote config loaded from: " + jsonStr);
            } catch (Exception e) {
                android.util.Log.w("AowuSpider", "Remote config failed, fallback to plain URL: " + jsonStr);
                siteConfig = new JSONObject();
                siteConfig.put("site", jsonStr);
            }
        } else if (jsonStr.startsWith("{")) {
            siteConfig = new JSONObject(jsonStr);
        } else {
            // 未知格式，当作纯站点URL
            siteConfig = new JSONObject();
            siteConfig.put("site", jsonStr);
        }

        siteUrl = siteConfig.optString("site", "");
        spiderKey = siteConfig.optString("key", "");

        // 多域名
        backupSites = new ArrayList<>();
        if (siteConfig.has("sites")) {
            JSONArray arr = siteConfig.getJSONArray("sites");
            for (int i = 0; i < arr.length(); i++) backupSites.add(arr.getString(i));
        }
        if (backupSites.isEmpty() && !TextUtils.isEmpty(siteUrl)) backupSites.add(siteUrl);

        // hosts 映射
        if (siteConfig.has("hosts")) {
            JSONObject hosts = siteConfig.getJSONObject("hosts");
            JSONArray names = hosts.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String domain = names.getString(i);
                    AowuHttp.addHost(domain, hosts.getString(domain));
                }
            }
        }

        // 预设 Cookie
        if (siteConfig.has("cookie")) {
            JSONObject c = siteConfig.getJSONObject("cookie");
            String domain = c.optString("domain", "");
            String name = c.optString("name", "");
            String value = c.optString("value", "");
            if (!TextUtils.isEmpty(domain) && !TextUtils.isEmpty(name)) {
                AowuHttp.injectCookie(domain, name, value);
            }
        }
    }

    /** 获取当前可用域名（永不返回 null） */
    protected String getActiveSite() {
        if (backupSites != null && !backupSites.isEmpty()) {
            String site = backupSites.get(0);
            if (site != null && !site.isEmpty()) return site;
        }
        if (siteUrl != null && !siteUrl.isEmpty()) return siteUrl;
        android.util.Log.e("AowuSpider", "getActiveSite() returning empty! backupSites=" + backupSites + ", siteUrl=" + siteUrl);
        return "";
    }

    /** URL 补全 */
    protected String abs(String url) {
        return fixUrl(url);
    }

    protected String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        url = url.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "https:" + url;
        String base = getActiveSite();
        if (TextUtils.isEmpty(base)) {
            android.util.Log.e("AowuSpider", "fixUrl() base is empty, cannot resolve: " + url);
            return "";
        }
        return base + (url.startsWith("/") ? url : "/" + url);
    }

    /** 提取 ID */
    protected String extractId(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Matcher m = ID_PATTERN.matcher(url);
        if (m.find()) return m.group(1);
        return "";
    }

    // ===== HTTP 请求（带 URL 合法性校验）=====

    protected String fetch(String path) throws Exception {
        String url = abs(path);
        if (TextUtils.isEmpty(url) || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new Exception("fetch() invalid URL: [" + url + "]");
        }
        return AowuHttp.get().get(url, null);
    }

    protected String fetch(String path, HashMap<String, String> extra) throws Exception {
        String url = abs(path);
        if (TextUtils.isEmpty(url) || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new Exception("fetch() invalid URL: [" + url + "]");
        }
        return AowuHttp.get().get(url, extra);
    }

    protected String post(String path, HashMap<String, String> params) throws Exception {
        String url = abs(path);
        if (TextUtils.isEmpty(url) || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new Exception("post() invalid URL: [" + url + "]");
        }
        return AowuHttp.get().post(url, params, null);
    }

    // ===== 本地代理委托给 AowuProxy =====

    public String proxyLocal(Map<String, String> params) throws Exception {
        return AowuProxy.handle(params);
    }

    // ===== 通用业务模板（子类可覆盖）=====

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        if (siteConfig != null && siteConfig.has("classes")) {
            JSONArray arr = siteConfig.getJSONArray("classes");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                classes.add(new Class(o.getString("id"), o.getString("name")));
            }
        }
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> list = new ArrayList<>();
        HashSet<String> idSet = new HashSet<>();
        String html = fetch("/");
        Document doc = Jsoup.parse(html);
        Elements items = doc.select("a.media-content, .module-poster-item a, .myui-vodlist__box a, .vodlist-item a");
        for (Element item : items) {
            Vod vod = parseVodFromElement(item, idSet);
            if (vod != null) list.add(vod);
        }
        return Result.string(list);
    }

    protected Vod parseVodFromElement(Element elem, HashSet<String> idSet) {
        String href = abs(elem.attr("href"));
        String id = extractId(href);
        if (TextUtils.isEmpty(id) || idSet.contains(id)) return null;
        String title = "";
        Element h = elem.selectFirst("h4, h3, h2");
        if (h != null) title = h.text().trim();
        if (TextUtils.isEmpty(title)) {
            Element img = elem.selectFirst("img");
            if (img != null) title = img.attr("alt");
        }
        if (TextUtils.isEmpty(title)) return null;
        String pic = "";
        Element img = elem.selectFirst("img");
        if (img != null) {
            pic = img.attr("data-src");
            if (TextUtils.isEmpty(pic)) pic = img.attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = img.attr("src");
        }
        String status = "";
        Element note = elem.selectFirst("span.position-absolute, .hl-note, .note, .pic-text");
        if (note != null) status = note.text().trim();
        idSet.add(id);
        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(title);
        vod.setVodPic(abs(pic));
        vod.setVodRemarks(status);
        return vod;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}
        String url = getActiveSite() + "/list/" + tid + (page == 1 ? ".html" : "/index_" + page + ".html");
        String html = fetch(url);
        Document doc = Jsoup.parse(html);
        Elements items = doc.select("a.media-content, .module-poster-item a, .myui-vodlist__box a");
        for (Element item : items) {
            Vod vod = parseVodFromElement(item, new HashSet<>());
            if (vod != null) list.add(vod);
        }
        boolean hasNext = doc.select(".page-list a, .pagination a, .stui-page__item a").size() > 0 || list.size() >= 24;
        return Result.get().vod(list).page(page, hasNext ? page + 1 : page, 24, list.size() > 0 ? page * 24 + 1 : 0).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        String id = ids.get(0);
        String html = fetch("/content/" + id + ".html");
        Document doc = Jsoup.parse(html);
        Vod vod = new Vod();
        vod.setVodId(id);
        Element h1 = doc.selectFirst("h1");
        if (h1 != null) vod.setVodName(h1.text().trim());
        String pic = "";
        Element ogImg = doc.selectFirst("meta[property=og:image]");
        if (ogImg != null) pic = ogImg.attr("content");
        if (TextUtils.isEmpty(pic)) {
            Element img = doc.selectFirst(".media-content img, .vod-pic img, .detail-pic img");
            if (img != null) {
                pic = img.attr("data-src");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }
        }
        vod.setVodPic(abs(pic));
        String director = "", actor = "", typeName = "", area = "", year = "";
        Elements infoItems = doc.select(".hl-info li, .info li, .vod-info li, .detail-info p");
        for (Element item : infoItems) {
            String text = item.text();
            if (text.contains("导演")) director = text.replaceAll("导演[：:]", "").trim();
            else if (text.contains("主演")) actor = text.replaceAll("主演[：:]", "").trim();
            else if (text.contains("类型")) typeName = text.replaceAll("类型[：:]", "").trim();
            else if (text.contains("地区")) area = text.replaceAll("地区[：:]", "").trim();
            else if (text.contains("年份") || text.contains("年代")) year = text.replaceAll("年份[：:]|年代[：:]", "").trim();
        }
        vod.setVodDirector(director);
        vod.setVodActor(actor);
        vod.setTypeName(typeName);
        vod.setVodArea(area);
        vod.setVodYear(year);
        Element descEl = doc.selectFirst("meta[name=description]");
        if (descEl != null) {
            String desc = descEl.attr("content");
            if (desc.contains("剧情")) desc = desc.substring(desc.indexOf("剧情") + 3);
            vod.setVodContent(desc.trim());
        }
        List<String> playFroms = new ArrayList<>();
        List<String> playUrls = new ArrayList<>();
        Elements tabLinks = doc.select(".nav-urls a, .play-tab a, .play-nav a");
        List<String> tabNames = new ArrayList<>();
        for (Element tab : tabLinks) {
            String name = tab.text().trim();
            if (!TextUtils.isEmpty(name) && !name.contains("选集") && !name.contains("剧情")) tabNames.add(name);
        }
        Elements playlists = doc.select(".v-playurl .hl-plays-list, .playlist, .play-list");
        if (playlists.isEmpty()) playlists = doc.select(".hl-plays-list");
        for (int i = 0; i < playlists.size(); i++) {
            Element playlist = playlists.get(i);
            String tabName = (i < tabNames.size()) ? tabNames.get(i) : ("线路" + (i + 1));
            playFroms.add(tabName);
            Elements links = playlist.select("a");
            List<String> urls = new ArrayList<>();
            for (Element link : links) {
                String name = link.text().trim();
                String href = abs(link.attr("href"));
                if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(href)) urls.add(name + "$" + href);
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
        String url = "";
        Element playerDiv = doc.selectFirst(".video-iframe, #player, .player, #playiframe");
        if (playerDiv != null) {
            url = playerDiv.attr("data-play");
            if (TextUtils.isEmpty(url)) url = playerDiv.attr("src");
        }
        HashMap<String, String> header = new HashMap<>();
        header.put("Referer", playUrl);
        header.put("Origin", getActiveSite());
        if (flag.contains("YZ") || flag.contains("yz") || flag.contains("webview")) {
            return Result.get().url(playUrl).parse(1).header(header).string();
        }
        if (!TextUtils.isEmpty(url) && (url.contains(".m3u8") || url.contains(".mp4"))) {
            return Result.get().url(url).parse(0).header(header).string();
        }
        Element iframe = doc.selectFirst("iframe");
        if (iframe != null) {
            String src = abs(iframe.attr("src"));
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
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}
        String encodedKey = URLEncoder.encode(key, "UTF-8");
        String url = getActiveSite() + "/dmso/so.html?wd=" + encodedKey;
        if (page > 1) url = url + "&page=" + page;
        String html = fetch(url);
        Document doc = Jsoup.parse(html);
        Elements items = doc.select("a.media-content, .module-poster-item a, .myui-vodlist__box a");
        for (Element item : items) {
            Vod vod = parseVodFromElement(item, new HashSet<>());
            if (vod != null) list.add(vod);
        }
        boolean hasNext = doc.select(".page-list a, .pagination a").size() > 0 || list.size() >= 24;
        return Result.get().vod(list).page(page, hasNext ? page + 1 : page, 24, list.size()).string();
    }
}
