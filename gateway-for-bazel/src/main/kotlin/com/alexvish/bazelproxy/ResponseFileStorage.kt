package com.alexvish.bazelproxy

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Component
class ResponseFileStorage(@Value("\${bazelproxy.rootPath}") val rootPath: Path) {
    private val removePrefixSlash = Regex("^[/]*")
    init {
        Files.createDirectories(rootPath)
    }

    fun processResponse(location: String, exchange: ServerWebExchange, body:Flux<DataBuffer>): Flux<DataBuffer> {
        val storePath = rootPath.resolve(removePrefixSlash.replaceFirst(location,""))
        Files.createDirectories(storePath.parent)
        val channel = Files.newByteChannel(storePath,StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        return body.map { dataBuffer ->
            val byteBuffer = dataBuffer.toByteBuffer().duplicate()
            channel.write(byteBuffer)
            dataBuffer
        }.doOnComplete {
            channel.close()
        }.doOnError {
            channel.close()
            Files.deleteIfExists(storePath)
        }.doOnCancel {
            channel.close()
            Files.deleteIfExists(storePath)
        }
    }
}