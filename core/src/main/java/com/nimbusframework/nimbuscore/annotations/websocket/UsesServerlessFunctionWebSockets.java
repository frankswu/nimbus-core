package com.nimbusframework.nimbuscore.annotations.websocket;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UsesServerlessFunctionWebSockets {
    UsesServerlessFunctionWebSocket[] value();
}
