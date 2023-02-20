package com.alexvish.bazelproxy

import org.reactivestreams.Publisher
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponseDecorator
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


@Component
class StoreResponse(val fileStorage: ResponseFileStorage):GlobalFilter, Ordered {
    override fun filter(exchange: ServerWebExchange?, chain: GatewayFilterChain?): Mono<Void> {
        return filterInternal(exchange!!, chain!!)
    }

    fun filterInternal(exchange: ServerWebExchange, chain: GatewayFilterChain) :Mono<Void>  {
        return if (canStoreResponse(exchange)) {
            val storePath = exchange.request.path.value()
            chain.filter(exchange.mutate().response(StoringResponseDecorator(exchange, fileStorage, storePath)).build())
        } else {
            chain.filter(exchange)
        }
    }

    fun canStoreResponse(exchange: ServerWebExchange): Boolean {
        return exchange.request.method == HttpMethod.GET && !hasRequestBody(exchange.request)
    }

    fun hasRequestBody(request: ServerHttpRequest): Boolean {
        return request.headers.contentLength > 0L
    }

    override fun getOrder(): Int {
        return -2
    }
}

class StoringResponseDecorator(
    val exchange: ServerWebExchange,
    val responseFileStorage: ResponseFileStorage,
    val storePath: String): ServerHttpResponseDecorator(exchange.response) {

    override fun writeWith(body: Publisher<out DataBuffer>): Mono<Void> {
        val response = exchange.response
        val decoratedBody = if (response.statusCode == HttpStatus.OK) {
            responseFileStorage.processResponse(storePath,exchange, body as Flux<DataBuffer>)
        } else {
            body
        }
        return super.writeWith(decoratedBody)
    }
}