package com.example.cahier.core.document

import kotlinx.serialization.json.Json

object DocumentSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    fun encode(document: TicDocument): String {
        return json.encodeToString(TicDocument.serializer(), document)
    }

    fun decodeOrNull(raw: String?): TicDocument? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString(TicDocument.serializer(), raw)
        }.getOrNull()
    }
}
