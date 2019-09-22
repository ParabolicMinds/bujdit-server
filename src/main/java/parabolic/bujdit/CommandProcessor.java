package parabolic.bujdit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import parabolic.bujdit.commands.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

class CommandProcessor {

    private ObjectMapper mapper = new ObjectMapper();

    private Map<String, Class<? extends ICommand>> commands = Map.of (
            "user_login", UserLogin.class,
            "bujdit_create", BujditCreate.class,
            "bujdit_list", BujditList.class,
            "bujdit_delete", BujditDelete.class,
            "bujdit_meta_get", BujditMetaGet.class,
            "bujdit_meta_set", BujditMetaSet.class,
            "bujdit_user_meta_get", BujditUserMetaGet.class,
            "bujdit_user_meta_set", BujditUserMetaSet.class
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

            RequestPersist pers = new RequestPersist();

            String session = BHF.extractString(reqRoot.get("session"));
            if (!session.isEmpty()) {

                dbcon.beginTransaction();
                boolean transSuccess = true;

                ResultSet results = dbcon.query("SELECT user_id, users.name, activity FROM session INNER JOIN users ON user_id = users.id WHERE token = ?", session);
                if (!results.next()) {
                    error(resRoot, Code.ExpiredOrInvalidSession);
                    return resRoot;
                }
                pers.userId = results.getLong(1);
                pers.userName = results.getString(2);
                pers.loggedIn = true;

                dbcon.update("UPDATE session SET activity = NOW() WHERE token = ?", session);
                dbcon.endTransaction();
            }

            ObjectNode userInfo = resRoot.putObject("user_info");
            userInfo.put("logged_in", pers.loggedIn);
            if (pers.loggedIn) {
                userInfo.put("user_id", pers.userId);
                userInfo.put("username", pers.userName);
            }

            JsonNode cmds = reqRoot.get("cmds");
            if (cmds == null || !cmds.isArray()) {
                success(resRoot);
                return resRoot;
            }

            ArrayNode resAry = resRoot.putArray("res");

            for (final JsonNode cmd : cmds) {
                ObjectNode res = resAry.addObject();
                String cmd_str = BHF.extractString(cmd.get("cmd"));

                if (!commands.containsKey(cmd_str)) {
                    this.error(res, Code.CommandNotFound);
                    continue;
                }

                try {
                    ICommand cmdInst = commands.get(cmd_str).getDeclaredConstructor().newInstance();
                    Code c = cmdInst.execute(pers, dbcon, cmd, res);
                    if (c == Code.Success) success(res);
                    else error(res, c);
                } catch (SQLException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
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
}
