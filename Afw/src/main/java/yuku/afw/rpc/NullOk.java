package yuku.afw.rpc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Just to document that the parameter may be null */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PARAMETER)
public @interface NullOk {
}
