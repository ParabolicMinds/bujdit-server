package parabolic.bujdit.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import parabolic.bujdit.BHF;
import parabolic.bujdit.Code;
import parabolic.bujdit.DB.Connection;
import parabolic.bujdit.RequestPersist;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ShnoppingStoreDelete implements ICommand {

    @Override
    public Code execute(RequestPersist pers, Connection dbcon, JsonNode cmd, ObjectNode response) throws SQLException {
        if (!pers.loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        ResultSet rs;

        long storeId = BHF.extractLong(cmd.get("id"), -1);
        if (storeId < 0) return Code.MissingRequiredField;

        // check permission
        rs = dbcon.query("SELECT shnopping_id FROM shnopping_store WHERE id = ?", storeId);
        if (!rs.next()) return Code.NotFoundOrInsufficientAccess;
        long shnopId = rs.getLong(1);
        rs = dbcon.query("SELECT user_id FROM shnopping_user WHERE user_id = ? AND shnopping_id = ? AND permission >= 2", pers.userId, shnopId);
        if (!rs.next()) return Code.NotFoundOrInsufficientAccess;

        int numD = dbcon.update("DELETE FROM shnopping_store WHERE id = ?", storeId);

        return (numD > 0) ? Code.Success : Code.NotFoundOrInsufficientAccess;
    }
}
