package com.hamza.controlsfx.serial;

import java.io.*;

public class SerializationText {

    public static final String SERIAL_SER = "serial.ser";

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        AppleProduct appleProduct = new AppleProduct();
        appleProduct.setHeadphonePort("Mohamed Salah");
        appleProduct.setThunderboltPort("Lamya Mohamed");

        serialized(appleProduct);
        deSerialized();

//        String s = SerializationUtility.serializeObjectToString(appleProduct);
//        SerializationUtility.writeData(s);

    }

    private static void serialized(Serializable serializable) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(SERIAL_SER);
        ObjectOutputStream oos = new ObjectOutputStream(fileOutputStream);
        oos.writeObject(serializable);
        oos.flush();
        oos.close();
    }

    private static void deSerialized() throws IOException, ClassNotFoundException {
        FileInputStream fileInputStream = new FileInputStream(SERIAL_SER);
        ObjectInputStream ois = new ObjectInputStream(fileInputStream);
        AppleProduct appleProduct = (AppleProduct) ois.readObject();

        System.out.println(appleProduct.getHeadphonePort());
        System.out.println(appleProduct.getThunderboltPort());
        ois.close();
    }
}
