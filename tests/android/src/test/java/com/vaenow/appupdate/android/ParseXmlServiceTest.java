package com.vaenow.appupdate.android;

import org.junit.Test;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ParseXmlServiceTest {
    private HashMap<String, String> parse(String xml) throws Exception {
        return new ParseXmlService().parseXml(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void parsesRepositoryExample() throws Exception {
        try (InputStream input = new java.io.FileInputStream("../../res/xml/version.xml")) {
            HashMap<String, String> result = new ParseXmlService().parseXml(input);
            assertEquals(3, result.size());
            assertEquals("2222", result.get("version"));
            assertEquals("name", result.get("name"));
            assertEquals("https://192.168.3.102/android.apk", result.get("url"));
        }
    }

    @Test
    public void preservesTextContentTrimmingAndDuplicateSemantics() throws Exception {
        HashMap<String, String> result = parse("<?xml version='1.0'?><update>"
                + "<version> 12 </version><version> </version>"
                + "<name>old</name><name> App <!-- ignored -->"
                + "<b>New</b><![CDATA[ <release>]]> &amp; &#65;&#x42; &lt;&gt;&quot;&apos; </name>"
                + "<url> https://example.com/app.apk?a=1&amp;b=2 </url>"
                + "<unknown><version>999</version></unknown></update>");
        assertEquals(3, result.size());
        assertEquals("12", result.get("version"));
        assertEquals("App New <release> & AB <>\"'", result.get("name"));
        assertEquals("https://example.com/app.apk?a=1&b=2", result.get("url"));
    }

    @Test
    public void preservesQualifiedNamesAndAllowsMissingOrEmptyFields() throws Exception {
        HashMap<String, String> result = parse("<update xmlns:p='urn:test'>"
                + "<p:version>999</p:version><name/><url> \n </url></update>");
        assertEquals(0, result.size());
        assertEquals("12", parse("<update xmlns='urn:test'><version>12</version></update>")
                .get("version"));
    }

    @Test
    public void honorsDeclaredInputEncoding() throws Exception {
        String xml = "<?xml version='1.0' encoding='UTF-16'?><update><name>Café</name></update>";
        assertEquals("Café", new ParseXmlService().parseXml(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_16))).get("name"));
    }

    @Test
    public void rejectsDoctypesAndEntityDeclarations() {
        String[] declarations = {
                "<!DOCTYPE update>",
                "<!DOCTYPE update SYSTEM 'http://127.0.0.1:9/external.dtd'>",
                "<!DOCTYPE update PUBLIC '-//TEST//DTD Update//EN' 'file:///nonexistent.dtd'>",
                "<!DOCTYPE update [<!ENTITY value 'injected'>]>",
                "<!DOCTYPE update [<!ENTITY value SYSTEM 'file:///etc/passwd'>]>",
                "<!DOCTYPE update [<!ENTITY % remote SYSTEM 'http://127.0.0.1:9/evil.dtd'>%remote;]>",
                "<!DOCTYPE update [<!ENTITY a 'large'><!ENTITY b '&a;&a;&a;&a;'>]>"
        };
        for (String declaration : declarations) {
            assertThrows(declaration, XmlPullParserException.class,
                    () -> parse(declaration + "<update><name>&value;</name></update>"));
        }
    }

    @Test
    public void rejectsUnresolvedEntitiesEvenInIgnoredContent() {
        String[] documents = {
                "<update><version>1&missing;2</version></update>",
                "<update><ignored>&missing;</ignored></update>",
                "<update><version>12</version></update>&missing;"
        };
        for (String xml : documents) {
            assertThrows(xml, XmlPullParserException.class, () -> parse(xml));
        }
    }

    @Test
    public void rejectsMalformedOrIncompleteDocuments() {
        String[] documents = {
                "", " ", "<!-- no root -->", "<update><version>12</version>",
                "<update><version>12</name></update>", "<update/><update/>",
                "text<update/>", "<update/>text", "<update/>&amp;",
                "<![CDATA[ ]]><update/>", "<update/><!DOCTYPE update>"
        };
        for (String xml : documents) {
            assertThrows(xml, XmlPullParserException.class, () -> parse(xml));
        }
    }
}
