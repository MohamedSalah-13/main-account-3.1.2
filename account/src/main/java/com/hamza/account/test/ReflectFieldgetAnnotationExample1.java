package com.hamza.account.test;

import java.lang.annotation.*;
import java.lang.reflect.Field;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface CustomAnnotations1 {
    public String name();

    public String value();
}

public class ReflectFieldgetAnnotationExample1 {

    public static void main(String[] args) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Field fld1 = SampleAnnotation.class.getField("Field");
        Annotation annotation = SampleAnnotation.class.getAnnotation(CustomAnnotations1.class);
        if (annotation instanceof CustomAnnotations1) {
            CustomAnnotations1 cAnn = (CustomAnnotations1) annotation;
            System.out.println("name  : " + cAnn.name());
            System.out.println("value  : " + cAnn.value());
        }
    }
}

@CustomAnnotations1(name = "fghfghgfhfgh", value = "Sample hgfhghfghfg Annotation")
class SampleAnnotation {

    //    @CustomAnnotations1(name="sampleClassField",  value = "Sample Field Annotation")
    public String Field;

    public String getField() {
        return Field;
    }

    public void setField(String sampleField) {
        this.Field = Field;
    }
}
