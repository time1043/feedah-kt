package com.time1043.feedah_kt

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform