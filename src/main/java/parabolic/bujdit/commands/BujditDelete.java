package parabolic.bujdit.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import parabolic.bujdit.BHF;
import parabolic.bujdit.Code;
import parabolic.bujdit.DB.Connection;
import parabolic.bujdit.RequestPersist;

import java.sql.ResultSet;
import java.sql.SQLException;

public class BujditDelete implements ICommand {

    @Override
    public Code execute(RequestPersist pers, Connection dbcon, JsonNode cmd, ObjectNode response) throws SQLException {
        if (!pers.loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        long id = BHF.extractLong(cmd.get("id"), -1);
        if (id == -1) return Code.MissingRequiredField;

        String sqlstr =
            "SELECT id FROM bujdit"+
            " INNER JOIN bujdit_user ON bujdit.id = bujdit_id"+
            " WHERE bujdit_id = ? AND permission >= 4";

        ResultSet rs = dbcon.query(sqlstr, id);
        if (!rs.next()) return Code.NotFoundOrInsufficientAccess;

        dbcon.update("DELETE FROM bujdit WHERE id = ?", id);

        return Code.Success;
    }
}
