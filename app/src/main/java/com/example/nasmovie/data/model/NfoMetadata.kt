package com.example.nasmovie.data.model

/**
 * NFO元数据解析结果
 * 兼容Kodi和Emby两种格式
 */
data class NfoMetadata(
    var title: String? = null,
    var originalTitle: String? = null,
    var sortTitle: String? = null,
    var plot: String? = null,
    var outline: String? = null,
    var tagline: String? = null,
    var year: Int = 0,
    var premiered: String? = null,
    var rating: Float = 0f,
    var votes: Int = 0,
    var mpaa: String? = null,
    var runtime: Int = 0,
    var director: String? = null,
    var directors: MutableList<String> = mutableListOf(),
    var writers: MutableList<String> = mutableListOf(),
    var actors: MutableList<String> = mutableListOf(),
    var actorList: MutableList<Actor> = mutableListOf(),
    var genres: MutableList<String> = mutableListOf(),
    var tags: MutableList<String> = mutableListOf(),
    var studios: MutableList<String> = mutableListOf(),
    var countries: MutableList<String> = mutableListOf(),
    var thumb: String? = null,
    var thumbs: MutableList<String> = mutableListOf(),
    var fanart: String? = null,
    var trailer: String? = null,
    var id: String? = null,
    var imdb: String? = null,
    var tmdbId: String? = null,
    var filename: String? = null
) {
    /**
     * 演员详情
     */
    data class Actor(
        var name: String? = null,
        var role: String? = null,
        var thumb: String? = null,
        var order: String? = null
    ) {
        constructor(name: String, role: String) : this(name, role, null, null)
    }
}