package wrappers.notification.models

import com.fasterxml.jackson.annotation.JsonProperty
import wrappers.queue.models.QueueEvent

class RecordCollection {
    @JsonProperty(value = "Records")
    val records: List<QueueEvent>? = null
}