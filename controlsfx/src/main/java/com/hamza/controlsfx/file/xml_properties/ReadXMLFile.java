package com.hamza.controlsfx.file.xml_properties;

import com.hamza.controlsfx.file.crypto.CryptoDatabaseConfig;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.HashMap;

public class ReadXMLFile {

    public static HashMap<String, String> readDatabaseConfig(String filePath) throws Exception {
        File fXmlFile = new File(filePath);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(fXmlFile);
        doc.getDocumentElement().normalize();
        System.out.println("Root element :" + doc.getDocumentElement().getNodeName());
        NodeList nList = doc.getElementsByTagName("DatabaseConfig");

        HashMap<String, String> map = new HashMap<>();
        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node nNode = nList.item(temp);
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;
                map.put(CryptoDatabaseConfig.URL, eElement.getElementsByTagName("url").item(0).getTextContent());
                map.put(CryptoDatabaseConfig.USERNAME, eElement.getElementsByTagName("username").item(0).getTextContent());
                map.put(CryptoDatabaseConfig.PASSWORD, eElement.getElementsByTagName("password").item(0).getTextContent());
                map.put(CryptoDatabaseConfig.DBNAME, eElement.getElementsByTagName("dbname").item(0).getTextContent());
                map.put(CryptoDatabaseConfig.PORT, eElement.getElementsByTagName("port").item(0).getTextContent());
                map.put(CryptoDatabaseConfig.KEY, eElement.getElementsByTagName("key").item(0).getTextContent());
            }
        }
        return map;
    }
}
