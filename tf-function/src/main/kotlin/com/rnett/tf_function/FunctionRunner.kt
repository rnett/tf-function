package com.rnett.tf_function

import org.tensorflow.ConcreteFunction
import org.tensorflow.Operand
import org.tensorflow.Signature
import org.tensorflow.internal.types.registry.TensorTypeRegistry
import org.tensorflow.ndarray.Shape
import org.tensorflow.op.Ops
import org.tensorflow.op.core.Placeholder
import org.tensorflow.proto.framework.DataType
import org.tensorflow.types.family.TType
import kotlin.properties.Delegates

data class TensorInfo(val shape: Shape, val dataType: DataType)

data class ArgsSignature(val nonTensorArgs: Map<String, *>, val tensors: Map<String, TensorInfo>) {
    val key by lazy { tensors.entries.joinToString("_") { "${it.key}=${it.value.dataType.name}${it.value.shape}" } }
}

data class FunctionResult<R>(val outputs: Map<String, Operand<*>>, val resultBuilder: (Map<String, Operand<*>>) -> R)

data class BuiltFunction<R>(val function: ConcreteFunction, val resultBuilder: (Map<String, Operand<*>>) -> R)

class FunctionRunner<R>(
    val name: String,
    val function: Ops.(Map<String, *>) -> FunctionResult<R>
) {
    private val functionCache = mutableMapOf<ArgsSignature, BuiltFunction<R>>()

    private fun buildFunction(args: ArgsSignature): BuiltFunction<R> {
        var resultBuilder: (Map<String, Operand<*>>) -> R by Delegates.notNull()
        val function = ConcreteFunction.create { tf ->
            val inputs = args.tensors.mapValues {
                tf.placeholder(TensorTypeRegistry.find<TType>(it.value.dataType).type(), Placeholder.shape(it.value.shape))
            }

            val result = function(tf, args.nonTensorArgs + inputs)
            resultBuilder = result.resultBuilder

            val builder = Signature.builder().key("${name}__${args.key}").methodName(name)

            inputs.forEach { (k, v) -> builder.input(k, v) }
            result.outputs.forEach { (k, v) -> builder.output(k, v) }

            builder.build()
        }
        return BuiltFunction(function, resultBuilder)
    }

    fun runEager(tf: Ops, args: Map<String, *>): R {
        check(tf.scope().env().isEager)

        val tensorArgs = args.filterValues { it is Operand<*> }.mapValues { (_, it) -> (it as Operand<*>).asTensor() }
        val constantArgs = args.filterValues { it !is Operand<*> }
        val argSignature = ArgsSignature(constantArgs, tensorArgs.mapValues { TensorInfo(it.value.shape(), it.value.dataType()) })
        val function = functionCache.getOrPut(argSignature) { buildFunction(argSignature) }

        val output = function.function.call(tensorArgs).mapValues { tf.constantOf(it.value as TType) }
        return function.resultBuilder(output)
    }

    fun runGraph(tf: Ops, args: Map<String, *>): R {
        check(tf.scope().env().isGraph)

        val result = function(tf.withSubScope(name), args)

        return result.resultBuilder(result.outputs)
    }

    fun run(tf: Ops, args: Map<String, *>): R = if (tf.scope().env().isEager)
        runEager(tf, args)
    else
        runGraph(tf, args)

}