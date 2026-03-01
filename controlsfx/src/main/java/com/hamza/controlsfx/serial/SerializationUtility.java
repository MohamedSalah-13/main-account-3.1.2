package com.hamza.controlsfx.serial;

import java.io.*;
import java.util.Base64;

public class SerializationUtility {

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        AppleProduct appleProduct = new AppleProduct();
//        appleProduct.headphonePort = "Mohamed Salah";
//        appleProduct.thunderboltPort = "20202";
//        appleProduct.name="Hamza";

//        String s="rO0ABXNyACVjb20uaGFtemEuYWNjb3VudC5zZXJpYWwuQXBwbGVQcm9kdWN0AAAAAAAS1ocCAAJMAA1oZWFkcGhvbmVQb3J0dAASTGphdmEvbGFuZy9TdHJpbmc7TAAPdGh1bmRlcmJvbHRQb3J0cQB+AAF4cHQADU1vaGFtZWQgU2FsYWh0AAUyMDIwMg==";
//        String s="rO0ABXNyACVjb20uaGFtemEuYWNjb3VudC5zZXJpYWwuQXBwbGVQcm9kdWN0AAAAAAAS1ocCAANMAA1oZWFkcGhvbmVQb3J0dAASTGphdmEvbGFuZy9TdHJpbmc7TAAEbmFtZXEAfgABTAAPdGh1bmRlcmJvbHRQb3J0cQB+AAF4cHQADU1vaGFtZWQgU2FsYWh0AAVIYW16YXQABTIwMjAy";
        String serializeObj = serializeObjectToString(appleProduct);
        System.out.println(serializeObj);

        System.out.println(
                "Deserializing AppleProduct...");

        AppleProduct deserializedObj = (AppleProduct) deSerializeObjectFromString(serializeObj);

        System.out.println(
                "Headphone port of AppleProduct:"
                        + deserializedObj.getHeadphonePort());
        System.out.println("Thunderbolt port of AppleProduct:" + deserializedObj.getThunderboltPort());
//        System.out.println("Name port of AppleProduct:" + deserializedObj.getName());
        writeData(serializeObj);
    }

    public static String serializeObjectToString(Serializable serializable) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
        objectOutputStream.writeObject(serializable);
        objectOutputStream.close();

        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    public static Object deSerializeObjectFromString(String s)
            throws IOException, ClassNotFoundException {

        byte[] data = Base64.getDecoder().decode(s);
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    public static void writeData(Serializable serializable) throws IOException {
        OutputStream outStream = new FileOutputStream("nama.txt");
        ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutStream);
        objectOutputStream.writeObject(serializable);
        byteOutStream.writeTo(outStream);
        objectOutputStream.close();
        outStream.close();

    }

    public void getBytes(String fileName) throws IOException {
        byte[] buffer = new byte[4096];
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(
                fileName));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int bytes = 0;
        while ((bytes = bis.read(buffer, 0, buffer.length)) > 0) {
            baos.write(buffer, 0, bytes);
        }
        baos.close();
        bis.close();
    }
}
