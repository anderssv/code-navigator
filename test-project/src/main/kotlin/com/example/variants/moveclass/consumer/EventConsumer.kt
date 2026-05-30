package com.example.variants.moveclass.consumer

import com.example.variants.moveclass.original.Event
import com.example.variants.moveclass.original.EventProcessor

class EventConsumer {
    private val processor = EventProcessor()

    fun handle(event: Event): String = processor.process(event)
}
