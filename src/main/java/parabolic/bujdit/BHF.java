package parabolic.bujdit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

public class BHF { // Bujdit Helper Functions

    public static boolean extractBoolean(JsonNode node, boolean def) {
        if (node == null || node.isNull()) return def;
        else return node.asBoolean(def);
    }

    public static String extractString(JsonNode node) {
        if (node == null || node.isNull()) return "";
        if (!node.isTextual()) return node.toString();
        else return node.asText("");
    }

    public static long extractLong(JsonNode node, long def) {
        if (node == null || node.isNull()) return def;
        else return node.asLong(def);
    }

    private static final ObjectMapper mapper = new ObjectMapper();
    public static ObjectNode String2JSON(String str) {
        if (str == null || str.isEmpty()) {
            return mapper.createObjectNode();
        } else {
            try {
                return (ObjectNode)mapper.readTree(str);
            } catch (IOException e) {
                e.printStackTrace();
                return mapper.createObjectNode();
            }
        }
    }

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

}
