package parabolic.bujdit;

import io.javalin.Javalin;
import io.javalin.http.Context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import parabolic.bujdit.DB.Connection;
import parabolic.bujdit.DB.Maintainer;

import java.io.IOException;
import java.sql.SQLException;

public class Core {

    public static void main(String[] args) {

        initializeDB();

        Maintainer dbm = new Maintainer();
        dbm.start();

        Javalin buj = Javalin.create().start(20304);
        buj.post("/api", Core::handleJavalinCtx);

    }

    private static void handleJavalinCtx(Context ctx) {

        JsonNode rootNode;

        try {
            CommandProcessor proc = new CommandProcessor();
            rootNode = new ObjectMapper().readTree(ctx.body());
        }
        catch (IOException ex) {
            ctx.result("invalid json");
            return;
        }

        CommandProcessor proc = new CommandProcessor();
        ctx.json(proc.process(rootNode));
    }

    private static void initializeDB() {
        try (Connection pgcon = new Connection()) {

            // ================================
            // USERS
            // ================================

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

            // ================================
            // BUJDIT
            // ================================

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

            // ================================
            // SHNOPPING
            // ================================

            str =
                "CREATE TABLE IF NOT EXISTS shnopping ("+
                    "id BIGSERIAL PRIMARY KEY,"+
                    "name VARCHAR(128) NOT NULL,"+
                    "meta JSON"+
                ")";
            pgcon.update(str);

            str =
                "CREATE TABLE IF NOT EXISTS shnopping_user ("+
                    "user_id BIGINT REFERENCES users(id) ON DELETE CASCADE NOT NULL,"+
                    "shnopping_id BIGINT REFERENCES shnopping(id) ON DELETE CASCADE NOT NULL,"+
                    "permission SMALLINT NOT NULL DEFAULT 0,"+
                    "meta JSON,"+

                    "UNIQUE(user_id, shnopping_id)"+
                ")";
            pgcon.update(str);

            str =
                "CREATE TABLE IF NOT EXISTS shnopping_store ("+
                    "id BIGSERIAL PRIMARY KEY,"+
                    "shnopping_id BIGINT REFERENCES shnopping(id) ON DELETE CASCADE NOT NULL,"+
                    "name VARCHAR(128) NOT NULL,"+

                    "UNIQUE(shnopping_id, name)"+
                ")";
            pgcon.update(str);

            str =
                "CREATE TABLE IF NOT EXISTS shnopping_item ("+
                    "id BIGSERIAL PRIMARY KEY,"+
                    "shnopping_id BIGINT REFERENCES shnopping(id) ON DELETE CASCADE NOT NULL,"+
                    "name VARCHAR(128) NOT NULL,"+
                    "variety VARCHAR(64) NOT NULL DEFAULT '',"+
                    "description TEXT NOT NULL DEFAULT '',"+

                    "UNIQUE(shnopping_id, name, variety)"+
                ")";
            pgcon.update(str);

        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}