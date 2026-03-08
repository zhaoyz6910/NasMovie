package com.example.nasmovie.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 电影实体
 */
@Entity(tableName = "movie")
public class Movie {

    @NonNull
    @PrimaryKey
    private String id = "";
    private String title;           // 标题
    private String originalTitle;   // 原标题
    private String plot;            // 简介
    private String director;        // 导演
    private String actors;          // 演员（JSON数组）
    private String genres;          // 类型（JSON数组）
    private int year;               // 年份
    private float rating;           // 评分
    private String posterPath;      // 海报路径（SMB）- 用于首页，优先 poster.jpg
    private String thumbPath;       // 缩略图路径（SMB）- 用于详情页，优先 thumb.jpg
    private String localPosterPath; // 本地缓存的 poster.jpg 路径
    private String localThumbPath;  // 本地缓存的 thumb.jpg 路径
    private String videoPath;       // 视频路径（SMB）
    private String nfoPath;         // NFO路径
    private String subtitlePaths;   // 字幕路径列表（JSON数组）
    private long fileSize;          // 文件大小
    private int duration;           // 时长（分钟）
    private long addTime;           // 添加时间
    private String serverId;        // 所属服务器ID
    private String folderPath;      // 文件夹路径

    public Movie() {
        this.addTime = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getPlot() {
        return plot;
    }

    public void setPlot(String plot) {
        this.plot = plot;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public String getActors() {
        return actors;
    }

    public void setActors(String actors) {
        this.actors = actors;
    }

    public String getGenres() {
        return genres;
    }

    public void setGenres(String genres) {
        this.genres = genres;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public String getPosterPath() {
        return posterPath;
    }

    public void setPosterPath(String posterPath) {
        this.posterPath = posterPath;
    }

    public String getThumbPath() {
        return thumbPath;
    }

    public void setThumbPath(String thumbPath) {
        this.thumbPath = thumbPath;
    }

    public String getLocalPosterPath() {
        return localPosterPath;
    }

    public void setLocalPosterPath(String localPosterPath) {
        this.localPosterPath = localPosterPath;
    }

    public String getLocalThumbPath() {
        return localThumbPath;
    }

    public void setLocalThumbPath(String localThumbPath) {
        this.localThumbPath = localThumbPath;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public String getNfoPath() {
        return nfoPath;
    }

    public void setNfoPath(String nfoPath) {
        this.nfoPath = nfoPath;
    }

    public String getSubtitlePaths() {
        return subtitlePaths;
    }

    public void setSubtitlePaths(String subtitlePaths) {
        this.subtitlePaths = subtitlePaths;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public long getAddTime() {
        return addTime;
    }

    public void setAddTime(long addTime) {
        this.addTime = addTime;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    // Helper methods for JSON fields
    private static final Gson gson = new Gson();

    public List<String> getActorList() {
        if (actors == null || actors.isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<String>>() {}.getType();
        List<String> result = gson.fromJson(actors, type);
        return result != null ? result : new ArrayList<>();
    }

    public void setActorList(List<String> actorList) {
        this.actors = gson.toJson(actorList);
    }

    public List<String> getGenreList() {
        if (genres == null || genres.isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<String>>() {}.getType();
        List<String> result = gson.fromJson(genres, type);
        return result != null ? result : new ArrayList<>();
    }

    public void setGenreList(List<String> genreList) {
        this.genres = gson.toJson(genreList);
    }

    public List<String> getSubtitlePathList() {
        if (subtitlePaths == null || subtitlePaths.isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<String>>() {}.getType();
        List<String> result = gson.fromJson(subtitlePaths, type);
        return result != null ? result : new ArrayList<>();
    }

    public void setSubtitlePathList(List<String> pathList) {
        this.subtitlePaths = gson.toJson(pathList);
    }
}