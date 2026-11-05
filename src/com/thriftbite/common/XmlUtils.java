package com.thriftbite.common;

import org.xml.sax.InputSource;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Serializable;
import java.util.List;

public class XmlUtils implements Serializable {

    public static String toXml(Object object) {
        StringWriter stringWriter = new StringWriter();

        OutputStream charToByteAdapter = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                stringWriter.write(b);
            }
        };

        try (XMLEncoder encoder = new XMLEncoder(charToByteAdapter)) {
            encoder.writeObject(object);
        }

        return stringWriter.toString()
                .replace("\n", " ")
                .replace("\r", "");
    }

    public static <T> T fromXml(String xml, Class<T> clazz) {
        if (xml == null || xml.isEmpty()) return null;

        try (StringReader reader = new StringReader(xml);
             XMLDecoder decoder = new XMLDecoder(new InputSource(reader))) {

            Object obj = decoder.readObject();
            return clazz.cast(obj);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> fromXmlList(String xml, Class<T> clazz) {
        if (xml == null || xml.isEmpty()) return null;

        try (StringReader reader = new StringReader(xml);
             XMLDecoder decoder = new XMLDecoder(new InputSource(reader))) {

            Object obj = decoder.readObject();
            return (List<T>) obj;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}