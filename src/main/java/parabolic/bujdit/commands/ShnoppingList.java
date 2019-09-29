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

public class ShnoppingList implements ICommand {

    @Override
    public Code execute(RequestPersist pers, Connection dbcon, JsonNode cmd, ObjectNode response) throws SQLException {
        if (!pers.loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        boolean includeMeta = BHF.extractBoolean(cmd.get("include_meta"), false);

        String sqlstr =
            "SELECT id, name, permission" + (includeMeta ? ", shnopping.meta AS meta, shnopping_user.meta AS user_meta" : "") +
            " FROM shnopping"+
            " INNER JOIN shnopping_user ON shnopping.id = shnopping_id"+
            " WHERE user_id = ? AND permission >= 1"+
            " ORDER BY id ASC";
        ResultSet rs = dbcon.query(sqlstr, pers.userId);

        ArrayNode shnoppings = response.putArray("shnoppings");
        while (rs.next()) {
            ObjectNode shnop = shnoppings.addObject();
            shnop.put("id", rs.getLong(1));
            shnop.put("name", rs.getString(2));
            shnop.put("permission", rs.getShort(3));
            if (includeMeta) {
                shnop.set("meta", BHF.String2JSON(rs.getString(4)));
                shnop.set("user_meta", BHF.String2JSON(rs.getString(5)));
            }
        }

        return Code.Success;
    }
}
