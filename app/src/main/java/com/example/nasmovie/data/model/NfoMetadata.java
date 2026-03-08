package com.example.nasmovie.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * NFO元数据解析结果
 * 兼容Kodi和Emby两种格式
 */
public class NfoMetadata {

    private String title;           // 标题
    private String originalTitle;   // 原标题
    private String sortTitle;       // 排序标题
    private String plot;            // 简介
    private String outline;         // 简短简介
    private String tagline;         // 标语
    private int year;               // 年份
    private String premiered;       // 上映日期
    private float rating;           // 评分
    private int votes;              // 投票数
    private String mpaa;            // 分级
    private int runtime;            // 时长（分钟）
    private String director;        // 导演
    private List<String> directors; // 导演列表
    private List<String> writers;   // 编剧
    private List<String> actors;    // 演员
    private List<Actor> actorList;  // 演员详情列表
    private List<String> genres;    // 类型
    private List<String> tags;      // 标签
    private List<String> studios;   // 制片公司
    private List<String> countries; // 国家
    private String thumb;           // 海报URL
    private List<String> thumbs;    // 海报列表
    private String fanart;          // 背景图
    private String trailer;         // 预告片
    private String id;              // IMDb ID
    private String imdb;            // IMDb ID (Emby格式)
    private String tmdbId;          // TMDB ID
    private String filename;        // 文件名

    public NfoMetadata() {
        directors = new ArrayList<>();
        writers = new ArrayList<>();
        actors = new ArrayList<>();
        actorList = new ArrayList<>();
        genres = new ArrayList<>();
        tags = new ArrayList<>();
        studios = new ArrayList<>();
        countries = new ArrayList<>();
        thumbs = new ArrayList<>();
    }

    // Actor inner class
    public static class Actor {
        private String name;
        private String role;
        private String thumb;
        private String order;

        public Actor() {}

        public Actor(String name, String role) {
            this.name = name;
            this.role = role;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getThumb() {
            return thumb;
        }

        public void setThumb(String thumb) {
            this.thumb = thumb;
        }

        public String getOrder() {
            return order;
        }

        public void setOrder(String order) {
            this.order = order;
        }
    }

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOriginalTitle() {
        return originalTitle;
    }

    public void setOriginalTitle(String originalTitle) {
        this.originalTitle = originalTitle;
    }

    public String getSortTitle() {
        return sortTitle;
    }

    public void setSortTitle(String sortTitle) {
        this.sortTitle = sortTitle;
    }

    public String getPlot() {
        return plot;
    }

    public void setPlot(String plot) {
        this.plot = plot;
    }

    public String getOutline() {
        return outline;
    }

    public void setOutline(String outline) {
        this.outline = outline;
    }

    public String getTagline() {
        return tagline;
    }

    public void setTagline(String tagline) {
        this.tagline = tagline;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getPremiered() {
        return premiered;
    }

    public void setPremiered(String premiered) {
        this.premiered = premiered;
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public int getVotes() {
        return votes;
    }

    public void setVotes(int votes) {
        this.votes = votes;
    }

    public String getMpaa() {
        return mpaa;
    }

    public void setMpaa(String mpaa) {
        this.mpaa = mpaa;
    }

    public int getRuntime() {
        return runtime;
    }

    public void setRuntime(int runtime) {
        this.runtime = runtime;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public List<String> getDirectors() {
        return directors;
    }

    public void setDirectors(List<String> directors) {
        this.directors = directors;
    }

    public List<String> getWriters() {
        return writers;
    }

    public void setWriters(List<String> writers) {
        this.writers = writers;
    }

    public List<String> getActors() {
        return actors;
    }

    public void setActors(List<String> actors) {
        this.actors = actors;
    }

    public List<Actor> getActorList() {
        return actorList;
    }

    public void setActorList(List<Actor> actorList) {
        this.actorList = actorList;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getStudios() {
        return studios;
    }

    public void setStudios(List<String> studios) {
        this.studios = studios;
    }

    public List<String> getCountries() {
        return countries;
    }

    public void setCountries(List<String> countries) {
        this.countries = countries;
    }

    public String getThumb() {
        return thumb;
    }

    public void setThumb(String thumb) {
        this.thumb = thumb;
    }

    public List<String> getThumbs() {
        return thumbs;
    }

    public void setThumbs(List<String> thumbs) {
        this.thumbs = thumbs;
    }

    public String getFanart() {
        return fanart;
    }

    public void setFanart(String fanart) {
        this.fanart = fanart;
    }

    public String getTrailer() {
        return trailer;
    }

    public void setTrailer(String trailer) {
        this.trailer = trailer;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getImdb() {
        return imdb;
    }

    public void setImdb(String imdb) {
        this.imdb = imdb;
    }

    public String getTmdbId() {
        return tmdbId;
    }

    public void setTmdbId(String tmdbId) {
        this.tmdbId = tmdbId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}