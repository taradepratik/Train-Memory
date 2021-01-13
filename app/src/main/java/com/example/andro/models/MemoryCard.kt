package com.example.andro.models

data class MemoryCard(
        val identifer: Int,
        val imageUrl: String?= null,
        var isFaseUp: Boolean = false,
        var isMatched: Boolean = false
)
