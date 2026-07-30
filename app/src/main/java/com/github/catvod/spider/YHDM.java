package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class YHDM extends AowuSpider {

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<String> typeIds = Arrays.asList("guochandongman", "ribendongman", "dongmandianying", "omeidongman");
        List<String> typeNames = Arrays.asList("国产动漫", "日本动漫", "动漫电影", "欧美动漫");
        for (int i = 0; i < typeIds.size(); i++) classes.add(new Class(typeIds.get(i), typeNames.get(i)));

        String html = fetch(getActiveSite());
        Document doc = Jsoup.parse(html);
        List<Vod> list = new ArrayList<>();
        for (Element li : doc.select(".stui-vodlist.clearfix .myui-vodlist__box")) {
            String vid = li.select("a").attr("href");
            String name = li.select("a").attr("title");
            String pic = li.select("a").attr("data-original");
            if (!pic.startsWith("http")) pic = getActiveSite() + pic;
            String remark = li.select(".pic-text.text-right").text();
            list.add(new Vod(vid, name, pic, remark));
        }
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String cateUrl = getActiveSite() + String.format("/type/%s-%s.html", tid, pg);
        Document doc = Jsoup.parse(fetch(cateUrl));
        List<Vod> list = new ArrayList<>();
        for (Element li : doc.select(".myui-vodlist__box")) {
            String vid = li.select("a").attr("href");
            String name = li.select("a").attr("title");
            String pic = li.select("a").attr("data-original");
            if (!pic.startsWith("http")) pic = getActiveSite() + pic;
            String remark = li.select(".pic-text.text-right").text();
            list.add(new Vod(vid, name, pic, remark));
        }
        int page = Integer.parseInt(pg);
        boolean hasNext = doc.select(".myui-page .visible-xs").size() > 0;
        return Result.get().vod(list).page(page, hasNext ? page + 1 : page, 24, list.size()).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String detailUrl = getActiveSite() + ids.get(0);
        Document doc = Jsoup.parse(fetch(detailUrl));
        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(doc.selectFirst(".myui-content__detail .title").text());
        vod.setVodPic(doc.selectFirst(".myui-content__thumb img").attr("data-original"));
        vod.setTypeName(doc.select(".myui-content__detail p").get(0).text().replace("类型：", ""));
        vod.setVodYear(doc.select(".myui-content__detail p").get(1).text().replace("年份：", ""));
        vod.setVodArea(doc.select(".myui-content__detail p").get(2).text().replace("地区：", ""));
        vod.setVodContent(doc.selectFirst(".desc .content") != null ? doc.selectFirst(".desc .content").text() : "");

        List<String> playFroms = new ArrayList<>();
        List<String> playUrls = new ArrayList<>();
        for (Element tab : doc.select(".nav-tabs li")) {
            playFroms.add(tab.select("a").text());
        }
        for (Element ul : doc.select(".myui-content__list")) {
            List<String> urls = new ArrayList<>();
            for (Element a : ul.select("a")) {
                urls.add(a.text() + "$" + a.attr("href"));
            }
            playUrls.add(String.join("#", urls));
        }
        vod.setVodPlayFrom(String.join("$$$", playFroms));
        vod.setVodPlayUrl(String.join("$$$", playUrls));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playerUrl = getActiveSite() + id;
        Document doc = Jsoup.parse(fetch(playerUrl));
        String url = doc.selectFirst("iframe").attr("src");
        if (!url.startsWith("http")) url = "https:" + url;
        return Result.get().url(url).parse(1).header(new HashMap<String, String>() {{ put("Referer", playerUrl); }}).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String searchUrl = getActiveSite() + "/search/" + key + ".html";
        Document doc = Jsoup.parse(fetch(searchUrl));
        List<Vod> list = new ArrayList<>();
        for (Element li : doc.select(".myui-vodlist__box")) {
            String vid = li.select("a").attr("href");
            String name = li.select("a").attr("title");
            String pic = li.select("a").attr("data-original");
            if (!pic.startsWith("http")) pic = getActiveSite() + pic;
            String remark = li.select(".pic-text.text-right").text();
            list.add(new Vod(vid, name, pic, remark));
        }
        return Result.get().vod(list).page(1, 1, 24, list.size()).string();
    }
}
