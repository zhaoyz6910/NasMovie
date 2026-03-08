package com.example.nasmovie.data.parser;

import android.util.Log;
import android.util.Xml;

import com.example.nasmovie.data.model.NfoMetadata;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * NFO文件解析器
 * 支持Kodi和Emby两种格式
 */
public class NfoParser {

    private static final String TAG = "NfoParser";

    // Kodi格式标签
    private static final String TAG_MOVIE = "movie";
    private static final String TAG_TITLE = "title";
    private static final String TAG_ORIGINALTITLE = "originaltitle";
    private static final String TAG_SORTTITLE = "sorttitle";
    private static final String TAG_PLOT = "plot";
    private static final String TAG_OUTLINE = "outline";
    private static final String TAG_TAGLINE = "tagline";
    private static final String TAG_YEAR = "year";
    private static final String TAG_PREMIERED = "premiered";
    private static final String TAG_RATING = "rating";
    private static final String TAG_VOTES = "votes";
    private static final String TAG_MPAA = "mpaa";
    private static final String TAG_RUNTIME = "runtime";
    private static final String TAG_DIRECTOR = "director";
    private static final String TAG_WRITER = "writer";
    private static final String TAG_ACTOR = "actor";
    private static final String TAG_NAME = "name";
    private static final String TAG_ROLE = "role";
    private static final String TAG_THUMB = "thumb";
    private static final String TAG_FANART = "fanart";
    private static final String TAG_TRAILER = "trailer";
    private static final String TAG_ID = "id";
    private static final String TAG_IMDB = "imdb";
    private static final String TAG_TMDBID = "tmdbid";
    private static final String TAG_GENRE = "genre";
    private static final String TAG_TAG = "tag";
    private static final String TAG_STUDIO = "studio";
    private static final String TAG_COUNTRY = "country";
    private static final String TAG_FILENAME = "filenameandpath";

    // Emby格式标签
    private static final String TAG_MOVIE_EMBY = "Movie";

    /**
     * 解析NFO文件内容
     */
    public static NfoMetadata parse(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        try {
            String content = new String(data, StandardCharsets.UTF_8);
            // 移除BOM标记
            if (content.startsWith("\uFEFF")) {
                content = content.substring(1);
            }
            InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            return parse(inputStream);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing NFO: " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析NFO文件输入流
     */
    public static NfoMetadata parse(InputStream inputStream) {
        NfoMetadata metadata = new NfoMetadata();

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(inputStream, StandardCharsets.UTF_8.name());

            int eventType = parser.getEventType();
            String currentTag = null;
            NfoMetadata.Actor currentActor = null;
            boolean inActor = false;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        currentTag = parser.getName();

                        // 检测格式
                        if (TAG_MOVIE.equals(currentTag) || TAG_MOVIE_EMBY.equals(currentTag)) {
                            // 开始解析电影
                        } else if (TAG_ACTOR.equals(currentTag)) {
                            inActor = true;
                            currentActor = new NfoMetadata.Actor();
                        }
                        break;

                    case XmlPullParser.TEXT:
                        String text = parser.getText();
                        if (text == null || text.trim().isEmpty()) {
                            break;
                        }
                        text = text.trim();

                        if (inActor && currentActor != null) {
                            parseActorField(currentTag, text, currentActor);
                        } else {
                            parseMovieField(currentTag, text, metadata);
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        String endTag = parser.getName();
                        if (TAG_ACTOR.equals(endTag)) {
                            inActor = false;
                            if (currentActor != null && currentActor.getName() != null) {
                                metadata.getActorList().add(currentActor);
                                metadata.getActors().add(currentActor.getName());
                            }
                            currentActor = null;
                        }
                        currentTag = null;
                        break;
                }
                eventType = parser.next();
            }

        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error parsing NFO: " + e.getMessage());
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignored) {}
        }

        return metadata;
    }

    private static void parseMovieField(String tag, String text, NfoMetadata metadata) {
        if (tag == null) return;

        switch (tag) {
            case TAG_TITLE:
                if (metadata.getTitle() == null) {
                    metadata.setTitle(text);
                }
                break;
            case TAG_ORIGINALTITLE:
                metadata.setOriginalTitle(text);
                break;
            case TAG_SORTTITLE:
                metadata.setSortTitle(text);
                break;
            case TAG_PLOT:
                metadata.setPlot(text);
                break;
            case TAG_OUTLINE:
                metadata.setOutline(text);
                break;
            case TAG_TAGLINE:
                metadata.setTagline(text);
                break;
            case TAG_YEAR:
                try {
                    metadata.setYear(Integer.parseInt(text));
                } catch (NumberFormatException e) {
                    // 尝试从日期格式提取年份
                    if (text.length() >= 4) {
                        try {
                            metadata.setYear(Integer.parseInt(text.substring(0, 4)));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                break;
            case TAG_PREMIERED:
                metadata.setPremiered(text);
                // 从日期提取年份
                if (metadata.getYear() == 0 && text.length() >= 4) {
                    try {
                        metadata.setYear(Integer.parseInt(text.substring(0, 4)));
                    } catch (NumberFormatException ignored) {}
                }
                break;
            case TAG_RATING:
                try {
                    metadata.setRating(Float.parseFloat(text));
                } catch (NumberFormatException ignored) {}
                break;
            case TAG_VOTES:
                try {
                    metadata.setVotes(Integer.parseInt(text.replaceAll("[^0-9]", "")));
                } catch (NumberFormatException ignored) {}
                break;
            case TAG_MPAA:
                metadata.setMpaa(text);
                break;
            case TAG_RUNTIME:
                try {
                    // 运行时间可能是分钟数或时间格式
                    if (text.contains(":")) {
                        String[] parts = text.split(":");
                        int hours = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
                        int minutes = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                        metadata.setRuntime(hours * 60 + minutes);
                    } else {
                        metadata.setRuntime(Integer.parseInt(text.replaceAll("[^0-9]", "")));
                    }
                } catch (NumberFormatException ignored) {}
                break;
            case TAG_DIRECTOR:
                if (metadata.getDirector() == null) {
                    metadata.setDirector(text);
                }
                metadata.getDirectors().add(text);
                break;
            case TAG_WRITER:
                metadata.getWriters().add(text);
                break;
            case TAG_THUMB:
                if (metadata.getThumb() == null) {
                    metadata.setThumb(text);
                }
                metadata.getThumbs().add(text);
                break;
            case TAG_FANART:
                metadata.setFanart(text);
                break;
            case TAG_TRAILER:
                metadata.setTrailer(text);
                break;
            case TAG_ID:
                if (metadata.getId() == null) {
                    metadata.setId(text);
                }
                break;
            case TAG_IMDB:
                metadata.setImdb(text);
                if (metadata.getId() == null) {
                    metadata.setId(text);
                }
                break;
            case TAG_TMDBID:
                metadata.setTmdbId(text);
                break;
            case TAG_GENRE:
                metadata.getGenres().add(text);
                break;
            case TAG_TAG:
                metadata.getTags().add(text);
                break;
            case TAG_STUDIO:
                metadata.getStudios().add(text);
                break;
            case TAG_COUNTRY:
                metadata.getCountries().add(text);
                break;
            case TAG_FILENAME:
                metadata.setFilename(text);
                break;
        }
    }

    private static void parseActorField(String tag, String text, NfoMetadata.Actor actor) {
        if (tag == null) return;

        switch (tag) {
            case TAG_NAME:
                actor.setName(text);
                break;
            case TAG_ROLE:
                actor.setRole(text);
                break;
            case TAG_THUMB:
                actor.setThumb(text);
                break;
        }
    }
}