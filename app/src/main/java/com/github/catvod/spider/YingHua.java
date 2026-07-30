package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;

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

public class YingHua extends AowuSpider {

    private static final Pattern ID_PATTERN = Pattern.compile("/v/(\d+)\.html");

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
        String html = fetch(getActiveSite() + "/");
        Document doc = Jsoup.parse(html);
        return Result.string(parseVodList(doc)).string();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}

        String url = getActiveSite() + "/type/" + tid + "-" + page + ".html";
        String html = fetch(url);
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseVodList(doc);

        boolean hasNext = doc.select(".pagination a").size() > 0 || list.size() >= 24;
        return Result.get().vod(list).page(page, hasNext ? page + 1 : page, 24, list.size() > 0 ? page * 24 + 1 : 0).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        String id = ids.get(0);

        String detailUrl = getActiveSite() + "/v/" + id + ".html";
        String html = fetch(detailUrl);
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(id);

        Element h1 = doc.selectFirst("h1");
        if (h1 != null) vod.setVodName(h1.text().trim());

        String pic = "";
        Element img = doc.selectFirst(".detail-pic img");
        if (img != null) {
            pic = img.attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = img.attr("src");
        }
        vod.setVodPic(abs(pic));

        Elements ps = doc.select(".detail-info p");
        for (Element p : ps) {
            String text = p.text();
            if (text.contains("导演")) vod.setVodDirector(text.replace("导演：", "").trim());
            else if (text.contains("主演")) vod.setVodActor(text.replace("主演：", "").trim());
            else if (text.contains("类型")) vod.setTypeName(text.replace("类型：", "").trim());
            else if (text.contains("地区")) vod.setVodArea(text.replace("地区：", "").trim());
            else if (text.contains("年代") || text.contains("年份")) vod.setVodYear(text.replaceAll("年代：|年份：", "").trim());
        }

        Element desc = doc.selectFirst(".detail-content");
        if (desc != null) vod.setVodContent(desc.text().trim());

        List<String> playFroms = new ArrayList<>();
        List<String> playUrls = new ArrayList<>();

        Elements tabs = doc.select(".play-nav a");
        for (int i = 0; i < tabs.size(); i++) {
            playFroms.add(tabs.get(i).text().trim());
        }
        if (playFroms.isEmpty()) playFroms.add("默认线路");

        Elements playlists = doc.select(".play-list");
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

        String url = "";
        Element player = doc.selectFirst("#playiframe");
        if (player != null) url = player.attr("src");
        if (TextUtils.isEmpty(url)) {
            Element iframe = doc.selectFirst("iframe");
            if (iframe != null) url = iframe.attr("src");
        }

        HashMap<String, String> header = new HashMap<>();
        header.put("Referer", playUrl);
        header.put("Origin", getActiveSite());

        if (!TextUtils.isEmpty(url)) {
            if (url.contains(".m3u8") || url.contains(".mp4")) {
                return Result.get().url(url).parse(0).header(header).string();
            }
            return Result.get().url(abs(url)).parse(1).header(header).string();
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
        String url = getActiveSite() + "/search/" + encodedKey + "--" + page + ".html";

        String html = fetch(url);
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseVodList(doc);

        boolean hasNext = doc.select(".pagination a").size() > 0 || list.size() >= 24;
        return Result.get().vod(list).page(page, hasNext ? page + 1 : page, 24, list.size()).string();
    }

    private List<Vod> parseVodList(Document doc) {
        List<Vod> list = new ArrayList<>();
        HashSet<String> idSet = new HashSet<>();

        Elements items = doc.select(".vodlist-item, .myui-vodlist__box");
        for (Element item : items) {
            Element a = item.selectFirst("a");
            if (a == null) continue;

            String href = abs(a.attr("href"));
            Matcher m = ID_PATTERN.matcher(href);
            if (!m.find()) continue;
            String id = m.group(1);
            if (idSet.contains(id)) continue;

            String title = a.attr("title");
            String pic = a.attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = a.selectFirst("img") != null ? a.selectFirst("img").attr("src") : "";
            String remark = "";
            Element note = item.selectFirst(".pic-text, .item-note");
            if (note != null) remark = note.text().trim();

            idSet.add(id);
            list.add(new Vod(id, title, abs(pic), remark));
        }
        return list;
    }
}
