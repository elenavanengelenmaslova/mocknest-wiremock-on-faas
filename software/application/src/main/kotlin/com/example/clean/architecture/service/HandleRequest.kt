package com.example.clean.architecture.service

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse

const val BASE_URL = "http://mocknest.internal"
const val ADMIN_PREFIX = "/__admin/"
const val MOCKNEST_PREFIX = "/mocknest/"

fun interface HandleClientRequest {
    operator fun invoke(httpRequest: HttpRequest): HttpResponse
}
fun interface HandleAdminRequest {
    operator fun invoke(path: String, httpRequest: HttpRequest): HttpResponse
}