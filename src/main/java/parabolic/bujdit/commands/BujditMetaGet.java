package parabolic.bujdit.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import parabolic.bujdit.BHF;
import parabolic.bujdit.Code;
import parabolic.bujdit.DBConnection;
import parabolic.bujdit.RequestPersist;

import java.sql.ResultSet;
import java.sql.SQLException;

public class BujditMetaGet implements ICommand {

    @Override
    public Code execute(RequestPersist pers, DBConnection dbcon, JsonNode cmd, ObjectNode response) throws SQLException {
        if (!pers.loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        long id = BHF.extractLong(cmd.get("id"), -1);
        if (id == -1) return Code.MissingRequiredField;

        String field = BHF.extractString(cmd.get("field"));

        String sqlstr =
                "SELECT bujdit.meta AS meta"+
                        " FROM bujdit"+
                        " INNER JOIN bujdit_user ON bujdit.id = bujdit_id"+
                        " WHERE user_id = ? AND bujdit_id = ? AND permission >= 1";

        ResultSet rs = dbcon.query(sqlstr, pers.userId, id);
        if (!rs.next()) return Code.NotFoundOrInsufficientPermissions;

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
