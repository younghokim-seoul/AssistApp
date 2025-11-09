@file:Suppress("NOTHING_TO_INLINE")

package com.example.assistapp.data.network.utils

import okhttp3.FormBody
import kotlin.collections.iterator

internal inline fun Map<String, String>.toFormBody(): FormBody {
    return FormBody.Builder().also { builder ->
        for ((k, v) in this) {
            builder.add(k, v)
        }
    }.build()
}
