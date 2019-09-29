package parabolic.bujdit.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import parabolic.bujdit.BHF;
import parabolic.bujdit.Code;
import parabolic.bujdit.DB.Connection;
import parabolic.bujdit.RequestPersist;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ShnoppingCreate implements ICommand {

    @Override
    public Code execute(RequestPersist pers, Connection dbcon, JsonNode cmd, ObjectNode response) throws SQLException {
        if (!pers.loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        String name = BHF.extractString(cmd.get("name"));
        String meta = BHF.extractString(cmd.get("meta"));
        ResultSet rs;

        if (name.isEmpty()) {
            return Code.MissingRequiredField;
        }

        dbcon.beginTransaction();
        if (meta.isEmpty()) {
            rs = dbcon.query("INSERT INTO shnopping (name) VALUES (?) RETURNING id", name);
        } else {
            rs = dbcon.query("INSERT INTO shnopping (name, meta) VALUES (?, ?::JSON) RETURNING id", name, meta);
        }
        rs.next();
        response.put("id", rs.getLong(1));
        dbcon.update("INSERT INTO shnopping_user (user_id, shnopping_id, permission) VALUES (?, ?, 4)", pers.userId, rs.getInt(1));
        dbcon.endTransaction();

        return Code.Success;
    }
}
