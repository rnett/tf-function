# @tf.function, Kotlin style

This is a proof of concept of a compiler plugin that provides `@tf.function` like behavior for Tensorflow Java
(wrapping seemingly eager code in a Graph, while being able to seamlessly call the method).

It's not that hard to extend this to a lambda like `graphFunction{ ... }`, which imo is a better and more Kotliny API.

The semantics are slightly different from Python's, since I wanted to support returning any type.  
The `result` block is re-ran every time the function is called, with the Graph's results substitured in for any `Operand` variables accessed in
the `result`.

Most of the interesting stuff happens in `tf-function/src/main/kotlin/com/rnett/tf_function/FunctionRunner.kt` (runtime) and
`tf-function-compiler-plugin/src/main/kotlin/com/rnett/tf_function/TFFunctionTransformer.kt` (compiler, it's very hacked together).

## Limitations (see Example)

The big issue with this is that the `c` in the result block (coming from the result of the graph execution) must be the same type as the `c` in the
function (the graph operand), but when calling from eager mode, the result block is given constants. For this to work, we need a way to construct "
fake" eager ops by providing their outputs.

I'm also not trying to handle generics. It should be possible by relying on erasure though.

## Example

Example in `sample/src/main/kotlin/com/rnett/tf_function/Test.kt`:

```kotlin
@TFFunction
fun Ops.testFunc(a: Int, b: Operand<TInt32>): Pair<Int, Operand<TInt32>> {
    val c: Operand<TInt32> = math.add(b, constant(2))
    println("Creating graph: a = $a")
    result {
        println("Building result: a = $a, executing in ${scope().env().environmentType()}")
        return (a + 2) to c
    }
}
```

The compiler plugin turns the method into something like this: `testFuncProp.run(this, mapOf("a" to a, "b" to b))`, and adds a field `testFuncProp`
that looks something like this:

```kotlin
val testFuncProp = FunctionRunner("testFunc") {
    val a = it["a"] as Int? ?: error("a is not present")
    val b = it["b"] as Operand<TInt32>? ?: error("b is not present")

    val c: Operand<TInt32> = math.add(b, constant(2))

    val outputs = mapOf("c" to c)

    val result: (Map<String, Operand<*>>) -> Pair<Int, Operand<TInt32>> = {
        val c = it[""] as Operand<TInt32>? ?: error("c is not present")

        (a + 2) to c
    }

    FunctionResult(outputs, result)
}
```

The function can then be used like:

```kotlin
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
```

which prints

```
Creating graph: a = 4
Every time: a = 4, executing in GRAPH
Result: 6, 6

Every time: a = 4, executing in GRAPH
Result: 6, 6

Creating graph: a = 6
Every time: a = 6, executing in GRAPH
Result: 8, 8
```