package com.RobinNotBad.BiliClient.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * DASH格式数据
 */
public class DashData {
    public int duration; // 视频长度（秒）
    public double minBufferTime; // 最小缓冲时间
    public List<DashVideoStream> videoStreams; // 视频流列表
    public List<DashAudioStream> audioStreams; // 音频流列表
    public DashAudioStream dolbyAudio; // 杜比全景声音频
    public DashAudioStream flacAudio; // 无损音轨音频

    public DashData() {
        videoStreams = new ArrayList<>();
        audioStreams = new ArrayList<>();
    }

    /**
     * 从JSON对象解析DASH数据
     */
    public static DashData fromJson(JSONObject json) throws JSONException {
        DashData dashData = new DashData();
        dashData.duration = json.optInt("duration", 0);
        dashData.minBufferTime = json.optDouble("minBufferTime",
                json.optDouble("min_buffer_time", 1.5));

        // 解析视频流
        dashData.videoStreams = DashVideoStream.fromJsonArray(json.optJSONArray("video"));

        // 解析音频流
        dashData.audioStreams = DashAudioStream.fromJsonArray(json.optJSONArray("audio"));

        // 解析杜比全景声
        JSONObject dolbyObj = json.optJSONObject("dolby");
        if (dolbyObj != null && dolbyObj.optInt("type", 0) > 0) {
            JSONObject dolbyAudioObj = dolbyObj.optJSONObject("audio");
            if (dolbyAudioObj == null && dolbyObj.has("audio") && !dolbyObj.isNull("audio")) {
                // audio可能是数组，取第一个
                dolbyAudioObj = dolbyObj.optJSONArray("audio").optJSONObject(0);
            }
            if (dolbyAudioObj != null) {
                dashData.dolbyAudio = DashAudioStream.fromJson(dolbyAudioObj);
            }
        }

        // 解析无损音轨
        JSONObject flacObj = json.optJSONObject("flac");
        if (flacObj != null && flacObj.optBoolean("display", false)) {
            JSONObject flacAudioObj = flacObj.optJSONObject("audio");
            if (flacAudioObj != null) {
                dashData.flacAudio = DashAudioStream.fromJson(flacAudioObj);
            }
        }

        return dashData;
    }

    /**
     * 获取指定清晰度的视频流
     */
    public DashVideoStream getVideoStream(int qn) {
        for (DashVideoStream stream : videoStreams) {
            if (stream.id == qn) {
                return stream;
            }
        }
        // 如果找不到，返回第一个（最高清晰度）
        return videoStreams.isEmpty() ? null : videoStreams.get(0);
    }

    /**
     * 获取指定清晰度和编码的视频流
     * @param qn 清晰度
     * @param codec 编码类型：0=自动, 1=H264, 2=H265(HEVC), 3=AV1
     * @return 匹配的视频流
     */
    public DashVideoStream getVideoStream(int qn, int codec) {
        if (codec == 0) {
            return getVideoStream(qn);
        }

        String targetCodec = getCodecString(codec);
        if (targetCodec == null) {
            return getVideoStream(qn);
        }

        // 先找匹配清晰度和编码的
        for (DashVideoStream stream : videoStreams) {
            if (stream.id == qn && stream.codecs != null && stream.codecs.contains(targetCodec)) {
                return stream;
            }
        }

        // 找不到匹配的编码，找匹配清晰度的任意编码
        for (DashVideoStream stream : videoStreams) {
            if (stream.id == qn) {
                return stream;
            }
        }

        // 找不到匹配清晰度，返回第一个匹配编码的
        for (DashVideoStream stream : videoStreams) {
            if (stream.codecs != null && stream.codecs.contains(targetCodec)) {
                return stream;
            }
        }

        // 都没有，返回第一个
        return videoStreams.isEmpty() ? null : videoStreams.get(0);
    }

    /**
     * 获取编码字符串
     * @param codec 编码类型：0=自动, 1=H264, 2=H265(HEVC), 3=AV1
     * @return 编码字符串
     */
    private String getCodecString(int codec) {
        switch (codec) {
            case 1: return "avc1"; // H264
            case 2: return "hev1"; // H265/HEVC
            case 3: return "av01"; // AV1
            default: return null;
        }
    }

    /**
     * 获取最高质量的音频流
     */
    public DashAudioStream getBestAudioStream() {
        // 优先返回无损音轨
        if (flacAudio != null) {
            return flacAudio;
        }
        // 其次返回杜比全景声
        if (dolbyAudio != null) {
            return dolbyAudio;
        }
        // 返回最高码率的普通音频流
        if (!audioStreams.isEmpty()) {
            DashAudioStream best = audioStreams.get(0);
            for (DashAudioStream stream : audioStreams) {
                if (stream.bandwidth > best.bandwidth) {
                    best = stream;
                }
            }
            return best;
        }
        return null;
    }

    /**
     * 是否有有效的音频流
     */
    public boolean hasAudio() {
        return !audioStreams.isEmpty() || dolbyAudio != null || flacAudio != null;
    }

    /**
     * 是否有有效的视频流
     */
    public boolean hasVideo() {
        return !videoStreams.isEmpty();
    }
}
