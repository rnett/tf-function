package com.rnett.tf_function

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.name.FqName

object Names {
    val TFFunction = FqName("com.rnett.tf_function.TFFunction")
    val FunctionRunner = FqName("com.rnett.tf_function.FunctionRunner")
    val FunctionResult = FqName("com.rnett.tf_function.FunctionResult")
    val result = FqName("com.rnett.tf_function.result")

    val Ops = FqName("org.tensorflow.op.Ops")
    val Operand = FqName("org.tensorflow.Operand")

    val Map = FqName("kotlin.collections.Map")
    val mapOf = FqName("kotlin.collections.mapOf")

    val error = FqName("kotlin.error")

    val Pair = FqName("kotlin.Pair")
    val to = FqName("kotlin.to")
}

class References(val context: IrPluginContext) {
    val TFFunction = context.referenceClass(Names.TFFunction) ?: error("Could not find TFFunction")
    val result = context.referenceFunctions(Names.result).single()
    val FunctionRunner = context.referenceClass(Names.FunctionRunner) ?: error("Could not find FunctionRunner")
    val FunctionRunner_run = FunctionRunner.getSimpleFunction("run") ?: error("Could not find FunctionRunner.run")
    val FunctionResult = context.referenceClass(Names.FunctionResult) ?: error("Could not find FunctionResult")

    val Ops = context.referenceClass(Names.Ops) ?: error("Could not find Ops")
    val Operand = context.referenceClass(Names.Operand) ?: error("Could not find Operand")

    val Map = context.referenceClass(Names.Map) ?: error("Could not find Map")
    val mapOf = context.referenceFunctions(Names.mapOf).single { it.owner.valueParameters.size == 1 && it.owner.valueParameters.first().isVararg }
    val Map_get = Map.getSimpleFunction("get") ?: error("Could not find Map.get")

    val error = context.referenceFunctions(Names.error).single()

    val Pair = context.referenceClass(Names.Pair) ?: error("Could not find Pair")

    val to = context.referenceFunctions(Names.to).single()
}