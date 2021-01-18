package com.rnett.tf_function

import org.tensorflow.Operand
import org.tensorflow.op.Ops

@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class TFFunction

class TFFunctionBuilder(val tf: Ops) {
    fun <T : Operand<*>> T.resolve() = this
    fun <R> result(block: () -> R): R = block()
}
