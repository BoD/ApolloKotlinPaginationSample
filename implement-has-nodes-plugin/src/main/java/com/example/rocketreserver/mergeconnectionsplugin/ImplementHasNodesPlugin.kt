package com.example.rocketreserver.mergeconnectionsplugin

import com.apollographql.apollo3.compiler.Plugin
import com.apollographql.apollo3.compiler.Transform
import com.apollographql.apollo3.compiler.codegen.kotlin.KotlinOutput
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

class ImplementHasNodesPlugin : Plugin {
    private val ApolloQueryTypeName = ClassName("com.apollographql.apollo3.api", "Query")
    private val HasNodesTypeName = ClassName("com.example.apollokotlinpaginationsample.repository", "HasNodes")

    private fun TypeName.firstTypeArgument() = (this as? ParameterizedTypeName)?.typeArguments?.firstOrNull()
    private fun TypeName.simpleName() = (this as? ClassName)?.simpleName
    private fun SimpleClassName(name: String) = ClassName("", name)

    override fun kotlinOutputTransform(): Transform<KotlinOutput>? {
        return object : Transform<KotlinOutput> {
            override fun transform(input: KotlinOutput): KotlinOutput {
                return KotlinOutput(
                    fileSpecs = input.fileSpecs.map { fileSpec ->
                        fileSpec.addHasNodesImplementation()
                    },
                    codegenMetadata = input.codegenMetadata
                )
            }
        }
    }

    private fun FileSpec.addHasNodesImplementation(): FileSpec {
        return toBuilder()
            .apply {
                members.replaceAll { member ->
                    if (member is TypeSpec && member.isQueryTypeSpec()) {
                        member.addHasNodesImplementation()
                    } else {
                        member
                    }
                }
            }
            .build()
    }

    private fun TypeSpec.addHasNodesImplementation(): TypeSpec {
        val dataTypeSpec = typeSpecs.first { it.name == "Data" }
        val connectionTypeSpec = typeSpecs.firstOrNull { it.isConnection() } ?: return this
        val nodeTypeName = connectionTypeSpec.propertySpecs.first { it.name == "nodes" }.type.firstTypeArgument()?.simpleName() ?: return this
        var parentTypeSpec: TypeSpec? = connectionTypeSpec
        val pathToConnection = mutableListOf<String>()
        while (true) {
            val (parentType, property) = typeSpecs.findTypeSpecWithPropertyOfType(parentTypeSpec!!.name!!) ?: break
            pathToConnection.add(0, property.name)
            parentTypeSpec = parentType
        }

        if (pathToConnection.isEmpty()) return this

        return this.toBuilder()
            .apply {
                typeSpecs.replaceAll { typeSpec ->
                    if (typeSpec == dataTypeSpec) {
                        dataTypeSpec.addHasNodesImplementation(nodeTypeName, pathToConnection)
                    } else {
                        typeSpec
                    }
                }
            }
            .build()
    }

    // data class Data (
    //   (...)
    // ) : HasNodes<Data, Node> {
    //    override fun getNodes(): List<Node> {
    //      return user.repositories.nodes.orEmpty().requireNoNulls()
    //    }
    //
    //    override fun updateNodes(nodes: List<Node>): Data {
    //      return copy(user = user.copy(repositories = user.repositories.copy(nodes = nodes)))
    //    }
    //  }
    private fun TypeSpec.addHasNodesImplementation(nodeTypeName: String, pathToConnection: List<String>): TypeSpec {
        val nodeType = SimpleClassName(nodeTypeName)
        return toBuilder()
            .addSuperinterface(HasNodesTypeName.parameterizedBy(SimpleClassName(name!!), nodeType))
            .addFunction(
                FunSpec.builder("getNodes")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(LIST.parameterizedBy(nodeType))
                    .addStatement(
                        "return %L.nodes.orEmpty().requireNoNulls()",
                        pathToConnection.joinToString(".")
                    )
                    .build()
            )
            .addFunction(
                FunSpec.builder("updateNodes")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("nodes", LIST.parameterizedBy(nodeType))
                    .returns(SimpleClassName(name!!))
                    .addStatement(
                        "return copy(%L)",
                        copyExpression(pathToConnection)
                    )
                    .build()
            )
            .build()
    }

    private fun copyExpression(pathToConnection: List<String>): String {
        var nested = "nodes = nodes"
        for (i in pathToConnection.lastIndex downTo 0) {
            val pathUpToElement = pathToConnection.subList(0, i + 1).joinToString(".")
            nested = "${pathToConnection[i]} = $pathUpToElement.copy($nested)"
        }
        return nested
    }

    private fun Collection<TypeSpec>.findTypeSpecWithPropertyOfType(typeName: String): Pair<TypeSpec, PropertySpec>? {
        return firstNotNullOfOrNull { typeSpec ->
            typeSpec.propertyOfType(typeName)?.let { propertySpec -> typeSpec to propertySpec }
        }
    }

    private fun TypeSpec.propertyOfType(typeName: String): PropertySpec? {
        return propertySpecs.firstOrNull {
            it.type.simpleName() == typeName
        }
    }

    private fun TypeSpec.isQueryTypeSpec(): Boolean {
        return superinterfaces.any { (typeName, _) ->
            (typeName as? ParameterizedTypeName)?.rawType == ApolloQueryTypeName
        }
    }

    private fun TypeSpec.isConnection(): Boolean {
        return propertySpecs.any { it.name == "pageInfo" } &&
                propertySpecs.any { it.name == "nodes" && (it.type as? ParameterizedTypeName)?.rawType == LIST }
    }
}
