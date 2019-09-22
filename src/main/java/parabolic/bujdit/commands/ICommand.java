package parabolic.bujdit.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import parabolic.bujdit.Code;
import parabolic.bujdit.DBConnection;
import parabolic.bujdit.RequestPersist;

import java.sql.SQLException;

public interface ICommand {

    Code execute(RequestPersist pers, DBConnection dbcon, JsonNode cmd, ObjectNode response) throws SQLException;

}
