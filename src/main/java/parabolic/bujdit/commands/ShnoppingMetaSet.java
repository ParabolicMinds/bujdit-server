package parabolic.bujdit.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import parabolic.bujdit.BHF;
import parabolic.bujdit.Code;
import parabolic.bujdit.DB.Connection;
import parabolic.bujdit.RequestPersist;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ShnoppingMetaSet implements ICommand {

    @Override
    public Code execute(RequestPersist pers, Connection dbcon, JsonNode cmd, ObjectNode response) throws SQLException {
        if (!pers.loggedIn) {
            return Code.CommandRequiresAuthentication;
        }

        long id = BHF.extractLong(cmd.get("id"), -1);
        String metaStr = BHF.extractString(cmd.get("meta"));
        if (id == -1 || metaStr.isEmpty()) return Code.MissingRequiredField;

        JsonNode meta;
        try {
            meta = new ObjectMapper().readTree(metaStr);
        } catch (IOException e) {
            return Code.InvalidFieldFormat;
        }

        String field = BHF.extractString(cmd.get("field"));

        String sqlstr =
            "SELECT shnopping.meta AS meta"+
            " FROM shnopping"+
            " INNER JOIN shnopping_user ON shnopping.id = shnopping_id"+
            " WHERE user_id = ? AND shnopping_id = ? AND permission >= 2";

        ResultSet rs = dbcon.query(sqlstr, pers.userId, id);
        if (!rs.next()) return Code.NotFoundOrInsufficientAccess;

        JsonNode metaSet;

        if (field.isEmpty()) {
            metaSet = meta;
        } else {
            ObjectNode metaGet = BHF.String2JSON(rs.getString(1));
            metaGet.set(field, meta);
            metaSet = metaGet;
        }

        dbcon.update("UPDATE shnopping SET meta = ?::JSON WHERE id = ?", metaSet, id);

        return Code.Success;
    }
}
