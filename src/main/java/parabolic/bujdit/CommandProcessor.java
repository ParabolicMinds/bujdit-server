package parabolic.bujdit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.imageio.IIOException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.BiFunction;

class CommandProcessor {

    private DBConnection dbcon;
    private ObjectMapper mapper = new ObjectMapper();

    // Session data
    private boolean loggedIn = false;
    private long userId = -1;
    private String userName = "";

    private interface CommandFunction extends BiFunction<JsonNode, ObjectNode, Code> {
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

    private Map<String, CommandFunction> commands = Map.of(
            "user_login", this::cmdUserLogin,
            "bujdit_create", this::cmdBujditCreate,
            "bujdit_list", this::cmdBujditList,
            "bujdit_delete", this::cmdBujditDelete,
            "bujdit_meta_get", this::cmdBujditMetaGet,
            "bujdit_meta_set", this::cmdBujditMetaSet
    );

    CommandProcessor() {}

    private void success(ObjectNode n) {
        n.put("success", true);
        n.put("code", Code.Success.code);
    }

    private void error(ObjectNode n, Code c) {
        n.put("success", false);
        n.put("code", c.code);
    }

    JsonNode process(JsonNode reqRoot) {
        ObjectNode resRoot = mapper.createObjectNode();

        try (DBConnection dbcon = new DBConnection()) {
            this.dbcon = dbcon; // I hate this Java try-with-resources garbage

            String session = extractString(reqRoot.get("session"));
            if (!session.isEmpty()) {

                dbcon.beginTransaction();
                boolean transSuccess = true;

                ResultSet results = dbcon.query("SELECT user_id, users.name, activity FROM session INNER JOIN users ON user_id = users.id WHERE token = ?", session);
                if (!results.next()) {
                    error(resRoot, Code.ExpiredOrInvalidSession);
                    return resRoot;
                }
                this.userId = results.getLong(1);
                this.userName = results.getString(2);
                this.loggedIn = true;

                dbcon.update("UPDATE session SET activity = NOW() WHERE token = ?", session);
                dbcon.endTransaction();
            }

            ObjectNode userInfo = resRoot.putObject("user_info");
            userInfo.put("logged_in", this.loggedIn);
            if (this.loggedIn) {
                userInfo.put("user_id", this.userId);
                userInfo.put("username", this.userName);
            }

            JsonNode cmds = reqRoot.get("cmds");
            if (cmds == null || !cmds.isArray()) {
                success(resRoot);
                return resRoot;
            }

            ArrayNode resAry = resRoot.putArray("res");

            for (final JsonNode cmd : cmds) {
                ObjectNode res = resAry.addObject();
                String cmd_str = extractString(cmd.get("cmd"));

                if (!commands.containsKey(cmd_str)) {
                    this.error(res, Code.CommandNotFound);
                    continue;
                }

                try {
                    Code c = commands.get(cmd_str).applyThrowing(cmd, res);
                    if (c == Code.Success) success(res);
                    else error(res, c);
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    error(res, Code.ServerException);
                }
            }

            success(resRoot);
            return resRoot;

        } catch (SQLException ex) {
            ObjectNode errNode = mapper.createObjectNode();
            error(errNode, Code.ServerException);
            return errNode;
        }
    }

    // ================================================================================================================================
    // USER_LOGIN
    // ================================================================================================================================

