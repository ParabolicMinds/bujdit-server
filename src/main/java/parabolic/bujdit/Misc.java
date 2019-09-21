package parabolic.bujdit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.SQLException;
import java.util.function.BiFunction;

public class Misc {

    public interface CommandFunction extends BiFunction<JsonNode, ObjectNode, Code> {
        @Override
        default Code apply(JsonNode p1, ObjectNode p2) {
            try {
                return applyThrowing(p1, p2);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }

        Code applyThrowing(JsonNode p1, ObjectNode p2) throws SQLException;
    }

    // stolen from StackOverflow
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
