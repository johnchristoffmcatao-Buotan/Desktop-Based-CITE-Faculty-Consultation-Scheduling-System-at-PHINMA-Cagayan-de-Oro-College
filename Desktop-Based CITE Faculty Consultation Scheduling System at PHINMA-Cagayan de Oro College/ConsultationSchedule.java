import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConsultationSchedule {

    // Time slots an instructor can offer (shared by the instructor and student screens).
    public static final String[] TIME_SLOTS = {
        "7:00 AM - 8:00 AM",
        "8:00 AM - 9:00 AM",
        "9:00 AM - 10:00 AM",
        "10:00 AM - 11:00 AM",
        "11:00 AM - 12:00 PM",
        "1:00 PM - 2:00 PM",
        "2:00 PM - 3:00 PM",
        "3:00 PM - 4:00 PM",
        "4:00 PM - 5:00 PM",
        "5:00 PM - 6:00 PM"
    };

    // Minutes a student has to cancel a pending request (also used by the instructor screen's rules).
    public static final int CANCEL_WINDOW_MINUTES = 20;

    private static final Pattern CLOCK =
        Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*([AaPp][Mm])?");

    // Start time of a slot label such as "7:00 AM - 8:00 AM"; null if it can't be read.
    public static LocalTime slotStart(String slot) {
        if (slot == null) {
            return null;
        }
        String first = slot.trim().split("\\s*[-\u2013\u2014]\\s*|\\s+to\\s+")[0].trim();
        Matcher m = CLOCK.matcher(first);
        if (!m.matches()) {
            return null;
        }
        int h = Integer.parseInt(m.group(1));
        int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        String ap = m.group(3);
        if (ap != null) {
            boolean pm = ap.equalsIgnoreCase("pm");
            h = (h == 12) ? (pm ? 12 : 0) : (pm ? h + 12 : h);
        }
        if (h > 23 || min > 59) {
            return null;
        }
        return LocalTime.of(h, min);
    }

    // True when the slot has already started (past day, or earlier today).
    public static boolean isPastSlot(LocalDate date, String slot) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            return true;
        }
        if (date.isAfter(today)) {
            return false;
        }
        LocalTime start = slotStart(slot);
        return start != null && !start.isAfter(LocalTime.now());
    }

    public static int slotIndex(String time) {
        for (int i = 0; i < TIME_SLOTS.length; i++) {
            if (TIME_SLOTS[i].equals(time)) {
                return i;
            }
        }
        return TIME_SLOTS.length;
    }

    // Sorts times in day order (plain string order would put 10:00 AM before 7:00 AM).
    public static void sortTimes(List<String> times) {
        times.sort(Comparator.comparingInt(ConsultationSchedule::slotIndex)
            .thenComparing(Comparator.naturalOrder()));
    }

    // DATABASE: Get available schedules from tbl_consult_sched (today and later only).
    public static List<String[]> getSlots(String instructorEmail) {

        List<String[]> slots = new ArrayList<>();

        String sql =
            "SELECT cs.consult_date, cs.day, cs.available_time " +
            "FROM tbl_consult_sched cs " +
            "JOIN tbl_instructor i ON cs.inst_id = i.inst_id " +
            "WHERE i.inst_email = ? " +
            "AND cs.status = 'Available' " +
            "AND cs.consult_date >= CURDATE() " +
            "ORDER BY cs.consult_date, cs.available_time";

        try (
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setString(1, instructorEmail);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {

                slots.add(new String[] {
                    rs.getString("consult_date"),
                    rs.getString("day"),
                    rs.getString("available_time")
                });
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return slots;
    }

    // DATABASE: Replace an instructor's available schedules.
    public static void replaceSlots(
            String instructorEmail,
            List<String[]> newSlots) {

        String deleteSQL =
            "DELETE FROM tbl_consult_sched " +
            "WHERE inst_id = (" +
            "SELECT inst_id FROM tbl_instructor WHERE inst_email = ?" +
            ") " +
            "AND sched_id NOT IN (" +
            "SELECT sched_id FROM tbl_consult" +
            ")";

        String insertSQL =
            "INSERT INTO tbl_consult_sched " +
            "(inst_id, consult_date, day, available_time, status) " +
            "VALUES (" +
            "(SELECT inst_id FROM tbl_instructor WHERE inst_email = ?)," +
            "?, ?, ?, 'Available')";

        try (Connection conn = DatabaseConnection.getConnection()) {

            try (PreparedStatement delete =
                    conn.prepareStatement(deleteSQL)) {

                delete.setString(1, instructorEmail);
                delete.executeUpdate();
            }

            try (PreparedStatement insert =
                    conn.prepareStatement(insertSQL)) {

                for (String[] slot : newSlots) {

                    insert.setString(1, instructorEmail);
                    insert.setString(2, slot[0]);
                    insert.setString(3, slot[1]);
                    insert.setString(4, slot[2]);

                    insert.executeUpdate();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void removeListener(Runnable listener) {
    }
}