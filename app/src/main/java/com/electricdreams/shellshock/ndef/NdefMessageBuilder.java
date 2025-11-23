package com.electricdreams.shellshock.ndef;

import java.nio.charset.Charset;

/**
 * Builder for creating NDEF messages from text content
 */
public class NdefMessageBuilder {
    
    /**
     * Create an NDEF message from a string
     */
    public static byte[] createNdefMessage(String message) {
        byte[] languageCode = "en".getBytes(Charset.forName("US-ASCII"));
        byte[] textBytes = message.getBytes(Charset.forName("UTF-8"));
        byte statusByte = (byte) languageCode.length; // UTF-8 + language length
        
        // Create payload
        byte[] payload = new byte[1 + languageCode.length + textBytes.length];
        payload[0] = statusByte;
        System.arraycopy(languageCode, 0, payload, 1, languageCode.length);
        System.arraycopy(textBytes, 0, payload, 1 + languageCode.length, textBytes.length);
        
        // Create type
        byte[] type = "T".getBytes();
        
        // Create record header
        byte[] recordHeader = new byte[3 + type.length + payload.length];
        recordHeader[0] = (byte) 0xD1; // MB + ME + SR + TNF=1 (well-known)
        recordHeader[1] = (byte) type.length;
        recordHeader[2] = (byte) payload.length;
        System.arraycopy(type, 0, recordHeader, 3, type.length);
        System.arraycopy(payload, 0, recordHeader, 3 + type.length, payload.length);
        
        // Create full NDEF message
        int ndefLength = payload.length + 3 + type.length;
        byte[] fullMessage = new byte[2 + recordHeader.length];
        fullMessage[0] = (byte) ((ndefLength >> 8) & 0xFF);
        fullMessage[1] = (byte) (ndefLength & 0xFF);
        System.arraycopy(recordHeader, 0, fullMessage, 2, recordHeader.length);
        
        return fullMessage;
    }
}
