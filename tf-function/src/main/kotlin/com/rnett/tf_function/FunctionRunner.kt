package com.rnett.tf_function

import org.tensorflow.ConcreteFunction
import org.tensorflow.Operand
import org.tensorflow.Signature
import org.tensorflow.Tensor
import org.tensorflow.internal.types.registry.TensorTypeRegistry
import org.tensorflow.ndarray.Shape
import org.tensorflow.op.Ops
import org.tensorflow.op.core.Placeholder
import org.tensorflow.proto.framework.DataType
import org.tensorflow.types.family.TType

data class ArgsSignature(val types: Map<String, Pair<DataType, Shape>>) : Map<String, Pair<DataType, Shape>> by types {
    val key by lazy { types.entries.joinToString("_") { "${it.key}=${it.value.first.name}${it.value.second}" } }
}

class FunctionRunner(
    val name: String,
    val function: TFFunctionBuilder.(Map<String, Operand<*>>) -> Map<String, Operand<*>>
) {
    private val functionCache = mutableMapOf<ArgsSignature, ConcreteFunction>()

    private fun buildFunction(args: ArgsSignature): ConcreteFunction =
        ConcreteFunction.create { tf ->
            val inputs = args.mapValues {
                tf.placeholder(TensorTypeRegistry.find<TType>(it.value.first).type(), Placeholder.shape(it.value.second))
            }

            val outputs = function(TFFunctionBuilder(tf), inputs)

            val builder = Signature.builder().key("${name}__${args.key}").methodName(name)

            inputs.forEach { (k, v) -> builder.input(k, v) }
            outputs.forEach { (k, v) -> builder.output(k, v) }

            builder.build()
        }

    fun runTensors(args: Map<String, Tensor>): Map<String, Tensor> {
        val argSignature = ArgsSignature(args.mapValues { (_, v) -> v.dataType() to v.shape() })
        val function = functionCache.getOrPut(argSignature) { buildFunction(argSignature) }
        return function.call(args)
    }

    fun runEager(tf: Ops, args: Map<String, Operand<*>>): Map<String, Operand<*>> {
        check(tf.scope().env().isEager)

        val tensorArgs = args.mapValues { it.value.asTensor() }
        val result = runTensors(tensorArgs)
        return result.mapValues { tf.constantOf(it.value as TType) }
    }

    fun runGraph(tf: Ops, args: Map<String, Operand<*>>): Map<String, Operand<*>> {
        check(tf.scope().env().isGraph)

        return function(TFFunctionBuilder(tf.withSubScope(name)), args)
    }

    fun run(tf: Ops, args: Map<String, Operand<*>>): Map<String, Operand<*>> = if (tf.scope().env().isEager)
        runEager(tf, args)
    else
        runGraph(tf, args)

}