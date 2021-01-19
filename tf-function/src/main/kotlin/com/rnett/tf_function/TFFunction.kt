package com.rnett.tf_function

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class TFFunction


inline fun <R> result(block: () -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return block()
}
