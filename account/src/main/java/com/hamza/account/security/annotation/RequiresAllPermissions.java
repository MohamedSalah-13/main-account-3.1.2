package com.hamza.account.security.annotation;

import com.hamza.account.type.PermissionCode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation للتحقق من وجود جميع الصلاحيات في القائمة
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresAllPermissions {
    
    /**
     * قائمة الصلاحيات (يجب توفرها جميعاً)
     */
    PermissionCode[] value();
    
    /**
     * رسالة مخصصة عند رفض الوصول
     */
    String message() default "";
    
    /**
     * هل يتم رفع استثناء
     */
    boolean throwException() default false;
}
