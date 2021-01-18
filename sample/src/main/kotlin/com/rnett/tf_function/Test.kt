package com.rnett.tf_function

@TFFunction
fun TFFunctionBuilder.testFunc(a: Int): Int {
    return result { a + 2 }
}