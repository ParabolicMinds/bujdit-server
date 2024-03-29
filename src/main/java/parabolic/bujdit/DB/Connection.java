package parabolic.bujdit.DB;

import com.fasterxml.jackson.databind.JsonNode;

import java.sql.*;
import java.util.Properties;

public class Connection implements AutoCloseable {

    private java.sql.Connection dbcon;

    public Connection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", "postgres");
        dbcon = DriverManager.getConnection("jdbc:postgresql://localhost/bujdit", props);

    }
    @Override
    public void close() {
        try {
            dbcon.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
            System.exit(-1);
        }
    }

    public void beginTransaction() {
        try {
            dbcon.setAutoCommit(false);
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public void endTransaction(boolean commit) {
        try {
            if (commit) dbcon.commit();
            else dbcon.rollback();
            dbcon.setAutoCommit(true);
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public void endTransaction() {
        endTransaction(true);
    }

    private static void setValue(PreparedStatement ps, int idx, Object ob) throws SQLException {
        if (ob.getClass() == Nulls.class) {
            switch ((Nulls) ob) {
            case Integer:
                ps.setNull(idx, Types.BIGINT);
            case String:
                ps.setNull(idx, Types.VARCHAR);
            }
        } else if (ob.getClass() == Long.class) {
            ps.setLong(idx, (Long) ob);
        } else if (ob.getClass() == Integer.class) {
            ps.setInt(idx, (Integer) ob);
        } else if (ob.getClass() == String.class) {
            ps.setString(idx, (String) ob);
        } else if (ob instanceof JsonNode) {
            ps.setString(idx, ((JsonNode)ob).toString());
        } else {
            throw new IllegalArgumentException();
        }
    }

    public int update(String queryStr, Object ... vars) throws SQLException {
        PreparedStatement ps = dbcon.prepareStatement(queryStr);
        for (int i = 0; i < vars.length; i++) {
            setValue(ps, i + 1, vars[i]);
        }
        return ps.executeUpdate();
    }

    public ResultSet query(String queryStr, Object ... vars) throws SQLException {
        PreparedStatement ps = dbcon.prepareStatement(queryStr);
        for (int i = 0; i < vars.length; i++) {
            setValue(ps, i + 1, vars[i]);
        }
        return ps.executeQuery();
    }

}
