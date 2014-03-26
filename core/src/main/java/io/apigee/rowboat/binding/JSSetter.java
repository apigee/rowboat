package io.apigee.rowboat.binding;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put this annotation on any method that serves as a setter for a property. The parameter must be an Object,
 * JSObject, String, or numeric type.
 */

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JSSetter
{
    String value() default "";
}

