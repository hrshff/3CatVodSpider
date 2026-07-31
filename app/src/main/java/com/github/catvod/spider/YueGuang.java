package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 月光影视 (www.shipian8.com)
 * TVBox Java Spider - 播放诊断版
 * 
 * 在playerContent中增加详细调试输出，确认播放页返回内容
 */
public class YueGuang extends Spider {

    private static final String HOST = "https://www.shipian8.com";

    private static final List<Cookie> cookieStore = new ArrayList<>();
    private static OkHttpClient customClient;

    public static OkHttpClient client() {
        if (customClient == null) {
            customClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .cookieJar(new CookieJar() {
                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.addAll(cookies);
                    }
                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        return cookieStore;
                    }
                })
                .build();
        }
        return customClient;
    }

    public static Dns safeDns() {
        return Dns.SYSTEM;
    }

    private Map<String, String> getHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        return headers;
    }

    private Map<String, String> getHeader(String referer) {
        Map<String, String> headers = getHeader();
        if (referer != null && !referer.isEmpty()) {
            headers.put("Referer", referer);
        }
        return headers;
    }

    private String abs(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        url = url.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "https:" + url;
        return HOST + url;
    }

    private boolean isValidHtml(String html) {
        if (html == null || html.length() < 5000) return false;
        return html.contains("stui-vodlist") || html.contains("vodlist") || html.contains("class=\"stui-") || html.contains("player_");
    }

    private String fetchWithClient(String url, String referer) throws Exception {
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", Util.CHROME)
            .header("Referer", referer != null ? referer : HOST + "/")
            .build();

        try (Response response = client().newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            return body;
        }
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();

        classes.put(new JSONObject().put("type_id", "1").put("type_name", "电影"));
        classes.put(new JSONObject().put("type_id", "2").put("type_name", "电视剧"));
        classes.put(new JSONObject().put("type_id", "3").put("type_name", "综艺"));
        classes.put(new JSONObject().put("type_id", "4").put("type_name", "动漫"));
        classes.put(new JSONObject().put("type_id", "5").put("type_name", "短剧"));

        result.put("class", classes);
        result.put("filters", new JSONObject());
        result.put("list", new JSONArray());

        return result.toString();
    }

    @Override
    public String homeVideoContent() throws Exception {
        String html = fetchWithClient(HOST, null);
        Document doc = Jsoup.parse(html);
        JSONArray list = parseVodList(doc);
        JSONObject result = new JSONObject();
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}

        String url = page == 1
            ? HOST + "/zwhstp/" + tid + ".html"
            : HOST + "/zwhstp/" + tid + "-" + page + ".html";

        String html = fetchWithClient(url, HOST + "/");

        if (!isValidHtml(html)) {
            String homeHtml = fetchWithClient(HOST, null);
            if (isValidHtml(homeHtml)) {
                Document homeDoc = Jsoup.parse(homeHtml);
                JSONArray list = parseVodList(homeDoc);
                JSONObject result = new JSONObject();
                result.put("page", page);
                result.put("pagecount", page);
                result.put("limit", 24);
                result.put("total", list.length());
                result.put("list", list);
                return result.toString();
            }
        }

        Document doc = Jsoup.parse(html);
        JSONArray list = parseVodList(doc);

        boolean hasNext = doc.select(".stui-page, .page").size() > 0;

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

            String html = fetchWithClient(id, HOST + "/zwhstp/1.html");
            Document doc = Jsoup.parse(html);

            String vodName = "";
            Element h1 = doc.selectFirst("h1.title");
            if (h1 != null) vodName = h1.text().trim();

            String vodPic = "";
            String vodContent = "";
            String vodYear = "";
            Element ldScript = doc.selectFirst("script[type=application/ld+json]");
            if (ldScript != null) {
                String ldJson = ldScript.html();
                Matcher m1 = Pattern.compile("\\\"thumbnailUrl\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(ldJson);
                if (m1.find()) vodPic = m1.group(1);
                Matcher m2 = Pattern.compile("\\\"description\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(ldJson);
                if (m2.find()) vodContent = m2.group(1).replace("&amp;nbsp;", " ").trim();
                Matcher m3 = Pattern.compile("\\\"uploadDate\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(ldJson);
                if (m3.find()) {
                    String date = m3.group(1);
                    if (date.length() >= 4) vodYear = date.substring(0, 4);
                }
            }

            String vodActor = "";
            String vodDirector = "";
            String vodClass = "";
            String vodArea = "";

            Element detailInfo = doc.selectFirst(".stui-content__detail");
            if (detailInfo != null) {
                Elements actorLinks = detailInfo.select("p.data a[href*=/zwhssc/]");
                List<String> actors = new ArrayList<>();
                for (Element a : actorLinks) {
                    String name = a.text().trim();
                    if (!name.isEmpty()) actors.add(name);
                }
                vodActor = String.join(",", actors);

                Element dataP = detailInfo.selectFirst("p.data");
                String dataText = dataP != null ? dataP.text() : "";

                Matcher md = Pattern.compile("导演[:：]\\s*([^\\n]+)").matcher(dataText);
                if (md.find()) vodDirector = md.group(1).trim();
                Matcher mc = Pattern.compile("类型[:：]\\s*([^\\n]+)").matcher(dataText);
                if (mc.find()) vodClass = mc.group(1).trim();
                Matcher ma = Pattern.compile("地区[:：]\\s*([^\\n]+)").matcher(dataText);
                if (ma.find()) vodArea = ma.group(1).trim();
            }

            List<String> froms = new ArrayList<>();
            List<String> urls = new ArrayList<>();

            Elements tabs = doc.select(".stui-content__playlist");
            Elements tabNames = doc.select(".stui-pannel__head h3.title");

            for (int i = 0; i < tabs.size(); i++) {
                String sourceName = (i < tabNames.size()) ? tabNames.get(i).text().trim() : ("源" + (i + 1));
                List<String> playLinks = new ArrayList<>();
                Elements links = tabs.get(i).select("li a[href]");
                for (Element a : links) {
                    String href = a.attr("href");
                    String epName = a.text().trim();
                    if (!href.isEmpty() && !epName.isEmpty()) {
                        playLinks.add(epName + "$" + abs(href));
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
            vod.put("vod_class", vodClass);
            vod.put("vod_area", vodArea);
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
        if (id == null || id.isEmpty()) {
            JSONObject r = new JSONObject();
            r.put("parse", 1);
            r.put("url", "http://127.0.0.1:9978/empty");
            return r.toString();
        }

        // 诊断：打印请求信息
        System.out.println("[YueGuang-DEBUG] playerContent start, id=" + id + ", flag=" + flag);

        String html = fetchWithClient(id, HOST + "/zwhsdt/1.html");

        // 诊断：打印返回内容长度和前200字符
        String preview = html != null ? (html.length() > 200 ? html.substring(0, 200) : html) : "null";
        System.out.println("[YueGuang-DEBUG] playerContent html len=" + (html != null ? html.length() : 0) + " preview=" + preview.replace("\n", " "));

        // 检查是否被拦截
        if (!isValidHtml(html)) {
            System.out.println("[YueGuang-DEBUG] playerContent INVALID html, returning empty");
            JSONObject r = new JSONObject();
            r.put("parse", 0);
            r.put("url", "");
            return r.toString();
        }

        // 尝试匹配 player_aaaa
        Matcher mp = Pattern.compile("var\\s+player_\\w+\\s*=\\s*(\\{.*?\\})\\s*</script>", Pattern.DOTALL).matcher(html);
        boolean found = mp.find();
        System.out.println("[YueGuang-DEBUG] playerContent primary regex found=" + found);

        if (!found) {
            // 备用匹配
            mp = Pattern.compile("var\\s+player_\\w+\\s*=\\s*(\\{.*?\\})(?:;|\\s*<|\\s*$)", Pattern.DOTALL).matcher(html);
            found = mp.find();
            System.out.println("[YueGuang-DEBUG] playerContent fallback regex found=" + found);
        }

        if (!found) {
            System.out.println("[YueGuang-DEBUG] playerContent NO MATCH, returning empty");
            JSONObject r = new JSONObject();
            r.put("parse", 0);
            r.put("url", "");
            return r.toString();
        }

        String playerStr = mp.group(1);
        System.out.println("[YueGuang-DEBUG] playerContent matched=" + playerStr.substring(0, Math.min(200, playerStr.length())));

        Matcher mu = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(playerStr);
        String mediaUrl = mu.find() ? mu.group(1) : "";
        Matcher me = Pattern.compile("\\\"encrypt\\\"\\s*:\\s*(\\d+)").matcher(playerStr);
        int encrypt = me.find() ? Integer.parseInt(me.group(1)) : 0;

        System.out.println("[YueGuang-DEBUG] playerContent raw url=" + mediaUrl + ", encrypt=" + encrypt);

        if (encrypt == 1 && !mediaUrl.isEmpty()) {
            mediaUrl = java.net.URLDecoder.decode(mediaUrl, "UTF-8");
        }

        // 处理JSON转义斜杠
        mediaUrl = mediaUrl.replace("\\/", "/");

        System.out.println("[YueGuang-DEBUG] playerContent final url=" + mediaUrl);

        boolean isM3u8 = mediaUrl.contains(".m3u8");
        boolean isMp4 = mediaUrl.contains(".mp4");
        int parse = (isM3u8 || isMp4) ? 0 : 1;

        JSONObject result = new JSONObject();
        result.put("parse", parse);
        result.put("url", mediaUrl);

        HashMap<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", id);
        result.put("header", new JSONObject(header).toString());

        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String encodedKey = URLEncoder.encode(key, "UTF-8");
        String url = HOST + "/zwhssc/" + encodedKey + "-------------.html";

        String html = fetchWithClient(url, HOST + "/");
        Document doc = Jsoup.parse(html);
        JSONArray list = parseVodList(doc);

        boolean hasNext = doc.select(".stui-page, .page").size() > 0;

        JSONObject result = new JSONObject();
        result.put("page", 1);
        result.put("pagecount", hasNext ? 2 : 1);
        result.put("limit", 24);
        result.put("total", list.length());
        result.put("list", list);

        return result.toString();
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        String url = params.get("url");
        if (url == null || url.isEmpty()) {
            return new Object[]{404, "text/plain", new byte[0]};
        }

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", Util.CHROME)
            .header("Referer", HOST)
            .build();

        try (Response response = client().newCall(request).execute()) {
            byte[] body = response.body() != null ? response.body().bytes() : new byte[0];
            String contentType = response.header("Content-Type", "application/octet-stream");
            return new Object[]{response.code(), contentType, body};
        }
    }

    @Override
    public String action(String action) throws Exception {
        if ("clearCookie".equals(action)) {
            cookieStore.clear();
            return "{" + "\"code\":200,\"msg\":\"Cookie已清除\"" + "}";
        }
        if ("getCookieCount".equals(action)) {
            return "{" + "\"code\":200,\"count\":" + cookieStore.size() + "}";
        }
        return null;
    }

    private JSONArray parseVodList(Document doc) throws Exception {
        JSONArray list = new JSONArray();
        Elements items = doc.select(".stui-vodlist__thumb");

        if (items.isEmpty()) {
            items = doc.select(".fed-list-pics, .myui-vodlist__thumb, .module-poster-item");
        }
        if (items.isEmpty()) {
            items = doc.select("a[href*=/zwhsdt/]");
        }

        for (Element item : items) {
            String href = item.attr("href");
            String title = item.attr("title");
            String img = item.attr("data-original");
            if (img.isEmpty()) img = item.attr("data-src");
            if (img.isEmpty()) {
                Element imgEl = item.selectFirst("img");
                if (imgEl != null) {
                    img = imgEl.attr("data-original");
                    if (img.isEmpty()) img = imgEl.attr("src");
                }
            }
            Element noteEl = item.selectFirst(".pic-text, .fed-list-remarks, .module-item-note");
            String note = noteEl != null ? noteEl.text().trim() : "";

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
