import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;


public class GradeConsultStudent extends JFrame {

    private static final String PAGE_DASHBOARD = "dashboard";
    private static final String PAGE_BOOK = "book";
    private static final String PAGE_PENDING = "pending";
    private static final String PAGE_HISTORY = "history";

    private final String studentName;
    private final String studentEmail;
    
    private String studentSection = "";
    private int selectedSubjectId = -1;
    private int selectedInstructorId = -1;
    private String selectedInstructorEmail = "";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel pages = new JPanel(cardLayout);
    private Ui.Sidebar sidebar;
    private Ui.NavItem navDashboard;
    private Ui.NavItem navBook;
    private Ui.NavItem navPending;
    private Ui.NavItem navHistory;

    private final JLabel lblHello = Ui.label("", Font.PLAIN, 32, Ui.TEXT);
    private final JLabel lblSub = Ui.label("", Font.PLAIN, 17, Ui.MUTED);
    private final JLabel lblEnrolled = new JLabel("0");
    private final JLabel lblPending = new JLabel("0");
    private final JLabel lblResolved = new JLabel("0");

    private final DefaultTableModel enrolledModel =
        Ui.model("Subject code", "Subject name", "Instructor", "Section");
    // History = everything that is no longer pending (accepted / rejected / cancelled / completed)
    private final DefaultTableModel historyModel =
        Ui.model("ID", "Date", "Day", "Time", "Status", "Cancellation Reason");
    // Pending requests only
    private final DefaultTableModel pendingModel =
        Ui.model("ID", "Date", "Day", "Time", "Status");

    private JTable historyTable;
    private JTable pendingTable;
    private Ui.PillCombo<String> subjectCombo;
    private Ui.PillTextField instructorField;
    private CalendarPanel calendar;
    private JLabel hintLabel;

    // date -> available time slots of the selected subject's instructor
    private final Map<LocalDate, List<String>> slotsByDate = new TreeMap<>();

    // consult id -> moment (millis) when the 20-minute cancel window closes (pending requests only)
    private final Map<Integer, Long> cancelDeadline = new HashMap<>();

    // consult id -> {subject, instructor} shown in the details box
    private final Map<Integer, String[]> consultInfo = new HashMap<>();

    public GradeConsultStudent(String name, String email) {
        Ui.install();
        this.studentName = name;
        this.studentEmail = email;

        setTitle("PHINMA COC - Student");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        if (Ui.logoImage() != null) {
            setIconImage(Ui.logoImage());
        }

        buildUi();
        loadProfile();
        loadSubjects();
        subjectCombo.addActionListener(e -> updateInstructor());
        loadHistory();
        updateStats();
        loadSlots();

        setSize(1280, 760);
        setMinimumSize(new Dimension(1020, 660));
        setLocationRelativeTo(null);
        showPage(PAGE_DASHBOARD);
    }
    //------------------------------------------------------------------

    // Nav Bar
    void showPage(String page) {
        cardLayout.show(pages, page);
        switch (page) {
            case PAGE_BOOK:
                sidebar.select(navBook);
                loadSlots();
                break;
            case PAGE_PENDING:
                sidebar.select(navPending);
                loadHistory();
                break;
            case PAGE_HISTORY:
                sidebar.select(navHistory);
                loadHistory();
                break;
            default:
                sidebar.select(navDashboard);
                updateStats();
                break;
        }
    }

    private void logout() {
        int c = JOptionPane.showConfirmDialog(this, "Do you want to log out?",
            "Log out", JOptionPane.YES_NO_OPTION);
        if (c == JOptionPane.YES_OPTION) {
            dispose();
            new LoginMenu().setVisible(true);
        }
    }
    //------------------------------------------------------------------

