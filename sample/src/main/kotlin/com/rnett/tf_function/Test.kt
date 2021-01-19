package com.rnett.tf_function

import org.tensorflow.Operand
import org.tensorflow.op.Ops
import org.tensorflow.types.TInt32

@TFFunction
fun Ops.testFunc(a: Int, b: Operand<TInt32>): Pair<Int, Operand<TInt32>> {
    val c: Operand<TInt32> = math.add(b, constant(2))
    println("Creating graph: a = $a")
    result {
        println("Building result: a = $a, executing in ${scope().env().environmentType()}")
        return (a + 2) to c
    }
}

// becomes something like this field:
// (it's called like testFuncProp.run(this, mapOf("a" to a, "b" to b)) )

val testFuncProp = FunctionRunner("testFunc") {
    val a = it["a"] as Int? ?: error("a not present")
    val b = it["b"] as Operand<TInt32>? ?: error("b not present")

    val c: Operand<TInt32> = math.add(b, constant(2))

    val outputs = mapOf("c" to c)

    val result: (Map<String, Operand<*>>) -> Pair<Int, Operand<TInt32>> = {
        val c = it[""] as Operand<TInt32>? ?: error("c not present")

        (a + 2) to c
    }

    FunctionResult(outputs, result)
}

fun main() {
    with(Ops.create()) {

        val (a, b) = testFunc(4, constant(4))
        println("Result: $a, ${b.asTensor().getInt()}")

        println()

        // shouldn't re-create graph, will re-execute result block

        val (a1, b1) = testFunc(4, constant(4))
        println("Result: $a1, ${b1.asTensor().getInt()}")

        println()

        // will re-create graph

        val (a2, b2) = testFunc(6, constant(6))
        println("Result: $a2, ${b2.asTensor().getInt()}")
    }
}

