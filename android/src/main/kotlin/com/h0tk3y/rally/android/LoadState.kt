package com.h0tk3y.rally.android

sealed interface LoadState<out T> {
    object LOADING : LoadState<Nothing>
    object FAILED : LoadState<Nothing>
    object EMPTY : LoadState<Nothing>
    data class Loaded<T>(val value: T) : LoadState<T>
}

fun <T> LoadState<T>.valueOrNull() = when (this) {
    is LoadState.Loaded -> value
    else -> null
}