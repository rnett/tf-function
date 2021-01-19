package com.rnett.tf_function

import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.FqName
import java.io.File

private val logFile by lazy {
    File("C:\\Users\\jimne\\Desktop\\My Stuff\\tf-function\\log").also { it.writeText("") }
}

fun initLog() {
    logFile.writeText("")
}

fun log(it: Any?) = logFile.appendText(it.toString() + "\n")
fun log(key: String, it: Any?) = log("$key: $it")

fun Iterable<IrConstructorCall>.getAnnotation(name: FqName): IrConstructorCall? =
    firstOrNull { it.symbol.owner.parentAsClass.fqNameWhenAvailable == name }

fun Iterable<IrConstructorCall>.getAnnotations(name: FqName): List<IrConstructorCall> =
    filter { it.symbol.owner.parentAsClass.fqNameWhenAvailable == name }

fun Iterable<IrConstructorCall>.hasAnnotation(name: FqName) =
    any { it.symbol.owner.parentAsClass.fqNameWhenAvailable == name }
