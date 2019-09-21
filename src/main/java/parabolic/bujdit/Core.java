package parabolic.bujdit;

import io.javalin.Javalin;
import io.javalin.http.Context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.sql.SQLException;

public class Core {

    public static void main(String[] args) {

        initializeDB();

        DBMaintainer dbm = new DBMaintainer();
        dbm.start();

        Javalin buj = Javalin.create().start(20304);
        buj.post("/api", Core::handleJavalinCtx);

    }

    private static void handleJavalinCtx(Context ctx) {

        JsonNode rootNode;
        ObjectMapper mapper = new ObjectMapper();

        try {
            rootNode = mapper.readTree(ctx.body());
        }
        catch (IOException ex) {
            ctx.result("invalid json");
            return;
        }

        try {
            CommandProcessor proc = new CommandProcessor();
            ctx.json(proc.process(rootNode));
        }
        catch (SQLException ex) {
            ctx.json(String.format("{\"success\":false,\"code\":%s}", Code.ServerException));
        }
    }

    private static void initializeDB() {
        try {
            DBConnection pgcon = new DBConnection();

            String str =
                "CREATE TABLE IF NOT EXISTS users ("+
                    "id BIGSERIAL PRIMARY KEY,"+
                    "name VARCHAR(32) UNIQUE NOT NULL,"+
                    "salt INTEGER NOT NULL,"+
                    "passhash CHAR(128) NOT NULL,"+
                    "metadata JSON"+
                 ")";
            pgcon.update(str);

            str =
                "CREATE TABLE IF NOT EXISTS session ("+
                    "user_id BIGINT REFERENCES users(id) ON DELETE CASCADE NOT NULL,"+
                    "token CHAR(64) UNIQUE NOT NULL,"+
                    "activity timestamp NOT NULL DEFAULT NOW()"+
                ")";
            pgcon.update(str);

            str =
                "CREATE TABLE IF NOT EXISTS bujdit ("+
                    "id BIGSERIAL PRIMARY KEY,"+
                    "name VARCHAR(128) NOT NULL,"+
                    "meta JSON"+
                ")";
            pgcon.update(str);

            str =
                "CREATE TABLE IF NOT EXISTS bujdit_user ("+
                    "user_id BIGINT REFERENCES users(id) ON DELETE CASCADE NOT NULL,"+
                    "bujdit_id BIGINT REFERENCES bujdit(id) ON DELETE CASCADE NOT NULL,"+
                    "UNIQUE(user_id, bujdit_id),"+
                    "permission SMALLINT NOT NULL DEFAULT 0,"+
                    "meta JSON"+
                ")";
            pgcon.update(str);

        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}