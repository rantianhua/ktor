package io.ktor.client.engine.okhttp

import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.jvm.javaio.*
import okhttp3.*
import okio.*

internal class StreamRequestBody(private val block: () -> ByteReadChannel) : RequestBody() {
    override fun contentType(): MediaType? = null

    override fun writeTo(sink: BufferedSink) {
        Okio.source(block().toInputStream()).use {
            sink.writeAll(it)
        }
    }
}