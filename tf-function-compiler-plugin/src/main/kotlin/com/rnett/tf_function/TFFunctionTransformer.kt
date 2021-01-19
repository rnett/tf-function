package com.rnett.tf_function

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.allParameters
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.assertCast
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.IrFunctionBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.transformStatement
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.IrStarProjectionImpl
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

class TFFunctionTransformer(context: IrPluginContext, messageCollector: MessageCollector) : BaseIrTransformer(context, messageCollector) {
    @OptIn(ExperimentalStdlibApi::class)
    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        if (declaration.hasAnnotation(Names.TFFunction)) {

            log("Initial", declaration.dump(true))

            if (declaration.extensionReceiverParameter?.type != References.Ops.typeWith()) {
                declaration.reportError("Functions with @TFFunction must have a org.tensorflow.op.Ops receiver.")
            }

            if (declaration.body == null) {
                declaration.reportError("@TFFunction function must have body")
            }

            val resultBlocks = declaration.body!!.statements.count { it is IrCall && it.symbol == References.result }

            if (resultBlocks > 1) {
                declaration.reportError("@TFFunction function can only have one result{} block")
            } else if (resultBlocks == 0) {
                //TODO allow none if directly retuning a tensor
                declaration.reportError("@TFFunction function must have have one result{} block")
            }

            val lastStatement = declaration.body!!.statements.last()
            if (lastStatement is IrReturn) {
                if (!(lastStatement.value is IrCall && (lastStatement.value as IrCall).symbol == References.result))
                    declaration.reportError("@TFFunction function must have result{} or return result{} as it's last statement")
            } else if (!(lastStatement is IrCall && lastStatement.symbol == References.result)) {
                declaration.reportError("@TFFunction function must have result{} or return result{} as it's last statement")
            }

            val runnerField = context.irFactory.buildField {
                isFinal = true
                isStatic = true
                type = References.FunctionRunner.typeWith()
                name = Name.identifier(declaration.name.asString().trim('<', '>') + "\$runner")
            }
                .apply {
                    val field = this
                    this.parent = currentFile

                    with(DeclarationIrBuilder(context, this.symbol)) {
                        initializer = irExprBody(irCall(References.FunctionRunner.constructors.single()).apply {
                            putValueArgument(0, irString(declaration.name.asString()))

                            /*
                            val lambda = declaration.deepCopyWithSymbols(field) { symbolRemapper, typeRemapper ->
                                DeepCopyIrTreeWithSymbols(symbolRemapper, typeRemapper, object : SymbolRenamer {
                                    override fun getFunctionName(symbol: IrSimpleFunctionSymbol): Name {
                                        return if (symbol == declaration.symbol) Name.special("<anonymous>") else super.getFunctionName(symbol)
                                    }
                                })
                            }.apply {
                                val function = this
                                this.returnType = returnType
                                this.origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                                this.visibility = DescriptorVisibilities.LOCAL
                             */

                            val lambda = buildLambda(References.FunctionResult.typeWith(declaration.returnType)) {
                                parent = field
                                val function = this

                                val ops = addValueParameter("ops", References.Ops.defaultType)
                                val args = addValueParameter(
                                    "args",
                                    References.Map.typeWith(context.irBuiltIns.stringType, null)
                                )

                                with(DeclarationIrBuilder(context, this.symbol)) {
                                    body = irBlockBody {
                                        val newArgs = buildMap<IrValueSymbol, IrValueSymbol> {
                                            put(declaration.extensionReceiverParameter!!.symbol, ops.symbol)

                                            declaration.valueParameters.forEach {
                                                val default = it.defaultValue
                                                val gotten = irTemporary(
                                                    irCall(References.Map_get).apply {
                                                        dispatchReceiver = irGet(args)
                                                        putValueArgument(0, irString(it.name.asString()))
                                                    }, "map_var", irType = it.type //TODO sub wildcard for type params
                                                )

                                                val value = if (default != null) {
                                                    irIfNull(it.type, irGet(gotten), default.expression, irGet(gotten))
                                                } else {
                                                    irIfNull(it.type, irGet(gotten), irCall(References.error).apply {
                                                        putValueArgument(0, irString("Required argument ${it.name.asString()} not present"))
                                                    }, irGet(gotten))
                                                }

                                                put(it.symbol, irTemporary(value, "arg_var").symbol)
                                            }
                                        }

                                        val newBodyStatements = declaration.body!!.statements.map {
                                            it.also { it.replaceSymbols(newArgs) }
                                        }


                                        newBodyStatements.dropLast(1).forEach {
                                            +it
                                        }

                                        val lastStatement = newBodyStatements.last().let {
                                            if (it is IrReturn)
                                                it.value
                                            else
                                                it
                                        }
                                        check(lastStatement is IrCall)


                                        val resultLambda = lastStatement.getValueArgument(0)!!.assertCast<IrFunctionExpression>().function

                                        val usedOperands = resultLambda.findUsedSymbols(References.Operand).associate {
                                            it.owner.name.asString() to it
                                        }

                                        val outputs = mapOf(context.irBuiltIns.stringType, References.Operand.defaultType,
                                            usedOperands.mapKeys { irString(it.key) }.mapValues { irGet(it.value.owner) }
                                        )

                                        val resultBuilder = buildLambda(declaration.returnType) {
                                            val resultFunction = this
                                            with(DeclarationIrBuilder(context, this.symbol)) {

                                                val tensorMap = resultFunction.addValueParameter(
                                                    "tensors",
                                                    References.Map.typeWith(context.irBuiltIns.stringType, References.Operand.starProjectedType)
                                                )

                                                body = irBlockBody {

                                                    val newTensorArgs = buildMap<IrValueSymbol, IrValueSymbol> {
                                                        usedOperands.forEach { (key, symbol) ->
                                                            val gotten = irTemporary(
                                                                irCall(References.Map_get).apply {
                                                                    dispatchReceiver = irGet(tensorMap)
                                                                    putValueArgument(0, irString(key))
                                                                }, "output_map_$key", irType = References.Operand.starProjectedType
                                                            )

                                                            val value = irIfNull(symbol.owner.type, irGet(gotten), irCall(References.error).apply {
                                                                putValueArgument(0, irString("Required output $key not present"))
                                                            }, irGet(gotten))

                                                            put(
                                                                symbol,
                                                                irTemporary(
                                                                    value,
                                                                    "output_$key",
                                                                    irType = References.Operand.starProjectedType
                                                                ).symbol
                                                            )
                                                        }
                                                    }

                                                    resultLambda.body!!.statements.forEach {
                                                        +it.also { it.replaceSymbols(newTensorArgs) }
                                                            .transformStatement(object : IrElementTransformerVoid() {
                                                                override fun visitReturn(expression: IrReturn): IrExpression {
                                                                    return IrReturnImpl(
                                                                        expression.startOffset,
                                                                        expression.endOffset,
                                                                        expression.type,
                                                                        resultFunction.symbol,
                                                                        expression.value
                                                                    )
                                                                }
                                                            })
                                                    }
                                                }

                                            }
                                        }

                                        +irReturn(
                                            irCallConstructor(
                                                References.FunctionResult.constructors.single(),
                                                listOf(declaration.returnType)
                                            ).apply {
                                                putValueArgument(0, outputs)
                                                putValueArgument(1, lambdaArgument(resultBuilder))
                                            })

                                    }
                                }
                            }

                            log("Lambda", lambda.dump(true))
                            putValueArgument(1, lambdaArgument(lambda))
                        })
                    }
                }


            with(DeclarationIrBuilder(context, declaration.symbol)) {
                declaration.body = irExprBody(irCall(References.FunctionRunner_run).apply {
                    dispatchReceiver = irGetField(null, runnerField)
                    putValueArgument(0, irGet(declaration.extensionReceiverParameter!!))
                    putValueArgument(
                        1,
                        mapOf(context.irBuiltIns.stringType, context.irBuiltIns.anyType.makeNullable(), declaration.valueParameters.associate {
                            irString(it.name.asString()) to irGet(it)
                        })
                    )
                }).patchDeclarationParents(declaration)
            }


            addFileDeclaration(runnerField.patchDeclarationParents(currentFile))
            log("Field", runnerField.dump(true))
            log("Method", declaration.dump(true))
        }
        return super.visitSimpleFunction(declaration)
    }

    fun IrElement.replaceSymbols(newSymbols: Map<IrValueSymbol, IrValueSymbol>) {
        transformChildrenVoid(object : IrElementTransformerVoidWithContext() {
            override fun visitGetValue(expression: IrGetValue): IrExpression {
                val newSymbol = newSymbols[expression.symbol]
                return if (newSymbol != null)
                    IrGetValueImpl(expression.startOffset, expression.endOffset, newSymbol, expression.origin)
                else
                    super.visitGetValue(expression)
            }
        })
    }

    fun IrElement.findUsedSymbols(type: IrClassSymbol? = null): Set<IrValueSymbol> {
        val seen = mutableSetOf<IrValueSymbol>()
        val declared = mutableSetOf<IrValueSymbol>()

        this.accept(object : IrElementTransformerVoid() {
            override fun visitGetValue(expression: IrGetValue): IrExpression {
                if (type == null || expression.symbol.owner.type.isSubtypeOfClass(type)) {
                    seen += expression.symbol
                }
                return expression
            }

            override fun visitVariable(declaration: IrVariable): IrStatement {
                declared += declaration.symbol
                return declaration
            }

            override fun visitValueParameter(declaration: IrValueParameter): IrStatement {
                declared += declaration.symbol
                return declaration
            }
        }, null)
        return seen - declared
    }

    inline fun buildLambda(
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

    fun varargOf(elementType: IrType, elements: Iterable<IrExpression>) = IrVarargImpl(
        UNDEFINED_OFFSET,
        UNDEFINED_OFFSET,
        context.irBuiltIns.arrayClass.typeWith(elementType),
        elementType,
        elements.toList()
    )


    fun IrBuilderWithScope.mapOf(
        keyType: IrType,
        valueType: IrType,
        map: Map<IrExpression, IrExpression>
    ): IrCall =
        irCall(References.mapOf, References.Map.typeWith(keyType, valueType)).apply {
            putTypeArgument(0, keyType)
            putTypeArgument(1, valueType)
            putValueArgument(0,
                varargOf(
                    References.Pair.typeWith(keyType, valueType),
                    map.map { (key, value) ->
                        irCall(References.to, References.Pair.typeWith(keyType, valueType)).apply {
                            putTypeArgument(0, keyType)
                            putTypeArgument(1, valueType)
                            extensionReceiver = key
                            putValueArgument(0, value)
                        }
                    }
                )
            )
        }
}

fun IrClassifierSymbol.typeWith(vararg arguments: IrType?): IrSimpleType = typeWith(arguments.toList())

fun IrClassifierSymbol.typeWith(arguments: List<IrType?>): IrSimpleType =
    IrSimpleTypeImpl(
        this,
        false,
        arguments.map { if (it != null) makeTypeProjection(it, Variance.INVARIANT) else IrStarProjectionImpl },
        emptyList()
    )