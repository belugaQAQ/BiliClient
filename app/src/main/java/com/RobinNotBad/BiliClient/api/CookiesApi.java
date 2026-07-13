package com.RobinNotBad.BiliClient.api;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.WindowManager;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.util.Cookies;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cookies相关API
 */
public class CookiesApi {

    public static ArrayList<String> genWebHeaders() {
        return new ArrayList<>() {{
            addAll(NetWorkUtil.webHeaders);

            add("Sec-Fetch-Site");
            add("same-site");

            add("Sec-Fetch-Mode");
            add("cors");

            add("Sec-Fetch-Dest");
            add("empty");
        }};
    }

    /**
     * 调用ExClimbWuzhi API激活Cookies，并检查如果有一些可本地生成的Cookie没有就顺带生成一下
     * 注：payload是我瞎jb弄得
     *
     * @return 返回码
     */
    public static int activeCookieInfo() throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/internal/gaia-gateway/ExClimbWuzhi";
        //NetWorkUtil.postJson(url, genCookiePayload().toString(), genWebHeaders());    //b站自己请求两次，所以我也请求两次（？）
        return new JSONObject(Objects.requireNonNull(NetWorkUtil.postJson(url, genCookiePayload(), genWebHeaders()).body()).string()).getInt("code");
    }

    /*
     * ExClimbWuzhi 激活 payload，参考 PiliPlus (https://github.com/bggRGjQaUbCoE/PiliPlus) 的实现。
     * 相比旧版（333.1007.fp.risk + 巨大的 webgl/字体/插件指纹），这里改用更精简且经 PiliPlus 验证可用的
     * 333.1387.fp.risk 版本，bfe9 为伪造 PNG IEND 结尾的 base64 末尾 50 个字符。
     * 外层结构为 {"payload": "<内层 json 字符串>"}，与 B 站 web 端 / PiliPlus 一致。
     */
    public static String genCookiePayload() throws JSONException {
        SecureRandom random = new SecureRandom();
        // 32 个随机字节 + IEND 标记 (00 00 00 00 49 45 4E 44) + 4 个随机字节
        byte[] pngBytes = new byte[32 + 8 + 4];
        random.nextBytes(pngBytes);
        pngBytes[32] = 0; pngBytes[33] = 0; pngBytes[34] = 0; pngBytes[35] = 0;
        pngBytes[36] = 73;  // 'I'
        pngBytes[37] = 69;  // 'E'
        pngBytes[38] = 78;  // 'N'
        pngBytes[39] = 68;  // 'D'
        String bfe9 = Base64.encodeToString(pngBytes, Base64.NO_WRAP);
        bfe9 = bfe9.substring(bfe9.length() - 50);

        JSONObject inner = new JSONObject();
        inner.put("3064", 1);
        inner.put("39c8", "333.1387.fp.risk");
        JSONObject c343 = new JSONObject();
        c343.put("adca", "Linux");
        c343.put("bfe9", bfe9);
        inner.put("3c43", c343);

        JSONObject outer = new JSONObject();
        outer.put("payload", inner.toString());
        return outer.toString();
    }

    /**
     * 仅获取buvid3
     *
     * @return buvid3
     */
    public static String getBuvid3Only() throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/web-frontend/getbuvid";
        JSONObject data = NetWorkUtil.getJson(url, genWebHeaders());
        return data.optString("buvid", "");
    }

    /**
     * 获取buvid3、buvid4，可能需要在上面先获取单个buvid3
     *
     * @return buvid3、buvid4
     */
    public static Pair<String, String> getWebBuvids() throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/frontend/finger/spi";
        JSONObject data = NetWorkUtil.getJson(url, genWebHeaders()).getJSONObject("data");
        return new Pair<>(data.optString("b_3"), data.optString("b_4"));
    }

    /**
     * 生成bili_ticket
     *
     * @return bili_ticket and create time
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws JSONException
     */
    public static Pair<String, Integer> genBiliTicket() throws IOException, NoSuchAlgorithmException, InvalidKeyException, JSONException {
        long ts = System.currentTimeMillis() / 1000;
        String o = hmacSha256("XgwSnGZ1p", "ts" + ts);
        String url = "https://api.bilibili.com/bapis/bilibili.api.ticket.v1.Ticket/GenWebTicket";
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.postJson(url + new NetWorkUtil.FormData()
                        .setUrlParam(true)
                        .put("key_id", "ec02")
                        .put("hexsign", o)
                        .put("context[ts]", String.valueOf(ts))
                        .put("csrf", SharedPreferencesUtil.getString("csrf", "")),
                "", genWebHeaders()).body()).string());
        if (result.has("data") && !result.isNull("data")) {
            JSONObject data = result.getJSONObject("data");
            return new Pair<>(data.optString("ticket"), data.optInt("created_at"));
        } else {
            return new Pair<>(null, -1);
        }
    }

    public static String hmacSha256(String key, String message)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] hashBytes = sha256Hmac.doFinal(message.getBytes());
        StringBuilder hexHash = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexHash.append('0');
            hexHash.append(hex);
        }
        return hexHash.toString();
    }

    private static final Map<String, String> otherCookieMap = new HashMap<>() {{
        put("enable_web_push", "DISABLE");
        put("header_theme_version", "undefined");
        put("home_feed_column", "4");
        put("PVID", "1");
    }};

    public static void checkCookies() throws JSONException, IOException {
        NetWorkUtil.get("https://www.bilibili.com/");

        Cookies cookies = NetWorkUtil.getCookies();

        // _uuid
        if (!cookies.containsKey("_uuid")) {
            NetWorkUtil.putCookie("_uuid", gen_uuid_infoc());
        }

        // b_nut
        if (!cookies.containsKey("b_nut")) {
            NetWorkUtil.putCookie("b_nut", gen_b_nut());
        }

        // b_lsid
        if (!cookies.containsKey("b_lsid")) {
            NetWorkUtil.putCookie("b_lsid", gen_b_lsid());
        }

        // buvid3 根据浏览器端的网络请求顺序，B站自己是先获取单个buvid3的，虽然我并不知道为什么要这样做…？
        if (!cookies.containsKey("buvid3")) {
            String buvid3 = getBuvid3Only();
            NetWorkUtil.putCookie("buvid3", buvid3);
        }

        // buvid3 & buvid4. Get from http API.
        if (!cookies.containsKey("buvid4")) {
            Pair<String, String> buvids = getWebBuvids();
            NetWorkUtil.putCookie("buvid3", buvids.first);
            NetWorkUtil.putCookie("buvid4", buvids.second);
        }

        // LIVE_BUVID
        if (!cookies.containsKey("LIVE_BUVID")) {
            long min = 1000000000000000L;
            long max = 9999999999999999L;
            NetWorkUtil.putCookie("LIVE_BUVID", "AUTO" + (min + (long) (new Random().nextDouble() * (max - min))));
        }

        // browser_resolution
        if (!cookies.containsKey("browser_resolution")) {
            Pair<Integer, Integer> resolution = gen_browser_resolution();
            NetWorkUtil.putCookie("browser_resolution", resolution.first + "-" + resolution.second);
        }

        // bili_ticket
        if (!cookies.containsKey("bili_ticket") || cookies.get("bili_ticket").equals("null") || !cookies.containsKey("bili_ticket_expires") || parseInt(cookies.get("bili_ticket_expires")) == null || parseInt(cookies.get("bili_ticket_expires")) < System.currentTimeMillis() / 1000) {
            try {
                Pair<String, Integer> bili_ticket = genBiliTicket();
                NetWorkUtil.putCookie("bili_ticket", bili_ticket.first);
                NetWorkUtil.putCookie("bili_ticket_expires", String.valueOf(bili_ticket.second + (3 * 24 * 60 * 60)));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } catch (InvalidKeyException e) {
                throw new RuntimeException(e);
            }
        }

        // buvid_fp
        if (!cookies.containsKey("buvid_fp")) {
            NetWorkUtil.putCookie("buvid_fp", gen_buvid_fp(NetWorkUtil.USER_AGENT_WEB + System.currentTimeMillis(), 31));
        }

        // Others
        for (Map.Entry<String, String> entry : otherCookieMap.entrySet()) {
            if (!cookies.containsKey(entry.getKey())) {
                NetWorkUtil.putCookie(entry.getKey(), entry.getValue());
            }
        }

        // 激活 buvid3 指纹（ExClimbWuzhi），参考 PiliPlus：每次启动都调用，否则 B 站风控会返回 -352
        // 激活为尽力而为，失败不应影响后续流程（与 PiliPlus 的 try/catch 一致）
        try {
            activeCookieInfo();
        } catch (Exception e) {
            Logu.e("activeCookieInfo: " + e.getMessage());
        }
    }

    private static Integer parseInt(String string) {
        try {
            return Integer.parseInt(string);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final String[] MP = {
            "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "A", "B", "C", "D", "E", "F", "10"
    };
    private static final int[] PCK = {8, 4, 4, 4, 12};
    private static final String CHARSET = "0123456789ABCDEF";

    private static String gen_b_lsid() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CHARSET.charAt(random.nextInt(CHARSET.length())));
        }
        String randomString = sb.toString();
        long currentTimeMillis = System.currentTimeMillis();
        return randomString + "_" + Long.toHexString(currentTimeMillis).toUpperCase();
    }

    @SuppressLint("DefaultLocale")
    private static String gen_uuid_infoc() {
        long t = System.currentTimeMillis() % 100000;
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int len : PCK) {
            for (int i = 0; i < len; i++) {
                sb.append(MP[random.nextInt(16)]);
            }
            sb.append("-");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(String.format("%05d", t)).append("infoc");
        return sb.toString();
    }

    private static String gen_b_nut() {
        long timestampInSeconds = System.currentTimeMillis() / 1000;
        return String.valueOf(timestampInSeconds);
    }

    private static Pair<Integer, Integer> gen_browser_resolution() {
        WindowManager windowManager = (WindowManager) BiliTerminal.context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        return new Pair<>(metrics.widthPixels, metrics.heightPixels);
    }

    private static final BigInteger MOD = BigInteger.ONE.shiftLeft(64);
    private static final BigInteger C1 = new BigInteger("87C37B91114253D5", 16);
    private static final BigInteger C2 = new BigInteger("4CF5AD432745937F", 16);
    private static final BigInteger C3 = BigInteger.valueOf(0x52DCE729L);
    private static final BigInteger C4 = BigInteger.valueOf(0x38495AB5L);
    private static final int R1 = 27;
    private static final int R2 = 31;
    private static final int R3 = 33;
    private static final int M = 5;

    public static String gen_buvid_fp(String key, long seed) throws IOException {
        InputStream source = new ByteArrayInputStream(key.getBytes("US-ASCII"));
        BigInteger m = murmur3_x64_128(source, BigInteger.valueOf(seed));
        return String.format("%016x%016x", m.mod(MOD), m.shiftRight(64).mod(MOD));
    }

    private static BigInteger rotateLeft(BigInteger x, int k) {
        return x.shiftLeft(k).or(x.shiftRight(64 - k)).mod(MOD);
    }

    private static BigInteger murmur3_x64_128(InputStream source, BigInteger seed) throws IOException {
        BigInteger h1 = seed;
        BigInteger h2 = seed;
        long processed = 0;
        byte[] buffer = new byte[16];
        while (true) {
            int bytesRead = source.read(buffer);
            processed += bytesRead;
            if (bytesRead == 16) {
                long k1 = ByteBuffer.wrap(buffer, 0, 8).getLong();
                long k2 = ByteBuffer.wrap(buffer, 8, 8).getLong();
                h1 = h1.xor(rotateLeft(BigInteger.valueOf(k1).multiply(C1).mod(MOD), R2).multiply(C2).mod(MOD));
                h1 = (rotateLeft(h1, R1).add(h2).multiply(BigInteger.valueOf(M)).add(C3)).mod(MOD);
                h2 = h2.xor(rotateLeft(BigInteger.valueOf(k2).multiply(C2).mod(MOD), R3).multiply(C1).mod(MOD));
                h2 = (rotateLeft(h2, R2).add(h1).multiply(BigInteger.valueOf(M)).add(C4)).mod(MOD);
            } else if (bytesRead == -1) {
                h1 = h1.xor(BigInteger.valueOf(processed));
                h2 = h2.xor(BigInteger.valueOf(processed));
                h1 = h1.add(h2).mod(MOD);
                h2 = h2.add(h1).mod(MOD);
                h1 = fmix64(h1);
                h2 = fmix64(h2);
                h1 = h1.add(h2).mod(MOD);
                h2 = h2.add(h1).mod(MOD);
                return h2.shiftLeft(64).or(h1);
            } else {
                long k1 = 0;
                long k2 = 0;
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead);
                if (bytesRead >= 15) {
                    k2 ^= (long) byteBuffer.get(14) << 48;
                }
                if (bytesRead >= 14) {
                    k2 ^= (long) byteBuffer.get(13) << 40;
                }
                if (bytesRead >= 13) {
                    k2 ^= (long) byteBuffer.get(12) << 32;
                }
                if (bytesRead >= 12) {
                    k2 ^= (long) byteBuffer.get(11) << 24;
                }
                if (bytesRead >= 11) {
                    k2 ^= (long) byteBuffer.get(10) << 16;
                }
                if (bytesRead >= 10) {
                    k2 ^= (long) byteBuffer.get(9) << 8;
                }
                if (bytesRead >= 9) {
                    k2 ^= byteBuffer.get(8);
                    h2 = h2.xor(rotateLeft(BigInteger.valueOf(k2).multiply(C2).mod(MOD), R3).multiply(C1).mod(MOD));
                }
                if (bytesRead >= 8) {
                    k1 ^= (long) byteBuffer.get(7) << 56;
                }
                if (bytesRead >= 7) {
                    k1 ^= (long) byteBuffer.get(6) << 48;
                }
                if (bytesRead >= 6) {
                    k1 ^= (long) byteBuffer.get(5) << 40;
                }
                if (bytesRead >= 5) {
                    k1 ^= (long) byteBuffer.get(4) << 32;
                }
                if (bytesRead >= 4) {
                    k1 ^= (long) byteBuffer.get(3) << 24;
                }
                if (bytesRead >= 3) {
                    k1 ^= (long) byteBuffer.get(2) << 16;
                }
                if (bytesRead >= 2) {
                    k1 ^= (long) byteBuffer.get(1) << 8;
                }
                if (bytesRead >= 1) {
                    k1 ^= byteBuffer.get(0);
                    h1 = h1.xor(rotateLeft(BigInteger.valueOf(k1).multiply(C1).mod(MOD), R2));
                }
            }
        }
    }

    private static BigInteger fmix64(BigInteger k) {
        final BigInteger C1 = new BigInteger("FF51AFD7ED558CCD", 16);
        final BigInteger C2 = new BigInteger("C4CEB9FE1A85EC53", 16);
        final int R = 33;
        k = k.xor(k.shiftRight(R)).multiply(C1).mod(MOD);
        k = k.xor(k.shiftRight(R)).multiply(C2).mod(MOD);
        k = k.xor(k.shiftRight(R)).mod(MOD);
        return k;
    }
}