    // UI
    private void buildUi() {
        sidebar = new Ui.Sidebar();
        navDashboard = sidebar.addNav(Ui.Glyph.DASHBOARD, "DASHBOARD", () -> showPage(PAGE_DASHBOARD));
        navBook = sidebar.addNav(Ui.Glyph.CALENDAR, "BOOK CONSULTATION", () -> showPage(PAGE_BOOK));
        navPending = sidebar.addNav(Ui.Glyph.PENDING, "PENDING\nCONSULTATION", () -> showPage(PAGE_PENDING));
        navHistory = sidebar.addNav(Ui.Glyph.HISTORY, "HISTORY", () -> showPage(PAGE_HISTORY));
        sidebar.addLogout("Log out", this::logout);

        pages.setOpaque(false);
        pages.add(buildDashboardPage(), PAGE_DASHBOARD);
        pages.add(buildBookPage(), PAGE_BOOK);
        pages.add(buildPendingPage(), PAGE_PENDING);
        pages.add(buildHistoryPage(), PAGE_HISTORY);

        Ui.Shell shell = new Ui.Shell();
        shell.add(pages, BorderLayout.CENTER);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(sidebar, BorderLayout.WEST);
        getContentPane().add(shell, BorderLayout.CENTER);
    }
    //------------------------------------------------------------------

