package com.hamza.account.security.annotation;

import com.hamza.account.type.PermissionCode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation للتحقق من الصلاحيات على مستوى Method
 * 
 * مثال الاستخدام:
 * <pre>
 * {@code
 * @RequiresPermission(PermissionCode.SALES_CREATE)
 * public void createSalesInvoice() {
 *     // سيتم التحقق من الصلاحية تلقائياً قبل التنفيذ
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    
    /**
     * الصلاحية المطلوبة
     */
    PermissionCode value();
    
    /**
     * رسالة مخصصة عند رفض الوصول (اختياري)
     */
    String message() default "";
    
    /**
     * هل يتم رفع استثناء أم عرض رسالة فقط
     */
    boolean throwException() default false;
}
