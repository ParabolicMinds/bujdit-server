package parabolic.bujdit;

import java.sql.SQLException;

public class DBMaintainer extends Thread {

    private DBConnection dbcon;

    DBMaintainer() {
        try {
            dbcon = new DBConnection();
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(15000);
            } catch (InterruptedException ignored) { }

            try {
                dbcon.update("DELETE FROM session WHERE activity < NOW() - ?::INTERVAL", "30 MINUTES");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }
}
