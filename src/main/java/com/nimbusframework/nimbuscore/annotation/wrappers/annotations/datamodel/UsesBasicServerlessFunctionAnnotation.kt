package com.nimbusframework.nimbuscore.annotation.wrappers.annotations.datamodel

import com.nimbusframework.nimbuscore.annotation.annotations.function.UsesBasicServerlessFunction

class UsesBasicServerlessFunctionAnnotation(private val usesBasicServerlessFunctionAnnotation: UsesBasicServerlessFunction): DataModelAnnotation() {

    override val stages: Array<String> = usesBasicServerlessFunctionAnnotation.stages

    override fun internalDataModel(): Class<out Any> {
        return usesBasicServerlessFunctionAnnotation.targetClass.java
    }
}