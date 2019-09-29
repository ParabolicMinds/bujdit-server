package parabolic.bujdit.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import parabolic.bujdit.BHF;
import parabolic.bujdit.Code;
import parabolic.bujdit.DB.Connection;
import parabolic.bujdit.RequestPersist;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ShnoppingUserMetaGet implements ICommand {

    @Override
    public Code execute(RequestPersist pers, Connection dbcon, JsonNode cmd, ObjectNode response) throws SQLException {
        if (!pers.loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        long id = BHF.extractLong(cmd.get("id"), -1);
        if (id == -1) return Code.MissingRequiredField;

        String field = BHF.extractString(cmd.get("field"));

        String sqlstr =
            "SELECT meta"+
            " FROM shnopping_user"+
            " WHERE user_id = ? AND shnopping_id = ?";

        ResultSet rs = dbcon.query(sqlstr, pers.userId, id);
        if (!rs.next()) return Code.NotFoundOrInsufficientAccess;

        JsonNode meta = BHF.String2JSON(rs.getString(1));

        if (field.isEmpty()) {
            response.set("meta", meta);
            return Code.Success;
        }

        JsonNode fieldNode = meta.get(field);
        response.set("meta", fieldNode == null ? new ObjectMapper().createObjectNode().nullNode() : fieldNode);

        return Code.Success;
    }
}