    //Dashboard Pages
    private JPanel buildDashboardPage() {
        JPanel page = Ui.pagePanel();

        JPanel helloRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        helloRow.setOpaque(false);
        helloRow.add(lblHello);
        JLabel userIcon = new JLabel(Ui.icon(Ui.Glyph.USER, 44, Ui.TEXT));
        userIcon.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
        helloRow.add(userIcon);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(Ui.left(helloRow));
        top.add(Ui.left(lblSub));
        lblSub.setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 0));
        top.add(Box.createVerticalStrut(26));
        top.add(Ui.left(Ui.statRow(
            Ui.statCard(lblEnrolled, "Enrolled Subject"),
            Ui.statCard(lblPending, "Pend"),
            Ui.statCard(lblResolved, "Resolved"))));
        top.add(Box.createVerticalStrut(40));
        top.add(Ui.left(Ui.titleRow("Enrolled Subjects", Ui.Glyph.SUBJECTS)));
        top.add(Box.createVerticalStrut(12));
        page.add(top, BorderLayout.NORTH);

        JTable table = new JTable();
        page.add(Ui.table(table, enrolledModel, false, 150, 280, 330, 100), BorderLayout.CENTER);
        return page;
    }

    private JPanel buildBookPage() {
        JPanel page = Ui.pagePanel();

        subjectCombo = new Ui.PillCombo<>();
        subjectCombo.setPreferredSize(new Dimension(520, 48));
        subjectCombo.setMaximumSize(new Dimension(520, 48));
        instructorField = new Ui.PillTextField();
        instructorField.setEditable(false);
        instructorField.setPreferredSize(new Dimension(520, 48));
        instructorField.setMaximumSize(new Dimension(520, 48));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(Ui.left(Ui.titleRow("BOOK CONSULTATION", Ui.Glyph.CALENDAR)));
        top.add(Box.createVerticalStrut(22));
        top.add(Ui.left(Ui.label("Select Subject:", Font.PLAIN, 17, Ui.TEXT)));
        top.add(Box.createVerticalStrut(6));
        top.add(Ui.left(subjectCombo));
        top.add(Box.createVerticalStrut(16));
        top.add(Ui.left(Ui.label("Instructor:", Font.PLAIN, 17, Ui.TEXT)));
        top.add(Box.createVerticalStrut(6));
        top.add(Ui.left(instructorField));
        top.add(Box.createVerticalStrut(22));
        page.add(top, BorderLayout.NORTH);

        calendar = new CalendarPanel();
        calendar.setClickable(d -> selectedInstructorEmail.isEmpty() || slotsByDate.containsKey(d));
        calendar.setClickHandler(this::onDayClicked);
        page.add(calendar, BorderLayout.CENTER);

        hintLabel = Ui.label(" ", Font.PLAIN, 14, Ui.MUTED);
        hintLabel.setBorder(BorderFactory.createEmptyBorder(10, 2, 0, 0));
        page.add(hintLabel, BorderLayout.SOUTH);
        return page;
    }

    private JPanel buildPendingPage() {
        JPanel page = Ui.pagePanel();
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        top.add(Ui.titleRow("PENDING CONSULTATION", Ui.Glyph.PENDING), BorderLayout.WEST);
        page.add(top, BorderLayout.NORTH);

        pendingTable = new JTable();
        page.add(Ui.table(pendingTable, pendingModel, true, 0, 200, 220, 330, 160), BorderLayout.CENTER);
        attachDetailsClick(pendingTable, pendingModel);
        return page;
    }

    // Click a row to see its details (and cancel it if it is still pending and inside the window).
    private void attachDetailsClick(JTable table, DefaultTableModel model) {
        table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    showDetails(table, model, row);
                }
            }
        });
    }

    private JPanel buildHistoryPage() {
        JPanel page = Ui.pagePanel();
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        top.add(Ui.titleRow("HISTORY", Ui.Glyph.HISTORY), BorderLayout.WEST);
        page.add(top, BorderLayout.NORTH);

        historyTable = new JTable();
        page.add(Ui.table(historyTable, historyModel, true, 0, 200, 220, 330, 160, 330), BorderLayout.CENTER);

        attachDetailsClick(historyTable, historyModel);
        return page;
    }
    //------------------------------------------------------------------

    // Data: Gets the data from the database
    private void loadProfile() {
        lblHello.setText("Hello! " + studentName);

        // DATABASE: the student's section (tbl_student.section is also used by the instructor screen)
        String sql = "SELECT section FROM tbl_student WHERE stud_email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, studentEmail);
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getString("section") != null) {
                studentSection = rs.getString("section");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        lblSub.setText(studentSection.isEmpty() ? "Student" : "Student - " + studentSection);
    }

    private void loadSubjects() {
        enrolledModel.setRowCount(0);
        subjectCombo.removeAllItems();
        subjectCombo.addItem("Choose a subject...");

        String sql =
            "SELECT s.sub_id, s.sub_code, s.sub_name, i.inst_name " +
            "FROM tbl_enrollment e " +
            "JOIN tbl_subject s ON e.sub_id = s.sub_id " +
            "JOIN tbl_instructor i ON s.inst_id = i.inst_id " +
            "JOIN tbl_student st ON e.stud_id = st.stud_id " +
            "WHERE st.stud_email = ? " +
            "ORDER BY s.sub_code";

        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, studentEmail);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                int subjectId = rs.getInt("sub_id");
                String code = rs.getString("sub_code");
                String subjectName = rs.getString("sub_name");
                String instructorName = rs.getString("inst_name");

                enrolledModel.addRow(new Object[] {code, subjectName, instructorName, studentSection});
                subjectCombo.addItem(subjectId + " - " + code + " - " + subjectName);
            }

        } catch (Exception e) {
            showDbError("Unable to load enrolled subjects", e);
        }
    }

    private void loadHistory() {

        historyModel.setRowCount(0);
        pendingModel.setRowCount(0);
        cancelDeadline.clear();
        consultInfo.clear();

        String sql =
            "SELECT c.consult_id, cs.consult_date, cs.day, cs.available_time, " +
            "c.status, c.cancellation_reason, " +
            "sub.sub_code, sub.sub_name, i.inst_name, " +
            "CASE WHEN c.status = 'Pending' AND c.requested_at IS NOT NULL THEN " +
            "GREATEST(0, TIMESTAMPDIFF(SECOND, NOW(), " +
            "DATE_ADD(c.requested_at, INTERVAL " + ConsultationSchedule.CANCEL_WINDOW_MINUTES + " MINUTE))) " +
            "ELSE NULL END AS seconds_left " +
            "FROM tbl_consult c " +
            "JOIN tbl_consult_sched cs ON c.sched_id = cs.sched_id " +
            "LEFT JOIN tbl_subject sub ON c.sub_id = sub.sub_id " +
            "LEFT JOIN tbl_instructor i ON c.inst_id = i.inst_id " +
            "WHERE c.stud_id = " +
            "(SELECT stud_id FROM tbl_student WHERE stud_email = ?) " +
            "ORDER BY c.consult_id DESC";

        try (
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, studentEmail);

            ResultSet rs = stmt.executeQuery();
            long now = System.currentTimeMillis();

            while (rs.next()) {
                String reason = rs.getString("cancellation_reason");
                int id = rs.getInt("consult_id");

                long secondsLeft = rs.getLong("seconds_left");
                if (!rs.wasNull() && secondsLeft > 0) {
                    cancelDeadline.put(id, now + secondsLeft * 1000L);
                }

                String status = rs.getString("status");
                String subCode = rs.getString("sub_code");
                String subName = rs.getString("sub_name");
                String instName = rs.getString("inst_name");
                consultInfo.put(id, new String[] {
                    subCode == null ? "N/A" : subCode + " - " + subName,
                    instName == null ? "N/A" : instName
                });
                if ("Pending".equals(status)) {
                    pendingModel.addRow(new Object[] {
                        id,
                        rs.getDate("consult_date"),
                        rs.getString("day"),
                        rs.getString("available_time"),
                        status
                    });
                } else {
                    historyModel.addRow(new Object[] {
                        id,
                        rs.getDate("consult_date"),
                        rs.getString("day"),
                        rs.getString("available_time"),
                        status,
                        reason == null ? "" : reason
                    });
                }
            }

        } catch (Exception e) {
            showDbError("Unable to load consultation history", e);
        }
    }

    private void updateStats() {
        // "Enrolled Subject" now counts this student's enrollments
        // (it used to count every subject in tbl_subject).
        String sql =
            "SELECT " +
            "(SELECT COUNT(*) FROM tbl_enrollment e " +
            " JOIN tbl_student s ON e.stud_id = s.stud_id " +
            " WHERE s.stud_email = ?) AS subjects, " +
            "(SELECT COUNT(*) FROM tbl_consult c " +
            " JOIN tbl_student s ON c.stud_id = s.stud_id " +
            " WHERE s.stud_email = ? AND c.status = 'Pending') AS pending, " +
            "(SELECT COUNT(*) FROM tbl_consult c " +
            " JOIN tbl_student s ON c.stud_id = s.stud_id " +
            " WHERE s.stud_email = ? AND c.status = 'Completed') AS completed";

        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, studentEmail);
            stmt.setString(2, studentEmail);
            stmt.setString(3, studentEmail);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                lblEnrolled.setText(String.valueOf(rs.getInt("subjects")));
                lblPending.setText(String.valueOf(rs.getInt("pending")));
                lblResolved.setText(String.valueOf(rs.getInt("completed")));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Picks the instructor of the chosen subject.
    private void updateInstructor() {
        String selected = (String) subjectCombo.getSelectedItem();

        if (selected == null || selected.startsWith("Choose")) {
            instructorField.setText("");
            selectedInstructorEmail = "";
            selectedSubjectId = -1;
            selectedInstructorId = -1;
            loadSlots();
            return;
        }

        try {
            // Format: subjectID - subjectCode - subjectName
            String[] parts = selected.split(" - ", 3);
            selectedSubjectId = Integer.parseInt(parts[0]);

            String sql =
                "SELECT i.inst_id, i.inst_name, i.inst_email " +
                "FROM tbl_subject s " +
                "JOIN tbl_instructor i ON s.inst_id = i.inst_id " +
                "WHERE s.sub_id = ?";

            try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, selectedSubjectId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    selectedInstructorId = rs.getInt("inst_id");
                    instructorField.setText(rs.getString("inst_name"));
                    selectedInstructorEmail = rs.getString("inst_email");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            instructorField.setText("");
            selectedInstructorEmail = "";
        }

        loadSlots();
    }

    // Reads the instructor's open slots and paints them on the calendar.
    private void loadSlots() {
        slotsByDate.clear();

        if (!selectedInstructorEmail.isEmpty()) {
            for (String[] slot : ConsultationSchedule.getSlots(selectedInstructorEmail)) {
                LocalDate date = CalendarPanel.parseDate(slot[0]);
                if (date == null || CalendarPanel.isPast(date)) {
                    continue;
                }
                if (ConsultationSchedule.isPastSlot(date, slot[2])) {
                    continue;   // today's time has already passed
                }
                slotsByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(slot[2]);
            }
            for (List<String> times : slotsByDate.values()) {
                ConsultationSchedule.sortTimes(times);
            }
        }

        Map<LocalDate, String> notes = new HashMap<>();
        Map<LocalDate, Color> tints = new HashMap<>();
        for (Map.Entry<LocalDate, List<String>> e : slotsByDate.entrySet()) {
            int n = e.getValue().size();
            notes.put(e.getKey(), n + (n == 1 ? " slot" : " slots"));
            tints.put(e.getKey(), Ui.GREEN_LIGHT);
        }
        calendar.setNotes(notes);
        calendar.setTints(tints);

        if (selectedInstructorEmail.isEmpty()) {
            hintLabel.setText("Select a subject to see the days your instructor is available.");
        } else if (slotsByDate.isEmpty()) {
            hintLabel.setText("This instructor has no open consultation times right now.");
        } else {
            hintLabel.setText("Green days have open slots/Grey days can't be booked.");
        }
    }
    
    // Booking
    private void onDayClicked(LocalDate date) {
        if (selectedInstructorEmail.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a subject first!");
            return;
        }
        List<String> times = slotsByDate.get(date);
        if (times == null || times.isEmpty()) {
            return;
        }
        bookConsultation(date, times);
    }

    private void bookConsultation(LocalDate date, List<String> times) {
        if (CalendarPanel.isPast(date)) {
            JOptionPane.showMessageDialog(this, "That day has already passed.",
                "Invalid Date", JOptionPane.WARNING_MESSAGE);
            loadSlots();
            return;
        }

        String subject = (String) subjectCombo.getSelectedItem();
        String day = date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);

        JComboBox<String> timeBox = new JComboBox<>(times.toArray(new String[0]));
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 8));
        panel.add(new JLabel("<html><b>Book Consultation</b></html>"));
        panel.add(new JLabel("Date: " + date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))
            + " (" + day + ")"));
        panel.add(new JLabel("Subject: " + subject));
        panel.add(new JLabel("Instructor: " + instructorField.getText()));
        panel.add(new JLabel("Choose a time:"));
        panel.add(timeBox);

        int confirm = JOptionPane.showConfirmDialog(this, panel, "Confirm Booking",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        String time = String.valueOf(timeBox.getSelectedItem());

        // The dialog may have been open for a while: re-check that the time hasn't passed.
        if (ConsultationSchedule.isPastSlot(date, time)) {
            JOptionPane.showMessageDialog(this, "That time has already passed. Please pick another slot.",
                "Time Passed", JOptionPane.WARNING_MESSAGE);
            loadSlots();
            return;
        }

        String sql =
            "INSERT INTO tbl_consult " +
            "(stud_id, inst_id, sub_id, sched_id, status, created_at, requested_at) " +
            "VALUES (" +
            "(SELECT stud_id FROM tbl_student WHERE stud_email = ?), " +
            "?, ?, " +
            "(SELECT sched_id FROM tbl_consult_sched " +
            " WHERE inst_id = ? " +
            " AND consult_date = ? " +
            " AND available_time = ? " +
            " AND status = 'Available' LIMIT 1), " +
            "'Pending', CURDATE(), NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, studentEmail);
            stmt.setInt(2, selectedInstructorId);
            stmt.setInt(3, selectedSubjectId);
            stmt.setInt(4, selectedInstructorId);
            stmt.setString(5, date.toString());
            stmt.setString(6, time);

            if (stmt.executeUpdate() > 0) {
                JOptionPane.showMessageDialog(this,
                    "Consultation requested successfully!\nYou can cancel it from History within "
                    + ConsultationSchedule.CANCEL_WINDOW_MINUTES + " minutes.",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
                loadSlots();
                loadHistory();
                updateStats();
            }

        } catch (Exception e) {
            showDbError("Unable to book consultation", e);
        }
    }

    // Shows one history entry. A pending request can be cancelled here for 20 minutes after booking.
    private void showDetails(JTable table, DefaultTableModel model, int row) {
        int m = table.convertRowIndexToModel(row);
        int consultId = (Integer) model.getValueAt(m, 0);
        String date = String.valueOf(model.getValueAt(m, 1));
        String day = String.valueOf(model.getValueAt(m, 2));
        String time = String.valueOf(model.getValueAt(m, 3));
        String status = String.valueOf(model.getValueAt(m, 4));
        String reason = model.getColumnCount() > 5 ? String.valueOf(model.getValueAt(m, 5)) : "";

        JTextArea reasonArea = new JTextArea(reason.isEmpty() ? "No reason was given." : reason, 6, 34);
        reasonArea.setEditable(false);
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);
        reasonArea.setBackground(Ui.FIELD);
        reasonArea.setFont(Ui.font(Font.PLAIN, 15));
        reasonArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        String[] info = consultInfo.getOrDefault(consultId, new String[] {"N/A", "N/A"});

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel("<html><b>" + date + "</b> (" + day + ")<br>" + time
            + "<br>Subject: <b>" + info[0] + "</b>"
            + "<br>Instructor: <b>" + info[1] + "</b>"
            + "<br>Status: <b>" + status + "</b></html>"), BorderLayout.NORTH);
        if (!reason.isEmpty() || "Cancelled".equals(status)) {
            JPanel reasonBox = new JPanel(new BorderLayout(0, 4));
            reasonBox.add(new JLabel("Reason:"), BorderLayout.NORTH);
            reasonBox.add(new JScrollPane(reasonArea), BorderLayout.CENTER);
            panel.add(reasonBox, BorderLayout.CENTER);
        }

        Long deadline = cancelDeadline.get(consultId);
        long msLeft = deadline == null ? 0 : deadline - System.currentTimeMillis();
        boolean canCancel = "Pending".equals(status) && msLeft > 0;

        if (!canCancel) {
            if ("Pending".equals(status)) {
                panel.add(new JLabel("<html><font color='#888888'>The "
                    + ConsultationSchedule.CANCEL_WINDOW_MINUTES
                    + "-minute cancellation window has ended.</font></html>"), BorderLayout.SOUTH);
            }
            JOptionPane.showMessageDialog(this, panel, "Consultation Details", JOptionPane.PLAIN_MESSAGE);
            return;
        }

        long mins = msLeft / 60000;
        long secs = (msLeft / 1000) % 60;
        panel.add(new JLabel(String.format(
            "<html><font color='#2e7d32'>You can still cancel this request (%d:%02d left).</font></html>",
            mins, secs)), BorderLayout.SOUTH);

        int pick = JOptionPane.showOptionDialog(this, panel, "Consultation Details",
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
            new String[] {"Cancel Request", "Close"}, "Close");
        if (pick == 0) {
            cancelRequest(consultId);
        }
    }

    // Asks the student why they are cancelling. Returns null if they back out; the reason is required.
    private String askCancelReason() {
        JTextArea reasonArea = new JTextArea(5, 30);
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);
        reasonArea.setFont(Ui.font(Font.PLAIN, 15));

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel("Please tell the instructor why you are cancelling:"), BorderLayout.NORTH);
        panel.add(new JScrollPane(reasonArea), BorderLayout.CENTER);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, panel, "Cancel Request",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return null;
            }
            String reason = reasonArea.getText().trim();
            if (!reason.isEmpty()) {
                return reason;
            }
            JOptionPane.showMessageDialog(this, "Please enter a cancellation reason.",
                "Reason Required", JOptionPane.WARNING_MESSAGE);
        }
    }

    // Cancels a pending request; the database re-checks the 20-minute window.
    private void cancelRequest(int consultId) {
        String reason = askCancelReason();
        if (reason == null) {
            return;
        }

        String sql =
            "UPDATE tbl_consult " +
            "SET status = 'Cancelled', cancelled_at = NOW(), " +
            "cancellation_reason = ? " +
            "WHERE consult_id = ? " +
            "AND stud_id = (SELECT stud_id FROM tbl_student WHERE stud_email = ?) " +
            "AND status = 'Pending' " +
            "AND requested_at IS NOT NULL " +
            "AND NOW() <= DATE_ADD(requested_at, INTERVAL " + ConsultationSchedule.CANCEL_WINDOW_MINUTES + " MINUTE)";

        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, reason);
            stmt.setInt(2, consultId);
            stmt.setString(3, studentEmail);

            if (stmt.executeUpdate() == 1) {
                JOptionPane.showMessageDialog(this, "Your request has been cancelled.");
            } else {
                JOptionPane.showMessageDialog(this,
                    "Cancellation failed. The " + ConsultationSchedule.CANCEL_WINDOW_MINUTES
                    + "-minute window may have ended, or the instructor already responded.",
                    "Unable to Cancel", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception e) {
            showDbError("Unable to cancel request", e);
        }
        loadHistory();
        updateStats();
        loadSlots();
    }

    private void showDbError(String message, Exception e) {
        JOptionPane.showMessageDialog(this, message + ":\n" + e.getMessage(),
            "Database Error", JOptionPane.ERROR_MESSAGE);
        e.printStackTrace();
    }
    //------------------------------------------------------------------

    public static void main(String[] args) {
        
    }
}