    private Code cmdUserLogin(JsonNode cmd, ObjectNode response) throws SQLException {
        String username = extractString(cmd.get("username"));
        String password = extractString(cmd.get("password"));

        if (username.isEmpty() || password.isEmpty()) {
            return Code.MissingRequiredField;
        }

        ResultSet result = dbcon.query("SELECT id, salt, passhash FROM users WHERE LOWER(name) = LOWER(?)", username);
        if (!result.next()) { // username not found
            return Code.UsernameOrPasswordInvalid;
        }

        long id = result.getLong(1);
        String salt = result.getString(2);
        String passhash = result.getString(3);

        try {
            MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
            if (!passhash.equals(bytesToHex(sha512.digest((password + salt).getBytes())))) {
                return Code.UsernameOrPasswordInvalid;
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        byte[] token_bytes = new byte[32];
        new SecureRandom().nextBytes(token_bytes);
        String token = bytesToHex(token_bytes);

        dbcon.update("INSERT INTO session (user_id, token) VALUES (?, ?)", id, token);
        response.put("token", token);

        return Code.Success;
    }

    // ================================================================================================================================
    // BUJDIT_CREATE
    // ================================================================================================================================

    private Code cmdBujditCreate(JsonNode cmd, ObjectNode response) throws SQLException {
        if (!loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        String name = extractString(cmd.get("name"));
        String meta = extractString(cmd.get("meta"));
        ResultSet rs;

        if (name.isEmpty()) {
            return Code.MissingRequiredField;
        }

        dbcon.beginTransaction();
        if (meta.isEmpty()) {
            rs = dbcon.query("INSERT INTO bujdit (name) VALUES (?) RETURNING id", name);
        } else {
            rs = dbcon.query("INSERT INTO bujdit (name, meta) VALUES (?, ?::JSON) RETURNING id", name, meta);
        }
        rs.next();
        dbcon.update("INSERT INTO bujdit_user (user_id, bujdit_id, permission) VALUES (?, ?, 4)", this.userId, rs.getInt(1));
        dbcon.endTransaction();

        return Code.Success;
    }

    // ================================================================================================================================
    // BUJDIT_LIST
    // ================================================================================================================================

    private Code cmdBujditList(JsonNode cmd, ObjectNode response) throws SQLException {
        if (!loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        boolean includeMeta = extractBoolean(cmd.get("include_meta"), false);

        String sqlstr =
            "SELECT id, name, permission" + (includeMeta ? ", bujdit.meta AS meta, bujdit_user.meta AS user_meta" : "") +
            " FROM bujdit"+
            " INNER JOIN bujdit_user ON bujdit.id = bujdit_id"+
            " WHERE user_id = ? AND permission >= 1"+
            " ORDER BY id ASC";
        ResultSet rs = dbcon.query(sqlstr, this.userId);

        ArrayNode bujdits = response.putArray("bujdits");
        while (rs.next()) {
            ObjectNode buj = bujdits.addObject();
            buj.put("id", rs.getLong(1));
            buj.put("name", rs.getString(2));
            buj.put("permission", rs.getString(3));
            if (includeMeta) {
                buj.set("meta", String2JSON(rs.getString(4)));
                buj.set("user_meta", String2JSON(rs.getString(5)));
            }
        }

        return Code.Success;
    }

    // ================================================================================================================================
    // BUJDIT_DELETE
    // ================================================================================================================================

    private Code cmdBujditDelete(JsonNode cmd, ObjectNode response) throws SQLException {
        if (!loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        long id = extractLong(cmd.get("id"), -1);
        if (id == -1) return Code.MissingRequiredField;

        String sqlstr =
            "SELECT id FROM bujdit"+
            " INNER JOIN bujdit_user ON bujdit.id = bujdit_id"+
            " WHERE bujdit_id = ? AND permission >= 4";

        ResultSet rs = dbcon.query(sqlstr, id);
        if (!rs.next()) return Code.NotFoundOrInsufficientPermissions;

        dbcon.update("DELETE FROM bujdit WHERE id = ?", id);
        return Code.Success;
    }

    // ================================================================================================================================
    // BUJDIT_META_GET
    // ================================================================================================================================

    private Code cmdBujditMetaGet(JsonNode cmd, ObjectNode response) throws SQLException {
        if (!loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        long id = extractLong(cmd.get("id"), -1);
        if (id == -1) return Code.MissingRequiredField;

        String field = extractString(cmd.get("field"));

        String sqlstr =
            "SELECT bujdit.meta AS meta"+
            " FROM bujdit"+
            " INNER JOIN bujdit_user ON bujdit.id = bujdit_id"+
            " WHERE user_id = ? AND bujdit_id = ? AND permission >= 1";

        ResultSet rs = dbcon.query(sqlstr, userId, id);
        if (!rs.next()) return Code.NotFoundOrInsufficientPermissions;

        JsonNode meta = String2JSON(rs.getString(1));

        if (field.isEmpty()) {
            response.set("meta", meta);
            return Code.Success;
        }

        JsonNode fieldNode = meta.get(field);
        response.set("meta", fieldNode == null ? mapper.createObjectNode().nullNode() : fieldNode);
        return Code.Success;
    }

    // ================================================================================================================================
    // BUJDIT_META_SET
    // ================================================================================================================================

    private Code cmdBujditMetaSet(JsonNode cmd, ObjectNode response) throws SQLException {
        if (!loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        long id = extractLong(cmd.get("id"), -1);
        String metaStr = extractString(cmd.get("meta"));
        if (id == -1 || metaStr.isEmpty()) return Code.MissingRequiredField;

        JsonNode meta;
        try {
            meta = mapper.readTree(metaStr);
        } catch (IOException e) {
            return Code.InvalidFieldFormat;
        }

        String field = extractString(cmd.get("field"));

        String sqlstr =
            "SELECT bujdit.meta AS meta"+
            " FROM bujdit"+
            " INNER JOIN bujdit_user ON bujdit.id = bujdit_id"+
            " WHERE user_id = ? AND bujdit_id = ? AND permission >= 2";

        ResultSet rs = dbcon.query(sqlstr, userId, id);
        if (!rs.next()) return Code.NotFoundOrInsufficientPermissions;

        JsonNode metaSet;

        if (field.isEmpty()) {
            metaSet = meta;
        } else {
            ObjectNode metaGet = String2JSON(rs.getString(1));
            metaGet.set(field, meta);
            metaSet = metaGet;
        }

        sqlstr =
            "UPDATE bujdit"+
            " SET meta = ?::JSON"+
            " WHERE id = ?";
        dbcon.update(sqlstr, metaSet, id);

        return Code.Success;
    }

    // ================================================================================================================================
    // --------------------------------------------------------------------------------------------------------------------------------
    // ================================================================================================================================

    private ObjectNode String2JSON(String str) {
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

    private static boolean extractBoolean(JsonNode node, boolean def) {
        if (node == null || node.isNull()) return def;
        else return node.asBoolean(def);
    }

    private static String extractString(JsonNode node) {
        if (node == null || node.isNull()) return "";
        if (!node.isTextual()) return node.toString();
        else return node.asText("");
    }

    private static long extractLong(JsonNode node, long def) {
        if (node == null || node.isNull()) return def;
        else return node.asLong(def);
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
