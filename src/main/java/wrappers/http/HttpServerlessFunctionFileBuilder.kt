package wrappers.http

import annotation.models.processing.MethodInformation
import wrappers.ServerlessFunctionFileBuilder
import wrappers.http.models.HttpEvent
import wrappers.http.models.LambdaProxyResponse
import java.io.PrintWriter
import javax.annotation.processing.ProcessingEnvironment
import javax.tools.Diagnostic

class HttpServerlessFunctionFileBuilder(
        private val processingEnv: ProcessingEnvironment,
        private val methodInformation: MethodInformation
): ServerlessFunctionFileBuilder(processingEnv, methodInformation) {

    override fun getGeneratedClassName(): String {
        return "HttpServerlessFunction${methodInformation.className}${methodInformation.methodName}"
    }

    fun createClass() {
        if (!customFunction()) {
            try {

                if (methodInformation.parameters.size > 2) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Not a valid http function handler (too many arguments)")
                }

                val inputParam = findInputTypeAndIndex()

                val builderFile = processingEnv.filer.createSourceFile(getGeneratedClassName())
                out = PrintWriter(builderFile.openWriter())

                val packageName = findPackageName(methodInformation.qualifiedName)

                if (packageName != "") write("package $packageName;")

                writeImports()

                write("public class ${getGeneratedClassName()} {")

                write()

                write("public void nimbusHandle(InputStream input, OutputStream output, Context context) {")

                write("ObjectMapper objectMapper = new ObjectMapper();")
                write("try {")

                writeInputs(inputParam)

                write("${methodInformation.className} handler = new ${methodInformation.className}();")

                writeFunction(inputParam)

                writeOutput()

                write("} catch (Exception e) {")

                writeHandleError()

                write("}")
                write("return;")


                write("}")

                write("}")

                out?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun writeImports() {
        write()

        write("import com.fasterxml.jackson.databind.ObjectMapper;")
        write("import com.amazonaws.services.lambda.runtime.Context;")
        write("import java.io.*;")
        write("import java.util.stream.Collectors;")
        if (methodInformation.qualifiedName.isNotBlank()) {
            write("import ${methodInformation.qualifiedName}.${methodInformation.className};")
        }
        write("import ${HttpEvent::class.qualifiedName};")
        write("import ${LambdaProxyResponse::class.qualifiedName};")

        write()
    }

    private fun writeInputs(inputParam: InputParam) {

        write("HttpEvent event = objectMapper.readValue(input, HttpEvent.class);")

        if (inputParam.type != null) {
            write("String body = event.getBody();")
            write("${inputParam.type} parsedType = objectMapper.readValue(body, ${inputParam.type}.class);")
        }

    }

    private fun writeFunction(inputParam: InputParam) {
        val callPrefix = if (methodInformation.returnType.toString() == "void") {
            ""
        } else {
            "${methodInformation.returnType} result = "
        }

        when {
            inputParam.type == null -> write("${callPrefix}handler.${methodInformation.methodName}(event);")
            inputParam.index == 0 -> write("${callPrefix}handler.${methodInformation.methodName}(parsedType, event);")
            else -> write("${callPrefix}handler.${methodInformation.methodName}(event, parsedType);")
        }
    }

    private fun writeOutput() {
        if (methodInformation.returnType.toString() != LambdaProxyResponse::class.qualifiedName) {
            write("LambdaProxyResponse response = new LambdaProxyResponse();")

            if (methodInformation.returnType.toString() != "void") {
                write("String resultString = objectMapper.writeValueAsString(result);")
                write("response.setBody(resultString);")
            }
        } else {
            write("LambdaProxyResponse response = result;")
        }

        write("String responseString = objectMapper.writeValueAsString(response);")
        write("PrintWriter writer = new PrintWriter(output);")
        write("writer.print(responseString);")
        write("writer.close();")
        write("output.close();")
    }

    private fun writeHandleError() {
        write("e.printStackTrace();")

        write("try {")
        write("LambdaProxyResponse errorResponse = LambdaProxyResponse.Companion.serverErrorResponse();")
        write("String responseString = objectMapper.writeValueAsString(errorResponse);")

        write("PrintWriter writer = new PrintWriter(output);")
        write("writer.print(responseString);")
        write("writer.close();")
        write("output.close();")

        write("} catch (IOException e2) {")
        write("e2.printStackTrace();")
        write("}")
    }
}