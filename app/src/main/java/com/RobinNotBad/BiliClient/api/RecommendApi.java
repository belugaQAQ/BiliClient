package com.RobinNotBad.BiliClient.api;

import android.text.TextUtils;
import android.util.Log;

import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


//推荐API 自己写的
//#如果想要增/删/改内容，可以直接把url复制到浏览器里，把得到的一大串东西交给json解析软件就能缕清结构了，然后就是拆拆拆
//2023-07-12
//2023-12-09

public class RecommendApi {
    private static final long UNIQ_ID = (long) (new Random().nextDouble() * 1000000);

    public static void getRecommend(List<VideoCard> videoCardList) throws IOException, JSONException {
        String url = ("https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd");
        url += new NetWorkUtil.FormData().setUrlParam(true)
                .put("web_location", 1430650)
                .put("feed_version", "V8")
                .put("homepage_ver", 1)
                .put("uniq_id", UNIQ_ID)
                .put("screen_width", 1100)
                .put("screen_height", 2056);

        JSONObject result;
        int maxRetries = 2;
        for (int retry = 0; retry < maxRetries; retry++) {
            try {
                // 第一次失败后，清除缓存的 WBI 密钥并重新获取
                if (retry > 0) {
                    Log.d("BiliClient", "RecommendApi: 重试，清除 WBI 缓存");
                    SharedPreferencesUtil.putInt("last_wbi", 0);
                }
                
                String signedUrl = ConfInfoApi.signWBI(url);
                Log.d("BiliClient", "RecommendApi signed URL: " + signedUrl);
                result = NetWorkUtil.getJson(signedUrl);
                
                int code = result.optInt("code", -1);
                if (code == 0) {
                    break; // 成功
                }
                
                Log.e("BiliClient", "RecommendApi code=" + code + " msg=" + result.optString("message", ""));
                
                if (retry < maxRetries - 1) {
                    continue; // 重试
                }
                
                throw new JSONException("RecommendApi error code=" + code);
            } catch (JSONException e) {
                if (retry < maxRetries - 1) {
                    continue; // 重试
                }
                throw e;
            }
        }

        if (!result.has("data") || result.isNull("data")) {
            Log.e("BiliClient", "RecommendApi: no data in response");
            throw new JSONException("RecommendApi: no data");
        }

        JSONObject data = result.getJSONObject("data");  //推荐列表中的data项又是一个json，把它提出来

        if (!data.has("item") || data.isNull("item")) {
            Log.e("BiliClient", "RecommendApi: no item in data");
            throw new JSONException("RecommendApi: no item");
        }

        JSONArray item = data.getJSONArray("item");  //data里面的items是视频卡片列表，把它提出来

        for (int i = 0; i < item.length(); i++) {    //遍历所有的视频卡片
            JSONObject card = item.getJSONObject(i);
            String bvid = card.optString("bvid", "");
            if (TextUtils.isEmpty(bvid)) {
                Log.d("BiliClient", "RecommendApi getRecommend: isAd");
                continue;
            }
            String cover = card.optString("pic", "");
            String title = card.optString("title", "");
            String upName = card.optJSONObject("owner") != null ? card.getJSONObject("owner").optString("name", "") : "";
            int viewCount = card.optJSONObject("stat") != null ? card.getJSONObject("stat").optInt("view", 0) : 0;
            String view = StringUtil.toWan(viewCount) + "观看";    //播放量
            videoCardList.add(new VideoCard(title, upName, view, cover, 0, bvid));
        }
    }

    public static ArrayList<VideoCard> getRelated(long aid) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/web-interface/archive/related?aid=" + aid;

        JSONObject result = NetWorkUtil.getJson(url);  //得到一整个json

        ArrayList<VideoCard> videoList = new ArrayList<>();
        if (result.has("data") && !result.isNull("data")) {
            JSONArray data = result.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject card = data.getJSONObject(i);
                VideoCard videoCard = new VideoCard();
                videoCard.aid = card.getLong("aid");
                videoCard.view = StringUtil.toWan(card.getJSONObject("stat").getLong("view")) + "观看";
                videoCard.cover = card.getString("pic");
                videoCard.title = card.getString("title");
                videoCard.upName = card.getJSONObject("owner").getString("name");
                videoList.add(videoCard);
            }

        }

        return videoList;
    }

    public static void getPopular(List<VideoCard> videoCardList, int page) throws JSONException, IOException {
        //热门接口在携带Cookie时返回的数据的排行是个性化的

        String url = "https://api.bilibili.com/x/web-interface/popular?pn=" + page + "&ps=10";

        JSONObject result = NetWorkUtil.getJson(url);  //得到一整个json

        if (result.has("data") && !result.isNull("data")) {
            if (result.getJSONObject("data").has("list")) {
                JSONArray list = result.getJSONObject("data").getJSONArray("list");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject card = list.getJSONObject(i);
                    VideoCard videoCard = new VideoCard();
                    videoCard.aid = card.getLong("aid");
                    videoCard.cover = card.getString("pic");
                    videoCard.title = card.getString("title");
                    videoCard.upName = card.getJSONObject("owner").getString("name");
                    videoCard.view = StringUtil.toWan(card.getJSONObject("stat").getLong("view")) + "观看";
                    videoCardList.add(videoCard);
                }
            }
        }
    }

    public static void getPrecious(List<VideoCard> videoCardList, int page) throws JSONException, IOException {
        //热门接口在携带Cookie时返回的数据的排行是个性化的

        String url = "https://api.bilibili.com/x/web-interface/popular/precious?page=" + page + "&page_size=10";

        JSONObject result = NetWorkUtil.getJson(url);  //得到一整个json

        if (result.has("data") && !result.isNull("data")) {
            if (result.getJSONObject("data").has("list")) {
                JSONArray list = result.getJSONObject("data").getJSONArray("list");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject card = list.getJSONObject(i);
                    VideoCard videoCard = new VideoCard();
                    videoCard.aid = card.getLong("aid");
                    videoCard.cover = card.getString("pic");
                    videoCard.title = card.getString("title");
                    videoCard.upName = card.getJSONObject("owner").getString("name");
                    videoCard.view = StringUtil.toWan(card.getJSONObject("stat").getLong("view")) + "观看";
                    videoCardList.add(videoCard);
                }
            }
        }
    }
}
