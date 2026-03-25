package com.example.nasmovie.data.parser

import android.util.Log
import android.util.Xml
import com.example.nasmovie.data.model.NfoMetadata
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * NFO文件解析器
 * 支持Kodi和Emby两种格式
 */
object NfoParser {

    private const val TAG = "NfoParser"

    // Kodi格式标签
    private const val TAG_MOVIE = "movie"
    private const val TAG_TITLE = "title"
    private const val TAG_ORIGINALTITLE = "originaltitle"
    private const val TAG_SORTTITLE = "sorttitle"
    private const val TAG_PLOT = "plot"
    private const val TAG_OUTLINE = "outline"
    private const val TAG_TAGLINE = "tagline"
    private const val TAG_YEAR = "year"
    private const val TAG_PREMIERED = "premiered"
    private const val TAG_RATING = "rating"
    private const val TAG_VALUE = "value"
    private const val TAG_VOTES = "votes"
    private const val TAG_MPAA = "mpaa"
    private const val TAG_RUNTIME = "runtime"
    private const val TAG_DIRECTOR = "director"
    private const val TAG_WRITER = "writer"
    private const val TAG_ACTOR = "actor"
    private const val TAG_NAME = "name"
    private const val TAG_ROLE = "role"
    private const val TAG_THUMB = "thumb"
    private const val TAG_FANART = "fanart"
    private const val TAG_TRAILER = "trailer"
    private const val TAG_ID = "id"
    private const val TAG_IMDB = "imdb"
    private const val TAG_TMDBID = "tmdbid"
    private const val TAG_GENRE = "genre"
    private const val TAG_TAG = "tag"
    private const val TAG_STUDIO = "studio"
    private const val TAG_COUNTRY = "country"
    private const val TAG_FILENAME = "filenameandpath"

    // Emby格式标签
    private const val TAG_MOVIE_EMBY = "Movie"

    /**
     * 解析NFO文件内容
     */
    fun parse(data: ByteArray?): NfoMetadata? {
        if (data == null || data.isEmpty()) {
            return null
        }

        return try {
            var content = String(data, StandardCharsets.UTF_8)
            // 移除BOM标记
            if (content.startsWith("\uFEFF")) {
                content = content.substring(1)
            }
            val inputStream = ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8))
            parse(inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing NFO: ${e.message}")
            null
        }
    }

    /**
     * 解析NFO文件输入流
     */
    fun parse(inputStream: InputStream): NfoMetadata {
        val metadata = NfoMetadata()

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, StandardCharsets.UTF_8.name())

            var eventType = parser.eventType
            var currentTag: String? = null
            var currentActor: NfoMetadata.Actor? = null
            var inActor = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name

                        // 检测格式
                        if (currentTag == TAG_ACTOR) {
                            inActor = true
                            currentActor = NfoMetadata.Actor()
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim()
                        if (text.isNullOrEmpty()) {
                            // 跳过
                        } else if (inActor && currentActor != null) {
                            parseActorField(currentTag, text, currentActor)
                        } else {
                            parseMovieField(currentTag, text, metadata)
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val endTag = parser.name
                        if (endTag == TAG_ACTOR) {
                            inActor = false
                            currentActor?.let { actor ->
                                if (actor.name != null) {
                                    metadata.actorList.add(actor)
                                    metadata.actors.add(actor.name!!)
                                }
                            }
                            currentActor = null
                        }
                        currentTag = null
                    }
                }
                eventType = parser.next()
            }

        } catch (e: XmlPullParserException) {
            Log.e(TAG, "Error parsing NFO: ${e.message}")
        } catch (e: IOException) {
            Log.e(TAG, "Error parsing NFO: ${e.message}")
        } finally {
            try {
                inputStream.close()
            } catch (e: IOException) {
                // 忽略
            }
        }

        return metadata
    }

    private fun parseMovieField(tag: String?, text: String, metadata: NfoMetadata) {
        if (tag == null) return

        when (tag) {
            TAG_TITLE -> if (metadata.title == null) metadata.title = text
            TAG_ORIGINALTITLE -> metadata.originalTitle = text
            TAG_SORTTITLE -> metadata.sortTitle = text
            TAG_PLOT -> metadata.plot = text
            TAG_OUTLINE -> metadata.outline = text
            TAG_TAGLINE -> metadata.tagline = text
            TAG_YEAR -> {
                try {
                    metadata.year = text.toInt()
                } catch (e: NumberFormatException) {
                    if (text.length >= 4) {
                        try {
                            metadata.year = text.substring(0, 4).toInt()
                        } catch (e2: NumberFormatException) {
                            // 忽略
                        }
                    }
                }
            }
            TAG_PREMIERED -> {
                metadata.premiered = text
                if (metadata.year == 0 && text.length >= 4) {
                    try {
                        metadata.year = text.substring(0, 4).toInt()
                    } catch (e: NumberFormatException) {
                        // 忽略
                    }
                }
            }
            TAG_RATING, TAG_VALUE -> {
                try {
                    val value = text.toFloat()
                    if (value > 0 || metadata.rating == 0f) {
                        metadata.rating = value
                    }
                } catch (e: NumberFormatException) {
                    // 忽略
                }
            }
            TAG_VOTES -> {
                try {
                    metadata.votes = text.replace("[^0-9]".toRegex(), "").toInt()
                } catch (e: NumberFormatException) {
                    // 忽略
                }
            }
            TAG_MPAA -> metadata.mpaa = text
            TAG_RUNTIME -> {
                try {
                    metadata.runtime = if (text.contains(":")) {
                        val parts = text.split(":")
                        val hours = if (parts.isNotEmpty()) parts[0].toInt() else 0
                        val minutes = if (parts.size > 1) parts[1].toInt() else 0
                        hours * 60 + minutes
                    } else {
                        text.replace("[^0-9]".toRegex(), "").toInt()
                    }
                } catch (e: NumberFormatException) {
                    // 忽略
                }
            }
            TAG_DIRECTOR -> {
                if (metadata.director == null) {
                    metadata.director = text
                }
                metadata.directors.add(text)
            }
            TAG_WRITER -> metadata.writers.add(text)
            TAG_THUMB -> {
                if (metadata.thumb == null) {
                    metadata.thumb = text
                }
                metadata.thumbs.add(text)
            }
            TAG_FANART -> metadata.fanart = text
            TAG_TRAILER -> metadata.trailer = text
            TAG_ID -> if (metadata.id == null) metadata.id = text
            TAG_IMDB -> {
                metadata.imdb = text
                if (metadata.id == null) {
                    metadata.id = text
                }
            }
            TAG_TMDBID -> metadata.tmdbId = text
            TAG_GENRE -> metadata.genres.add(text)
            TAG_TAG -> metadata.tags.add(text)
            TAG_STUDIO -> metadata.studios.add(text)
            TAG_COUNTRY -> metadata.countries.add(text)
            TAG_FILENAME -> metadata.filename = text
        }
    }

    private fun parseActorField(tag: String?, text: String, actor: NfoMetadata.Actor) {
        if (tag == null) return

        when (tag) {
            TAG_NAME -> actor.name = text
            TAG_ROLE -> actor.role = text
            TAG_THUMB -> actor.thumb = text
        }
    }
}