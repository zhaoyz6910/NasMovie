package com.example.nasmovie.data.parser;

import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字幕文件解析器
 * 支持SRT和ASS格式
 */
public class SubtitleParser {

    private static final String TAG = "SubtitleParser";

    // SRT时间格式正则表达式
    private static final Pattern SRT_TIME_PATTERN = Pattern.compile(
        "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})"
    );

    /**
     * 字幕条目
     */
    public static class SubtitleEntry {
        public long startTimeMs;
        public long endTimeMs;
        public String text;

        public SubtitleEntry(long startTimeMs, long endTimeMs, String text) {
            this.startTimeMs = startTimeMs;
            this.endTimeMs = endTimeMs;
            this.text = text;
        }
    }

    /**
     * 字幕数据
     */
    public static class SubtitleData {
        public List<SubtitleEntry> entries;
        public String format;

        public SubtitleData() {
            entries = new ArrayList<>();
        }
    }

    /**
     * 解析字幕文件
     */
    public static SubtitleData parse(byte[] data, String extension) {
        if (data == null || data.length == 0) {
            return null;
        }

        String ext = extension.toLowerCase();

        // 检测编码
        Charset charset = detectCharset(data);
        String content = new String(data, charset);

        try {
            if ("srt".equals(ext)) {
                return parseSrt(content);
            } else if ("ass".equals(ext) || "ssa".equals(ext)) {
                return parseAss(content);
            } else if ("vtt".equals(ext)) {
                return parseVtt(content);
            } else {
                // 默认尝试SRT格式
                return parseSrt(content);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing subtitle: " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析SRT格式字幕
     */
    public static SubtitleData parseSrt(String content) {
        SubtitleData subtitleData = new SubtitleData();
        subtitleData.format = "srt";

        String[] lines = content.split("\\r?\\n");
        int i = 0;

        while (i < lines.length) {
            String line = lines[i].trim();

            // 跳过序号行
            if (isNumeric(line)) {
                i++;
                if (i >= lines.length) break;
                line = lines[i].trim();
            }

            // 解析时间轴
            Matcher matcher = SRT_TIME_PATTERN.matcher(line);
            if (matcher.matches()) {
                long startTime = parseTime(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
                long endTime = parseTime(matcher.group(5), matcher.group(6), matcher.group(7), matcher.group(8));

                // 读取字幕文本
                StringBuilder textBuilder = new StringBuilder();
                i++;
                while (i < lines.length) {
                    String textLine = lines[i].trim();
                    if (textLine.isEmpty() || SRT_TIME_PATTERN.matcher(textLine).matches() || isNumeric(textLine)) {
                        break;
                    }
                    if (textBuilder.length() > 0) {
                        textBuilder.append("\n");
                    }
                    textBuilder.append(textLine);
                    i++;
                }

                if (textBuilder.length() > 0) {
                    subtitleData.entries.add(new SubtitleEntry(startTime, endTime, textBuilder.toString()));
                }
                continue;
            }

            i++;
        }

        return subtitleData;
    }

    /**
     * 解析ASS/SSA格式字幕
     */
    public static SubtitleData parseAss(String content) {
        SubtitleData subtitleData = new SubtitleData();
        subtitleData.format = "ass";

        String[] lines = content.split("\\r?\\n");
        boolean inEvents = false;
        int timeScale = 10; // ASS时间精度(毫秒)

        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("[Events]")) {
                inEvents = true;
                continue;
            }

            if (line.startsWith("[") && line.endsWith("]")) {
                inEvents = false;
                continue;
            }

            if (inEvents && line.startsWith("Dialogue:")) {
                // 解析对话行
                // 格式: Dialogue: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text
                String[] parts = line.substring(9).split(",", 10);
                if (parts.length >= 10) {
                    try {
                        long startTime = parseAssTime(parts[1].trim());
                        long endTime = parseAssTime(parts[2].trim());
                        String text = cleanAssText(parts[9]);

                        if (!TextUtils.isEmpty(text)) {
                            subtitleData.entries.add(new SubtitleEntry(startTime, endTime, text));
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error parsing ASS line: " + line);
                    }
                }
            }
        }

        return subtitleData;
    }

    /**
     * 解析WebVTT格式字幕
     */
    public static SubtitleData parseVtt(String content) {
        SubtitleData subtitleData = new SubtitleData();
        subtitleData.format = "vtt";

        String[] lines = content.split("\\r?\\n");
        int i = 0;

        // 跳过WEBVTT头部
        while (i < lines.length && !lines[i].contains("-->")) {
            i++;
        }

        while (i < lines.length) {
            String line = lines[i].trim();

            // 解析时间轴 (格式: 00:00:00.000 --> 00:00:00.000)
            if (line.contains("-->")) {
                String[] times = line.split("-->");
                if (times.length == 2) {
                    long startTime = parseVttTime(times[0].trim());
                    long endTime = parseVttTime(times[1].trim());

                    // 读取字幕文本
                    StringBuilder textBuilder = new StringBuilder();
                    i++;
                    while (i < lines.length) {
                        String textLine = lines[i].trim();
                        if (textLine.isEmpty() || textLine.contains("-->")) {
                            break;
                        }
                        if (textBuilder.length() > 0) {
                            textBuilder.append("\n");
                        }
                        // 移除VTT标签
                        textLine = textLine.replaceAll("<[^>]+>", "");
                        textBuilder.append(textLine);
                        i++;
                    }

                    if (textBuilder.length() > 0) {
                        subtitleData.entries.add(new SubtitleEntry(startTime, endTime, textBuilder.toString()));
                    }
                    continue;
                }
            }

            i++;
        }

        return subtitleData;
    }

    /**
     * 解析SRT时间 (HH:MM:SS,mmm)
     */
    private static long parseTime(String hours, String minutes, String seconds, String millis) {
        return Long.parseLong(hours) * 3600000
            + Long.parseLong(minutes) * 60000
            + Long.parseLong(seconds) * 1000
            + Long.parseLong(millis);
    }

    /**
     * 解析ASS时间 (H:MM:SS.mm)
     */
    private static long parseAssTime(String time) {
        String[] parts = time.split("[:.]");
        if (parts.length >= 3) {
            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            long seconds = Long.parseLong(parts[2]);
            long centis = parts.length > 3 ? Long.parseLong(parts[3]) : 0;

            return hours * 3600000 + minutes * 60000 + seconds * 1000 + centis * 10;
        }
        return 0;
    }

    /**
     * 解析VTT时间 (HH:MM:SS.mmm 或 MM:SS.mmm)
     */
    private static long parseVttTime(String time) {
        time = time.trim();
        String[] mainParts = time.split("\\.");
        long millis = 0;
        if (mainParts.length > 1) {
            millis = Long.parseLong(mainParts[1]);
        }

        String[] timeParts = mainParts[0].split(":");
        if (timeParts.length == 3) {
            return Long.parseLong(timeParts[0]) * 3600000
                + Long.parseLong(timeParts[1]) * 60000
                + Long.parseLong(timeParts[2]) * 1000
                + millis;
        } else if (timeParts.length == 2) {
            return Long.parseLong(timeParts[0]) * 60000
                + Long.parseLong(timeParts[1]) * 1000
                + millis;
        }
        return 0;
    }

    /**
     * 清理ASS字幕文本
     */
    private static String cleanAssText(String text) {
        // 移除ASS标签
        text = text.replaceAll("\\{[^}]*\\}", "");
        text = text.replaceAll("\\\\N", "\n");
        text = text.replaceAll("\\\\n", "\n");
        text = text.replaceAll("\\\\h", " ");
        return text.trim();
    }

    /**
     * 检测字符编码
     */
    private static Charset detectCharset(byte[] data) {
        // 检查BOM
        if (data.length >= 3 && data[0] == (byte) 0xEF && data[1] == (byte) 0xBB && data[2] == (byte) 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (data.length >= 2 && data[0] == (byte) 0xFF && data[1] == (byte) 0xFE) {
            return Charset.forName("UTF-16LE");
        }
        if (data.length >= 2 && data[0] == (byte) 0xFE && data[1] == (byte) 0xFF) {
            return Charset.forName("UTF-16BE");
        }

        // 默认使用UTF-8
        return StandardCharsets.UTF_8;
    }

    /**
     * 检查字符串是否为数字
     */
    private static boolean isNumeric(String str) {
        if (TextUtils.isEmpty(str)) {
            return false;
        }
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }
}