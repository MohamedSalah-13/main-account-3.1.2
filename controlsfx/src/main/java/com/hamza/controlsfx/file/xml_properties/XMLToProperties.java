package com.hamza.controlsfx.file.xml_properties;

import java.io.*;
import java.util.Properties;

public class XMLToProperties {

    public XMLToProperties(String inXmlFile, String outPropertiesFile) throws IOException {
//        String outPropertiesFile = "application.properties";
//        String inXmlFile = "applicationProperties.xml";

        InputStream inStream = new FileInputStream(inXmlFile);      //Input XML File
        OutputStream outStream = new FileOutputStream(outPropertiesFile); //Output properties File

        Properties props = new Properties();

        //Load XML file
        props.loadFromXML(inStream);

        //Store to properties file
        props.store(outStream, "Converted from applicationProperties.xml");

        //Use properties in code
        System.out.println(props.get("input.dir"));     //Prints 'c:/temp/input'
    }
}