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

class CommandProcessor {

    private DBConnection pgcon;

    // Session data
    private boolean loggedIn = false;
    private long userId = -1;
    private String userName = "";

    private Map<String, Misc.CommandFunction> commands = Map.of(
            "user_login", this::cmdUserLogin,
            "bujdit_create", this::cmdBujditCreate,
            "bujdit_list", this::cmdBujditList,
            "bujdit_delete", this::cmdBujditDelete
    );

    CommandProcessor() throws SQLException {
        pgcon = new DBConnection();
    }

    void success(ObjectNode n) {
        n.put("success", true);
        n.put("code", Code.Success.code);
    }

    void error(ObjectNode n, Code c) {
        n.put("success", false);
        n.put("code", c.code);
    }

    JsonNode process(JsonNode reqRoot) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode resRoot = mapper.createObjectNode();

        String session = extractString(reqRoot.get("session"));
        if (!session.isEmpty()) {

            pgcon.beginTransaction();
            boolean transSuccess = true;
            try {

                ResultSet results = pgcon.query("SELECT user_id, users.name, activity FROM session INNER JOIN users ON user_id = users.id WHERE token = ?", session);
                if (!results.next()) {
                    error(resRoot, Code.ExpiredOrInvalidSession);
                    return resRoot;
                }
                this.userId = results.getLong(1);
                this.userName = results.getString(2);
                this.loggedIn = true;

                pgcon.update("UPDATE session SET activity = NOW() WHERE token = ?", session);

            } catch (SQLException ex) {
                transSuccess = false;
                error(resRoot, Code.ServerException);
                return resRoot;
            } finally {
                pgcon.endTransaction(transSuccess);
            }
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
            ObjectNode res = mapper.createObjectNode();
            resAry.add(res);
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

        ResultSet result = pgcon.query("SELECT id, salt, passhash FROM users WHERE LOWER(name) = LOWER(?)", username);
        if (!result.next()) { // username not found
            return Code.UsernameOrPasswordInvalid;
        }

        long id = result.getLong(1);
        String salt = result.getString(2);
        String passhash = result.getString(3);

        try {
            MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
            if (!passhash.equals(Misc.bytesToHex(sha512.digest((password + salt).getBytes())))) {
                return Code.UsernameOrPasswordInvalid;
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        byte[] token_bytes = new byte[32];
        new SecureRandom().nextBytes(token_bytes);
        String token = Misc.bytesToHex(token_bytes);

        pgcon.update("INSERT INTO session (user_id, token) VALUES (?, ?)", id, token);
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

        pgcon.beginTransaction();
        if (meta.isEmpty()) {
            rs = pgcon.query("INSERT INTO bujdit (name) VALUES (?) RETURNING id", name);
        } else {
            rs = pgcon.query("INSERT INTO bujdit (name, meta) VALUES (?, ?::JSON) RETURNING id", name, meta);
        }
        rs.next();
        pgcon.update("INSERT INTO bujdit_user (user_id, bujdit_id, permission) VALUES (?, ?, 4)", this.userId, rs.getInt(1));
        pgcon.endTransaction();

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
        ResultSet rs = pgcon.query(sqlstr, this.userId);

        ArrayNode bujdits = response.putArray("bujdits");
        while (rs.next()) {
            ObjectNode buj = bujdits.addObject();
            buj.put("id", rs.getLong(1));
            buj.put("name", rs.getString(2));
            buj.put("permission", rs.getString(3));
            if (includeMeta) {
                buj.set("meta", extractJSON(rs.getString(4)));
                buj.set("user_meta", extractJSON(rs.getString(5)));
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

        ResultSet rs = pgcon.query(sqlstr, id);
        if (!rs.next()) return Code.BujditNotFound;

        pgcon.update("DELETE FROM bujdit WHERE id = ?", id);
        return Code.Success;
    }

    // ================================================================================================================================
    // --------------------------------------------------------------------------------------------------------------------------------
    // ================================================================================================================================

    private static ObjectNode extractJSON(String str) {
        ObjectMapper mapper = new ObjectMapper();
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
}
