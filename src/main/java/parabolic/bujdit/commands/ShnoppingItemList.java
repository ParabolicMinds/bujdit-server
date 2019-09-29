package parabolic.bujdit.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import parabolic.bujdit.BHF;
import parabolic.bujdit.Code;
import parabolic.bujdit.DB.Connection;
import parabolic.bujdit.RequestPersist;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ShnoppingItemList implements ICommand {

    @Override
    public Code execute(RequestPersist pers, Connection dbcon, JsonNode cmd, ObjectNode response) throws SQLException {
        if (!pers.loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        ResultSet rs;

        long shnopId = BHF.extractLong(cmd.get("id"), -1);
        if (shnopId < 0) return Code.MissingRequiredField;

        // check permission
        rs = dbcon.query("SELECT user_id FROM shnopping_user WHERE user_id = ? AND shnopping_id = ? AND permission >= 1", pers.userId, shnopId);
        if (!rs.next()) return Code.NotFoundOrInsufficientAccess;

        rs = dbcon.query("SELECT id, name, variety, description FROM shnopping_item WHERE shnopping_id = ?", shnopId);
        ArrayNode items = response.putArray("stores");

        while (rs.next()) {
            ObjectNode item = items.addObject();
            item.put("id", rs.getLong(1));
            item.put("name", rs.getString(2));
            item.put("variety", rs.getString(3));
            item.put("desc", rs.getString(4));
        }

        return Code.Success;
    }
}
