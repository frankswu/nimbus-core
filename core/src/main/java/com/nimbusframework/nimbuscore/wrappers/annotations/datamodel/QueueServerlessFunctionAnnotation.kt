package com.nimbusframework.nimbuscore.wrappers.annotations.datamodel

import com.nimbusframework.nimbuscore.annotations.function.QueueServerlessFunction

class QueueServerlessFunctionAnnotation(private val queueServerlessFunction: QueueServerlessFunction): DataModelAnnotation() {

    override val stages = queueServerlessFunction.stages

    override fun internalDataModel(): Class<out Any> {
        return queueServerlessFunction.queue.java
    }

}
