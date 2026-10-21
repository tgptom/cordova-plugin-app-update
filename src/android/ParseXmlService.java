package com.vaenow.appupdate.android;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.InputStream;
import java.util.HashMap;

/**
 * Created by LuoWen on 2015/10/27.
 */
public class ParseXmlService {
    public HashMap<String, String> parseXml(InputStream inStream) throws Exception {
        HashMap<String, String> hashMap = new HashMap<String, String>();

        XmlPullParser parser = Xml.newPullParser();
        // Android's factory enables DTD processing by default.
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
        if (parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL)) {
            throw new XmlPullParserException("DTD processing must be disabled");
        }
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(inStream, null);

        boolean rootSeen = false;
        String field = null;
        StringBuilder text = new StringBuilder();
        int event;
        // next() hides declarations and unresolved entities; inspect every token instead.
        while ((event = parser.nextToken()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.DOCDECL) {
                throw new XmlPullParserException("DOCTYPE is not allowed");
            }
            if (event == XmlPullParser.ENTITY_REF && parser.getText() == null) {
                throw new XmlPullParserException("Unresolved entity is not allowed");
            }
            if (event == XmlPullParser.START_TAG) {
                if (parser.getDepth() == 1) {
                    if (rootSeen) {
                        throw new XmlPullParserException("Multiple root elements");
                    }
                    rootSeen = true;
                }
                if (parser.getDepth() == 2) {
                    String name = parser.getName();
                    field = "version".equals(name) || "name".equals(name) || "url".equals(name)
                            ? name : null;
                    text.setLength(0);
                }
            } else if (event == XmlPullParser.END_TAG && parser.getDepth() == 2) {
                if (field != null) {
                    String value = text.toString().trim();
                    if (!value.isEmpty()) {
                        hashMap.put(field, value);
                    }
                }
                field = null;
            } else if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT
                    || event == XmlPullParser.ENTITY_REF
                    || event == XmlPullParser.IGNORABLE_WHITESPACE) {
                if (parser.getDepth() == 0 && (event == XmlPullParser.ENTITY_REF
                        || event == XmlPullParser.CDSECT || !parser.isWhitespace())) {
                    throw new XmlPullParserException("Text outside root element");
                }
                if (field != null) {
                    text.append(parser.getText());
                }
            }
        }
        if (!rootSeen || parser.getDepth() != 0) {
            throw new XmlPullParserException("Incomplete XML document");
        }
        return hashMap;
    }
}