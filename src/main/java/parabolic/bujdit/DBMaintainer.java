package parabolic.bujdit;

import java.sql.SQLException;

public class DBMaintainer extends Thread {

    DBMaintainer() { }

    @Override
    public void run() {
        while (true) {

            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) { }

            try (DBConnection dbcon = new DBConnection()) {
                dbcon.update("DELETE FROM session WHERE activity < NOW() - ?::INTERVAL", "30 MINUTES");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }
}
