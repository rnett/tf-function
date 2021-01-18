package com.rnett.tf_function

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.allParameters
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.IrFunctionBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.transformStatement
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name

class TFFunctionTransformer(context: IrPluginContext, messageCollector: MessageCollector) : BaseIrTransformer(context, messageCollector) {
    @OptIn(ExperimentalStdlibApi::class)
    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        if (declaration.hasAnnotation(Names.TFFunction)) {

            log(declaration.dump(true))

            if (declaration.extensionReceiverParameter?.type != References.TFFunctionBuilder.typeWith()) {
                declaration.reportError("Functions with @TFFunction must have a com.rnett.tf_funcion.TFFunctionBuilder receiver.")
            }

            val runnerField = context.irFactory.buildField {
                isFinal = true
                isStatic = true
                type = References.FunctionRunner.typeWith()
                name = Name.identifier(declaration.name.asString().trim('<', '>') + "\$runner")
            }.apply {
                parent = currentFile
                val field = this

                with(DeclarationIrBuilder(context, this.symbol)) {
                    initializer = irExprBody(irCall(References.FunctionRunner.constructors.single()).apply {
                        putValueArgument(0, irString(declaration.name.asString()))

                        val lambda = buildLambda(References.Map.typeWith(context.irBuiltIns.stringType, References.Operand.starProjectedType)) {
                            parent = field
                            val ops = addValueParameter("ops", References.TFFunctionBuilder.defaultType)
                            val args = addValueParameter(
                                "args",
                                References.Map.typeWith(context.irBuiltIns.stringType, References.Operand.starProjectedType)
                            )
                            with(DeclarationIrBuilder(context, this.symbol)) {
                                body = irBlockBody {
                                    val newArgs = buildMap<IrValueSymbol, IrValueDeclaration> {
                                        put(declaration.extensionReceiverParameter!!.symbol, ops)

                                        declaration.valueParameters.forEach {
                                            val default = it.defaultValue
                                            val gotten = irTemporary(
                                                irCall(References.Map_get).apply {
                                                    dispatchReceiver = irGet(args)
                                                    putValueArgument(0, irString(it.name.asString()))
                                                }, "map_var", irType = References.Operand.starProjectedType
                                            )

                                            val value = if (default != null) {
                                                irIfNull(it.type, irGet(gotten), default.expression, irGet(gotten))
                                            } else {
                                                irIfNull(it.type, irGet(gotten), irCall(References.error).apply {
                                                    putValueArgument(0, irString("Required argument ${it.name.asString()} not present"))
                                                }, irGet(gotten))
                                            }

                                            put(it.symbol, irTemporary(value, "arg_var"))
                                        }
                                    }


                                    declaration.body!!.statements.forEach {
                                        val stmt = it.deepCopyWithSymbols().transformStatement(object : IrElementTransformerVoid() {
                                            override fun visitGetValue(expression: IrGetValue): IrExpression {
                                                if (expression.symbol in newArgs)
                                                    return irGet(newArgs.getValue(expression.symbol))
                                                return super.visitGetValue(expression)
                                            }
                                        }).patchDeclarationParents(this@buildLambda)

                                        //TODO this is the original return type, needs to be Map<>
                                        if (stmt is IrReturn)
                                            +irReturn(stmt.value)
                                        else
                                            +stmt
                                    }
                                }.patchDeclarationParents(this@buildLambda)
                            }
                        }

                        putValueArgument(1, lambdaArgument(lambda))
                    })
                }
            }


            log(runnerField.dump(true))

            addFileDeclaration(runnerField)
        }
        return super.visitSimpleFunction(declaration)
    }

    fun buildLambda(
        returnType: IrType,
//            parent: IrDeclarationParent,
        funBuilder: IrFunctionBuilder.() -> Unit = {},
        funApply: IrSimpleFunction.() -> Unit
    ): IrSimpleFunction = context.irFactory.buildFun {
        name = Name.special("<anonymous>")
        this.returnType = returnType
        this.origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        this.visibility = DescriptorVisibilities.LOCAL
        funBuilder()
    }.apply {
//        this.parent = parent
        funApply()
    }

    fun lambdaArgument(
        lambda: IrSimpleFunction,
        type: IrType = run {
            val base = if (lambda.isSuspend)
                context.irBuiltIns.suspendFunction(lambda.allParameters.size)
            else
                context.irBuiltIns.function(lambda.allParameters.size)

            base.typeWith(lambda.allParameters.map { it.type } + lambda.returnType)
        }
    ) = IrFunctionExpressionImpl(
        UNDEFINED_OFFSET,
        UNDEFINED_OFFSET,
        type,
        lambda,
        IrStatementOrigin.LAMBDA
    )
}