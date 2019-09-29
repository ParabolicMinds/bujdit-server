package parabolic.bujdit.DB;

import java.sql.SQLException;

public class Maintainer extends Thread {

    public Maintainer() { }

    @Override
    public void run() {
        while (true) {

            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) { }

            try (Connection dbcon = new Connection()) {
                dbcon.update("DELETE FROM session WHERE activity < NOW() - ?::INTERVAL", "30 MINUTES");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }
}
