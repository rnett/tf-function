package com.rnett.tf_function

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.name.FqName

object Names {
    val TFFunction = FqName("com.rnett.tf_function.TFFunction")
    val TFFunctionBuilder = FqName("com.rnett.tf_function.TFFunctionBuilder")

    val FunctionRunner = FqName("com.rnett.tf_function.FunctionRunner")
    val Ops = FqName("org.tensorflow.op.Ops")
    val Operand = FqName("org.tensorflow.Operand")

    val Map = FqName("kotlin.collections.Map")
    val error = FqName("kotlin.error")
}

class References(val context: IrPluginContext) {
    val TFFunction = context.referenceClass(Names.TFFunction) ?: error("Could not find TFFunction")
    val TFFunctionBuilder = context.referenceClass(Names.TFFunctionBuilder) ?: error("Could not find TFFunctionBuilder")
    val FunctionRunner = context.referenceClass(Names.FunctionRunner) ?: error("Could not find FunctionRunner")

    val Ops = context.referenceClass(Names.Ops) ?: error("Could not find Ops")
    val Operand = context.referenceClass(Names.Operand) ?: error("Could not find Operand")

    val Map = context.referenceClass(Names.Map) ?: error("Could not find Map")
    val Map_get = Map.getSimpleFunction("get") ?: error("Could not find Map.get")
    val error = context.referenceFunctions(Names.error).single()
}