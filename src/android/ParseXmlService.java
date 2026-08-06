package com.vaenow.appupdate.android;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashMap;

/**
 * Created by LuoWen on 2015/10/27.
 */
public class ParseXmlService {
    public HashMap<String, String> parseXml(InputStream inStream) throws Exception {
        HashMap<String, String> hashMap = new HashMap<String, String>();

        // Create a document builder factory
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Get a document builder from the factory
        DocumentBuilder builder = factory.newDocumentBuilder();
        // Parse the input stream into a document
        Document document = builder.parse(inStream);
        // Get the XML root element
        Element root = document.getDocumentElement();
        // Get all child nodes
        NodeList childNodes = root.getChildNodes();
        for (int j = 0; j < childNodes.getLength(); j++) {
            // Iterate over the child nodes
            Node childNode = (Node) childNodes.item(j);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) childNode;
                // Version code
                if ("version".equals(childElement.getNodeName())) {
                    hashMap.put("version", childElement.getFirstChild().getNodeValue());
                }
                // Application name
                else if (("name".equals(childElement.getNodeName()))) {
                    hashMap.put("name", childElement.getFirstChild().getNodeValue());
                }
                // Download URL
                else if (("url".equals(childElement.getNodeName()))) {
                    hashMap.put("url", childElement.getFirstChild().getNodeValue());
                }
            }
        }
        return hashMap;
    }
}