package parabolic.bujdit.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import parabolic.bujdit.BHF;
import parabolic.bujdit.Code;
import parabolic.bujdit.DB.Connection;
import parabolic.bujdit.RequestPersist;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ShnoppingDelete implements ICommand {

    @Override
    public Code execute(RequestPersist pers, Connection dbcon, JsonNode cmd, ObjectNode response) throws SQLException {
        if (!pers.loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        long id = BHF.extractLong(cmd.get("id"), -1);
        if (id == -1) return Code.MissingRequiredField;

        String sqlstr =
            "SELECT id FROM shnopping"+
            " INNER JOIN shnopping_user ON shnopping.id = shnopping_id"+
            " WHERE shnopping_id = ? AND permission >= 4";

        ResultSet rs = dbcon.query(sqlstr, id);
        if (!rs.next()) return Code.NotFoundOrInsufficientAccess;

        dbcon.update("DELETE FROM shnopping WHERE id = ?", id);

        return Code.Success;
    }
}
