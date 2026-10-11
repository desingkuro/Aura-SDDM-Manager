package com.example.aura

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform