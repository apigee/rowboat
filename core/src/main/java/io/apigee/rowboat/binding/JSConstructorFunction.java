package io.apigee.rowboat.binding;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put this annotation on any method that should be bound to a class constructor. In other words, this is the
 * opposite of a prototype function.
 */

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JSConstructorFunction
{
    String value() default "";
}

