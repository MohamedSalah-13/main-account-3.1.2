package com.hamza.controlsfx.file.xml_properties;

import java.io.*;
import java.util.Properties;

public class PropertiesToXML {

    public PropertiesToXML(String inPropertiesFile, String outXmlFile) throws IOException {
//        String inPropertiesFile = "application.properties";
//        String outXmlFile = "applicationProperties.xml";

        InputStream inputStream = new FileInputStream(inPropertiesFile);
        OutputStream outputStream = new FileOutputStream(outXmlFile);

        Properties properties = new Properties();
        properties.load(inputStream);
        properties.storeToXML(outputStream, "application.properties", "UTF-8");
    }
}
