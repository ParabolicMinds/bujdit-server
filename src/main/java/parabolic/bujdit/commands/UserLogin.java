package parabolic.bujdit.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import parabolic.bujdit.Code;
import parabolic.bujdit.DBConnection;
import parabolic.bujdit.BHF;
import parabolic.bujdit.RequestPersist;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserLogin implements ICommand {

    @Override
    public Code execute(RequestPersist pers, DBConnection dbcon, JsonNode cmd, ObjectNode response) throws SQLException {
        String username = BHF.extractString(cmd.get("username"));
        String password = BHF.extractString(cmd.get("password"));

        if (username.isEmpty() || password.isEmpty()) {
            return Code.MissingRequiredField;
        }

        ResultSet result = dbcon.query("SELECT id, salt, passhash FROM users WHERE LOWER(name) = LOWER(?)", username);
        if (!result.next()) { // username not found
            return Code.UsernameOrPasswordInvalid;
        }

        long id = result.getLong(1);
        String salt = result.getString(2);
        String passhash = result.getString(3);

        try {
            MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
            if (!passhash.equals(BHF.bytesToHex(sha512.digest((password + salt).getBytes())))) {
                return Code.UsernameOrPasswordInvalid;
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        byte[] token_bytes = new byte[32];
        new SecureRandom().nextBytes(token_bytes);
        String token = BHF.bytesToHex(token_bytes);

        dbcon.update("INSERT INTO session (user_id, token) VALUES (?, ?)", id, token);
        response.put("token", token);

        return Code.Success;
    }
}
