package io.github.yuemenglong.orm.lang.anno;

import io.github.yuemenglong.orm.lang.Def;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {
    String name() default "";

    boolean nullable() default true;

    int length() default 255;

    int precision() default 0;

    int scale() default 0;

    String defaultValue() default Def.NONE_DEFAULT_VALUE;
}
//(precision, scale)
