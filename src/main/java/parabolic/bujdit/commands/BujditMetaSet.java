package parabolic.bujdit.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import parabolic.bujdit.BHF;
import parabolic.bujdit.Code;
import parabolic.bujdit.DBConnection;
import parabolic.bujdit.RequestPersist;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BujditMetaSet implements ICommand {

    @Override
    public Code execute(RequestPersist pers, DBConnection dbcon, JsonNode cmd, ObjectNode response) throws SQLException {
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
                "SELECT bujdit.meta AS meta"+
                        " FROM bujdit"+
                        " INNER JOIN bujdit_user ON bujdit.id = bujdit_id"+
                        " WHERE user_id = ? AND bujdit_id = ? AND permission >= 2";

        ResultSet rs = dbcon.query(sqlstr, pers.userId, id);
        if (!rs.next()) return Code.NotFoundOrInsufficientPermissions;

        JsonNode metaSet;

        if (field.isEmpty()) {
            metaSet = meta;
        } else {
            ObjectNode metaGet = BHF.String2JSON(rs.getString(1));
            metaGet.set(field, meta);
            metaSet = metaGet;
        }

        sqlstr =
                "UPDATE bujdit"+
                        " SET meta = ?::JSON"+
                        " WHERE id = ?";
        dbcon.update(sqlstr, metaSet, id);

        return Code.Success;
    }
}