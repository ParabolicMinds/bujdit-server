package parabolic.bujdit.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import parabolic.bujdit.BHF;
import parabolic.bujdit.Code;
import parabolic.bujdit.DB.Connection;
import parabolic.bujdit.RequestPersist;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ShnoppingStoreCreate implements ICommand {

    @Override
    public Code execute(RequestPersist pers, Connection dbcon, JsonNode cmd, ObjectNode response) throws SQLException {
        if (!pers.loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        ResultSet rs;

        String name = BHF.extractString(cmd.get("name"));
        long id = BHF.extractLong(cmd.get("id"), -1);
        if (name.isEmpty() || id < 0) return Code.MissingRequiredField;

        // check permission
        rs = dbcon.query("SELECT user_id FROM shnopping_user WHERE user_id = ? AND shnopping_id = ? AND permission >= 2", pers.userId, id);
        if (!rs.next()) return Code.NotFoundOrInsufficientAccess;

        try {
            rs = dbcon.query("INSERT INTO shnopping_store (shnopping_id, name) VALUES (?, ?) RETURNING id", id, name);
            rs.next();
            response.put("id", rs.getLong(1));
            return Code.Success;
        } catch (SQLException ex) {
            return Code.DuplicateEntryRejected;
        }
    }
}
