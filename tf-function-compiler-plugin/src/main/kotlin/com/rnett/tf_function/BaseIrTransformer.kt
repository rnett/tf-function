package com.rnett.tf_function

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.declarations.path
import java.io.File

abstract class BaseIrTransformer(val context: IrPluginContext, val messageCollector: MessageCollector) :
    IrElementTransformerVoidWithContext(), FileLoweringPass {
    lateinit var file: File
    lateinit var fileText: String

    fun IrElement.location(): CompilerMessageLocation? {
        val beforeText = fileText.substring(0, this.startOffset)
        val line = beforeText.count { it == '\n' } + 1
        val beforeLine = beforeText.substringBeforeLast('\n').length
        val offsetOnLine = this.startOffset - beforeLine
        return CompilerMessageLocation.create(file.path, line, offsetOnLine, null)
    }

    fun IrElement.reportError(
        message: String,
        level: CompilerMessageSeverity = CompilerMessageSeverity.ERROR
    ) = messageCollector.report(
        level,
        message,
        this.location()
    )

    private val fileDeclarations = mutableListOf<IrDeclaration>()

    protected fun addFileDeclaration(declaration: IrDeclaration) {
        declaration.parent = currentFile
        fileDeclarations += declaration
    }

    override fun lower(irFile: IrFile) {
        file = File(irFile.path)
        fileText = file.readText()

        visitFile(irFile)
        irFile.declarations.addAll(fileDeclarations.filter { it.parent == irFile })
    }

    protected val currentSymbolOwner get() = allScopes.lastOrNull { it.irElement is IrSymbolOwner }?.irElement as? IrSymbolOwner

    val References = References(context)
}