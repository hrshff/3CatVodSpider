package com.github.catvod.spider;

import android.text.TextUtils;

import java.util.Map;

/**
 * 嗷呜级本地代理服务
 *
 * 职责：处理 TVBox 本地代理请求（壁纸、弹幕、缓存）
 * 对应 api.json:
 *   wallpaper: http://127.0.0.1:9978/proxy?do=AowuWapper&mode=2
 *   danmaku: http://127.0.0.1:2525/danmu?name={name}&epid={episode}
 */
public class AowuProxy {

    /**
     * 主入口，由 AowuSpider.proxyLocal() 调用
     */
    public static String handle(Map<String, String> params) {
        String doParam = params.get("do");
        if ("AowuWapper".equals(doParam)) return wallpaper(params);
        if ("danmu".equals(doParam)) return danmaku(params);
        if ("cache".equals(doParam)) return cache(params);
        return null;
    }

    /** 壁纸服务 */
    private static String wallpaper(Map<String, String> params) {
        String mode = params.get("mode");
        if ("2".equals(mode)) {
            return "{\"urls\":[\"https://picsum.photos/1920/1080?random=1\",\"https://picsum.photos/1920/1080?random=2\"]}";
        }
        return "{\"url\":\"https://picsum.photos/1920/1080\"}";
    }

    /** 弹幕服务 */
    private static String danmaku(Map<String, String> params) {
        String name = params.get("name");
        String epid = params.get("epid");
        // 实际可从弹幕 API 获取，这里返回空数组
        return "[]";
    }

    /** 图片缓存代理（减少重复下载） */
    private static String cache(Map<String, String> params) {
        String url = params.get("url");
        if (TextUtils.isEmpty(url)) return null;
        return "{\"url\":\"" + url + "\"}";
    }
}
