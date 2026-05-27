package com.hamza.account.security.annotation;

import com.hamza.account.type.PermissionCode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation للتحقق من وجود أي صلاحية من القائمة
 * 
 * مثال الاستخدام:
 * <pre>
 * {@code
 * @RequiresAnyPermission({PermissionCode.SALES_SHOW, PermissionCode.SALES_CREATE})
 * public void accessSalesModule() {
 *     // يحتاج إما صلاحية العرض أو الإضافة
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresAnyPermission {
    
    /**
     * قائمة الصلاحيات (يكفي واحدة منها)
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
