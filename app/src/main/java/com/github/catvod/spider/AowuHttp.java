package com.github.catvod.spider;

import android.text.TextUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Dns;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 嗷呜级 HTTP 客户端（单例）
 *
 * 职责：独立管理所有 HTTP 连接，与业务完全解耦
 * 功能：CookieJar 会话 + 自定义 DNS + SSL 绕过 + 动态 UA
 * 所有 Spider 共享同一实例，Cookie/hosts 全局隔离
 */
public class AowuHttp {

    private static volatile AowuHttp instance;

    // ===== 全局 Cookie 存储（按域名隔离）=====
    public static final Map<String, List<Cookie>> COOKIE_STORE = new ConcurrentHashMap<>();

    // ===== 全局 hosts 映射（防 DNS 污染）=====
    public static final Map<String, List<String>> HOSTS_MAP = new ConcurrentHashMap<>();

    // ===== 动态 UA 池 =====
    private static final Random RANDOM = new Random();
    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Linux; Android 15; 2407FRK8EC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    };

    private final OkHttpClient client;

    private AowuHttp() {
        client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            // SSL 绕过
            .sslSocketFactory(createUnsafeSocketFactory(), new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            })
            .hostnameVerifier((hostname, session) -> true)
            // 自定义 DNS
            .dns(new Dns() {
                @Override
                public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                    List<String> ips = HOSTS_MAP.get(hostname);
                    if (ips != null && !ips.isEmpty()) {
                        List<InetAddress> addresses = new ArrayList<>();
                        for (String ip : ips) {
                            try { addresses.add(InetAddress.getByName(ip)); }
                            catch (Exception ignored) {}
                        }
                        if (!addresses.isEmpty()) return addresses;
                    }
                    return Dns.SYSTEM.lookup(hostname);
                }
            })
            // CookieJar
            .cookieJar(new CookieJar() {
                @Override
                public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                    if (cookies != null && !cookies.isEmpty()) {
                        COOKIE_STORE.put(url.host(), new ArrayList<>(cookies));
                    }
                }
                @Override
                public List<Cookie> loadForRequest(HttpUrl url) {
                    List<Cookie> cookies = COOKIE_STORE.get(url.host());
                    return cookies != null ? new ArrayList<>(cookies) : new ArrayList<>();
                }
            })
            .build();
    }

    public static AowuHttp get() {
        if (instance == null) {
            synchronized (AowuHttp.class) {
                if (instance == null) instance = new AowuHttp();
            }
        }
        return instance;
    }

    // ===== 动态请求头 =====
    public static HashMap<String, String> headers(String referer) {
        HashMap<String, String> h = new HashMap<>();
        h.put("User-Agent", USER_AGENTS[RANDOM.nextInt(USER_AGENTS.length)]);
        h.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        h.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        h.put("Accept-Encoding", "gzip, deflate, br");
        h.put("Connection", "keep-alive");
        h.put("Upgrade-Insecure-Requests", "1");
        h.put("Sec-Fetch-Dest", "document");
        h.put("Sec-Fetch-Mode", "navigate");
        h.put("Sec-Fetch-Site", "none");
        h.put("Sec-Fetch-User", "?1");
        h.put("Cache-Control", "max-age=0");
        if (!TextUtils.isEmpty(referer)) h.put("Referer", referer);
        return h;
    }

    // ===== 公共 API =====
    public static void addHost(String domain, String... ips) {
        HOSTS_MAP.put(domain, Arrays.asList(ips));
    }

    public static void injectCookie(String domain, String name, String value) {
        try {
            Cookie cookie = new Cookie.Builder().domain(domain).name(name).value(value).path("/").build();
            COOKIE_STORE.put(domain, Arrays.asList(cookie));
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static String getCookieString(String host) {
        List<Cookie> cookies = COOKIE_STORE.get(host);
        if (cookies == null || cookies.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Cookie c : cookies) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(c.name()).append("=").append(c.value());
        }
        return sb.toString();
    }

    // ===== HTTP 请求 =====
    public String get(String url, HashMap<String, String> extraHeaders) throws Exception {
        Request.Builder builder = new Request.Builder().url(url);
        for (Map.Entry<String, String> e : headers(null).entrySet()) builder.addHeader(e.getKey(), e.getValue());
        if (extraHeaders != null) for (Map.Entry<String, String> e : extraHeaders.entrySet()) builder.addHeader(e.getKey(), e.getValue());
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) throw new Exception("HTTP " + response.code());
            return response.body() != null ? response.body().string() : "";
        }
    }

    public String post(String url, HashMap<String, String> params, HashMap<String, String> extraHeaders) throws Exception {
        Request.Builder builder = new Request.Builder().url(url);
        for (Map.Entry<String, String> e : headers(null).entrySet()) builder.addHeader(e.getKey(), e.getValue());
        if (extraHeaders != null) for (Map.Entry<String, String> e : extraHeaders.entrySet()) builder.addHeader(e.getKey(), e.getValue());
        FormBody.Builder form = new FormBody.Builder();
        if (params != null) for (Map.Entry<String, String> e : params.entrySet()) form.add(e.getKey(), e.getValue());
        try (Response response = client.newCall(builder.post(form.build()).build()).execute()) {
            if (!response.isSuccessful()) throw new Exception("HTTP " + response.code());
            return response.body() != null ? response.body().string() : "";
        }
    }

    private static SSLSocketFactory createUnsafeSocketFactory() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{ new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) { throw new RuntimeException("SSL init failed", e); }
    }
}
