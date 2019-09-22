package parabolic.bujdit.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import parabolic.bujdit.BHF;
import parabolic.bujdit.Code;
import parabolic.bujdit.DBConnection;
import parabolic.bujdit.RequestPersist;

import java.sql.ResultSet;
import java.sql.SQLException;

public class BujditList implements ICommand {

    @Override
    public Code execute(RequestPersist pers, DBConnection dbcon, JsonNode cmd, ObjectNode response) throws SQLException {
        if (!pers.loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        boolean includeMeta = BHF.extractBoolean(cmd.get("include_meta"), false);

        String sqlstr =
            "SELECT id, name, permission" + (includeMeta ? ", bujdit.meta AS meta, bujdit_user.meta AS user_meta" : "") +
            " FROM bujdit"+
            " INNER JOIN bujdit_user ON bujdit.id = bujdit_id"+
            " WHERE user_id = ? AND permission >= 1"+
            " ORDER BY id ASC";
        ResultSet rs = dbcon.query(sqlstr, pers.userId);

        ArrayNode bujdits = response.putArray("bujdits");
        while (rs.next()) {
            ObjectNode buj = bujdits.addObject();
            buj.put("id", rs.getLong(1));
            buj.put("name", rs.getString(2));
            buj.put("permission", rs.getString(3));
            if (includeMeta) {
                buj.set("meta", BHF.String2JSON(rs.getString(4)));
                buj.set("user_meta", BHF.String2JSON(rs.getString(5)));
            }
        }

        return Code.Success;
    }
}
