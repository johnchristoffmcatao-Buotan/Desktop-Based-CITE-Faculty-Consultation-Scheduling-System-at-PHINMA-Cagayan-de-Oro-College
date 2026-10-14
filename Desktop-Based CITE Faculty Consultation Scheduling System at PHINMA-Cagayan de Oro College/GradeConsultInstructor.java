import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class GradeConsultInstructor extends JFrame {

    // Name of the primary-key column of tbl_consult
    private static final String CONSULT_PK = "consult_id";
    private static final String PAGE_DASHBOARD = "dashboard";
    private static final String PAGE_ACCEPTED = "accepted";
    private static final String PAGE_SCHEDULE = "schedule";
    private static final String PAGE_HISTORY = "history";

    private enum Mode { VIEW, ADD, EDIT }

    // One row of tbl_consult_sched.
    private static final class SchedRow {
        final int id;
        final String time;
        final String status;
        final boolean hasRequest;

        SchedRow(int id, String time, String status, boolean hasRequest) {
            this.id = id;
            this.time = time;
            this.status = status;
            this.hasRequest = hasRequest;
        }

        boolean open() {
            return "Available".equals(status);
        }

        // Keep a slot that has consultation history so its date/time remains available to history joins.
        boolean locked() {
            return !open() || hasRequest;
        }
    }

    private final String instructorName;
    private final String instructorEmail;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel pages = new JPanel(cardLayout);
    private Ui.Sidebar sidebar;
    private Ui.NavItem navDashboard;
    private Ui.NavItem navAccepted;
    private Ui.NavItem navSchedule;
    private Ui.NavItem navHistory;

    private final JLabel lblHello = Ui.label("", Font.PLAIN, 32, Ui.TEXT);
    private final JLabel lblSub = Ui.label("", Font.PLAIN, 17, Ui.MUTED);
    private final JLabel lblTotal = new JLabel("0");
    private final JLabel lblPending = new JLabel("0");
    private final JLabel lblActive = new JLabel("0");

    // Row layout: [hidden consult id, student, section, date, last column]
    private final DefaultTableModel pendingModel =
        Ui.model("ID", "Student name", "Section", "Date of Consultation", "");
    private final DefaultTableModel acceptedModel =
        Ui.model("ID", "Student name", "Section", "Date of Consultation", "");
    // consult id -> {subject, day, time, student email} for the history details box
    private final Map<Integer, String[]> historyInfo = new HashMap<>();
    private final DefaultTableModel historyModel =
        Ui.model("ID", "Student name", "Section", "Date of Consultation", "Status", "Cancellation Reason");
    private JTable pendingTable;
    private JTable acceptedTable;
    private JButton btnAcceptAll;
    private JButton btnRejectAll;
    private JButton btnCancelAll;

    // Schedule calendar
    private CalendarPanel calendar;
    private JButton btnLeft;   // EDIT   / CANCEL / DONE
    private JButton btnRight;  // ADD    / SAVE
    private JLabel banner;
    private Mode mode = Mode.VIEW;
    private final Map<LocalDate, List<SchedRow>> schedule = new TreeMap<>();
    private Timer consultationTimer;

    public GradeConsultInstructor(String name, String email) {
        Ui.install();
        this.instructorName = name;
        this.instructorEmail = email;

        setTitle("PHINMA COC - Instructor");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        if (Ui.logoImage() != null) {
            setIconImage(Ui.logoImage());
        }

        buildUi();
        lblHello.setText("Hello! " + instructorName);
        lblSub.setText("Instructor - " + "CITE Department");
        reloadConsultations();
        loadSchedule();
        setMode(Mode.VIEW);

        consultationTimer = new Timer(1000, e -> {
            reloadConsultations();
        });

        consultationTimer.start();

        setSize(1280, 760);
        setMinimumSize(new Dimension(1020, 660));
        setLocationRelativeTo(null);
        showPage(PAGE_DASHBOARD);
    }
// ------------------------------------------------------------------

// Navigation Panel
    void showPage(String page) {
        cardLayout.show(pages, page);
        switch (page) {
            case PAGE_ACCEPTED:
                sidebar.select(navAccepted);
                reloadConsultations();
                break;
            case PAGE_SCHEDULE:
                sidebar.select(navSchedule);
                loadSchedule();
                break;
            case PAGE_HISTORY:
                sidebar.select(navHistory);
                reloadConsultations();
                break;
            default:
                sidebar.select(navDashboard);
                reloadConsultations();
                break;
        }
        if (!PAGE_SCHEDULE.equals(page) && mode != Mode.VIEW) {
            setMode(Mode.VIEW);
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
    
    // UI: Pending, accepted, history, schedule
    private void buildUi() {
        sidebar = new Ui.Sidebar();
        navDashboard = sidebar.addNav(Ui.Glyph.DASHBOARD, "DASHBOARD", () -> showPage(PAGE_DASHBOARD));
        navAccepted = sidebar.addNav(Ui.Glyph.CHECK, "ACCEPTED\nCONSULTATION", () -> showPage(PAGE_ACCEPTED));
        navSchedule = sidebar.addNav(Ui.Glyph.CALENDAR, "MY SCHEDULE", () -> showPage(PAGE_SCHEDULE));
        navHistory = sidebar.addNav(Ui.Glyph.HISTORY, "CONSULTATION\nHISTORY", () -> showPage(PAGE_HISTORY));
        sidebar.addLogout("Log out", this::logout);
        
        pages.setOpaque(false);
        pages.add(buildDashboardPage(), PAGE_DASHBOARD);
        pages.add(buildAcceptedPage(), PAGE_ACCEPTED);
        pages.add(buildSchedulePage(), PAGE_SCHEDULE);
        pages.add(buildHistoryPage(), PAGE_HISTORY);
        
        Ui.Shell shell = new Ui.Shell();
        shell.add(pages, BorderLayout.CENTER);
        
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(sidebar, BorderLayout.WEST);
        getContentPane().add(shell, BorderLayout.CENTER);
    }
// ------------------------------------------------------------------
    
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
            Ui.statCard(lblTotal, "Total"),
            Ui.statCard(lblPending, "Pending"),
            Ui.statCard(lblActive, "Active"))));
            top.add(Box.createVerticalStrut(40));
            top.add(Ui.left(Ui.titleRow("Pending Consultation Requests", Ui.Glyph.PENDING)));
            top.add(Box.createVerticalStrut(6));
            top.add(Ui.left(Ui.label("Select a student (Ctrl+click for several)", Font.PLAIN, 14, Ui.MUTED)));
            top.add(Box.createVerticalStrut(10));
            
            btnAcceptAll = Ui.button("Accept All (0)");
            btnRejectAll = Ui.button("Reject All (0)");
            btnAcceptAll.setPreferredSize(new Dimension(180, 40));
        btnRejectAll.setPreferredSize(new Dimension(180, 40));
        btnAcceptAll.addActionListener(e -> acceptAllPending());
        btnRejectAll.addActionListener(e -> rejectAllPending());
        JPanel bulkButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bulkButtons.setOpaque(false);
        bulkButtons.add(btnAcceptAll);
        bulkButtons.add(Box.createHorizontalStrut(10));
        bulkButtons.add(btnRejectAll);
        // must be left-aligned like the other rows, otherwise BoxLayout shifts the whole block
        bulkButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        top.add(Ui.left(bulkButtons));
        top.add(Box.createVerticalStrut(10));
        page.add(top, BorderLayout.NORTH);

        pendingTable = new JTable();
        page.add(Ui.table(pendingTable, pendingModel, true, 0, 330, 260, 260, 120), BorderLayout.CENTER);
        Ui.actionColumn(pendingTable, 3);

        pendingTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = pendingTable.rowAtPoint(e.getPoint());
                if (row < 0) {
                    pendingTable.clearSelection();   // click on empty area -> back to bulk mode
                } else if (e.getClickCount() == 2) {
                    handlePending(row);
                }
            }
        });
        pendingTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateBulkButtons();
            }
        });
        return page;
    }

    private JPanel buildAcceptedPage() {
        JPanel page = Ui.pagePanel();
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(Ui.left(Ui.titleRow("Accepted Consultation", Ui.Glyph.CHECK)));
        top.add(Box.createVerticalStrut(6));
        top.add(Ui.left(Ui.label("Select a student (Ctrl+click for several) to cancel only them; with nothing selected Cancel applies to everyone. Cancelling only works within 20 minutes of acceptance. Double-click a row to complete or cancel it.",
            Font.PLAIN, 14, Ui.MUTED)));
        top.add(Box.createVerticalStrut(10));
        btnCancelAll = Ui.button("Cancel All (0)");
        btnCancelAll.setPreferredSize(new Dimension(180, 40));
        btnCancelAll.addActionListener(e -> cancelAllAccepted());
        top.add(Ui.left(btnCancelAll));
        top.add(Box.createVerticalStrut(12));
        page.add(top, BorderLayout.NORTH);

        acceptedTable = new JTable();
        page.add(Ui.table(acceptedTable, acceptedModel, true, 0, 330, 260, 260, 120), BorderLayout.CENTER);
        Ui.actionColumn(acceptedTable, 3);

        acceptedTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = acceptedTable.rowAtPoint(e.getPoint());
                if (row < 0) {
                    acceptedTable.clearSelection();
                } else if (e.getClickCount() == 2) {
                    completeConsultation(row);
                }
            }
        });
        acceptedTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateBulkButtons();
            }
        });
        return page;
    }

    private JPanel buildHistoryPage() {
        JPanel page = Ui.pagePanel();
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        top.add(Ui.titleRow("CONSULTATION HISTORY", null), BorderLayout.WEST);
        page.add(top, BorderLayout.NORTH);

        JTable table = new JTable();
        page.add(Ui.table(table, historyModel, true, 0, 330, 260, 260, 120), BorderLayout.CENTER);

        // Click a row to read the full status / cancellation reason (it may not fit in the cell).
        table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    showHistoryDetails(table, row);
                }
            }
        });
        return page;
    }

    private void showHistoryDetails(JTable table, int row) {
        int m = table.convertRowIndexToModel(row);
        int consultId = (Integer) historyModel.getValueAt(m, 0);
        String[] info = historyInfo.getOrDefault(consultId, new String[] {"N/A", "", "", ""});
        String student = String.valueOf(historyModel.getValueAt(m, 1));
        String section = String.valueOf(historyModel.getValueAt(m, 2));
        String date = String.valueOf(historyModel.getValueAt(m, 3));
        String status = String.valueOf(historyModel.getValueAt(m, 4));
        String reason = String.valueOf(historyModel.getValueAt(m, 5));

        JTextArea reasonArea = new JTextArea(reason.isEmpty() ? "No reason was given." : reason, 6, 34);
        reasonArea.setEditable(false);
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);
        reasonArea.setBackground(Ui.FIELD);
        reasonArea.setFont(Ui.font(Font.PLAIN, 15));
        reasonArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel("<html><b>" + student + "</b> (" + section + ")"
            + "<br>Email: " + info[3]
            + "<br>Subject: <b>" + info[0] + "</b>"
            + "<br>Date: " + date + " (" + info[1] + ")"
            + "<br>Time: " + info[2]
            + "<br>Status: <b>" + status + "</b></html>"), BorderLayout.NORTH);
        if (!reason.isEmpty() || "Cancelled".equals(status) || "Rejected".equals(status)) {
            JPanel reasonBox = new JPanel(new BorderLayout(0, 4));
            reasonBox.add(new JLabel("Reason:"), BorderLayout.NORTH);
            reasonBox.add(new JScrollPane(reasonArea), BorderLayout.CENTER);
            panel.add(reasonBox, BorderLayout.CENTER);
        }
        JOptionPane.showMessageDialog(this, panel, "Consultation Details", JOptionPane.PLAIN_MESSAGE);
    }

    private JPanel buildSchedulePage() {
        JPanel page = Ui.pagePanel();

        calendar = new CalendarPanel();
        // VIEW and EDIT: only days that already have a schedule react.
        // ADD: any day from today on can be highlighted (past days are locked by the calendar).
        calendar.setClickable(d -> mode == Mode.ADD || schedule.containsKey(d));
        calendar.setClickHandler(this::onDayClicked);
        calendar.setSelectionListener(this::updateSaveButton);
        page.add(calendar, BorderLayout.CENTER);

        banner = Ui.label(" ", Font.PLAIN, 15, Ui.TEXT);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        legend.setOpaque(false);
        legend.add(legendItem(Ui.GREEN_LIGHT, "Open slots"));
        legend.add(legendItem(Ui.AMBER, "Fully booked"));
        legend.add(legendItem(Ui.GREEN, "Highlighted"));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(Ui.left(banner));
        info.add(Box.createVerticalStrut(6));
        info.add(Ui.left(legend));

        btnLeft = Ui.button("EDIT");
        btnRight = Ui.button("ADD");
        for (JButton b : new JButton[] {btnLeft, btnRight}) {
            b.setPreferredSize(new Dimension(140, 46));
        }
        btnLeft.addActionListener(e -> onLeftButton());
        btnRight.addActionListener(e -> onRightButton());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        buttons.setOpaque(false);
        buttons.add(btnLeft);
        buttons.add(btnRight);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        south.add(info, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.EAST);
        page.add(south, BorderLayout.SOUTH);
        return page;
    }

    private JLabel legendItem(Color color, String text) {
        JLabel l = Ui.label(text, Font.PLAIN, 13, Ui.MUTED);
        l.setIcon(new Icon() {
            @Override
            public int getIconWidth() {
                return 16;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setColor(color);
                g.fillRect(x, y, 16, 16);
                g.setColor(Ui.BORDER);
                g.drawRect(x, y, 15, 15);
            }
        });
        l.setIconTextGap(6);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 18));
        return l;
    }

    private boolean reloading;

    private void reloadConsultations() {
        // The tables are rebuilt every second, which would wipe the selection, so remember it.
        List<Integer> keepPending = selectedIds(pendingTable, pendingModel);
        List<Integer> keepAccepted = selectedIds(acceptedTable, acceptedModel);

        reloading = true;
        try {
            loadConsultTable(pendingModel, "Pending", "Review", "ASC", "pending consultations");
            loadConsultTable(acceptedModel, "Approved", null, "ASC", "accepted consultations");
            loadHistoryTable();
            updateStats();
            restoreSelection(pendingTable, pendingModel, keepPending);
            restoreSelection(acceptedTable, acceptedModel, keepAccepted);
        } finally {
            reloading = false;
        }
        updateBulkButtons();
    }

    private void restoreSelection(JTable table, DefaultTableModel model, List<Integer> ids) {
        if (table == null || ids.isEmpty()) {
            return;
        }
        for (int r = 0; r < model.getRowCount(); r++) {
            if (ids.contains((Integer) model.getValueAt(r, 0))) {
                int viewRow = table.convertRowIndexToView(r);
                table.addRowSelectionInterval(viewRow, viewRow);
            }
        }
    }

    // Buttons say "Selected (n)" when students are selected, otherwise "All (n)".
    private void updateBulkButtons() {
        if (reloading) {
            return;
        }
        int selPending = pendingTable == null ? 0 : pendingTable.getSelectedRowCount();
        int selAccepted = acceptedTable == null ? 0 : acceptedTable.getSelectedRowCount();
        String pendingLabel = selPending > 0 ? "Selected (" + selPending + ")"
            : "All (" + pendingModel.getRowCount() + ")";
        String acceptedLabel = selAccepted > 0 ? "Selected (" + selAccepted + ")"
            : "All (" + acceptedModel.getRowCount() + ")";
        if (btnAcceptAll != null) btnAcceptAll.setText("Accept " + pendingLabel);
        if (btnRejectAll != null) btnRejectAll.setText("Reject " + pendingLabel);
        if (btnCancelAll != null) btnCancelAll.setText("Cancel " + acceptedLabel);
    }

    private void loadHistoryTable() {
        historyModel.setRowCount(0);
        String sql =
            "SELECT c.consult_id, s.stud_name, s.stud_email, s.section, cs.consult_date, " +
            "cs.day, cs.available_time, sub.sub_code, sub.sub_name, " +
            "c.status, c.cancellation_reason " +
            "FROM tbl_consult c " +
            "JOIN tbl_student s ON c.stud_id = s.stud_id " +
            "LEFT JOIN tbl_subject sub ON c.sub_id = sub.sub_id " +
            "JOIN tbl_consult_sched cs ON c.sched_id = cs.sched_id " +
            "WHERE c.inst_id = (SELECT inst_id FROM tbl_instructor WHERE inst_email = ?) " +
            "AND c.status IN ('Completed', 'Rejected', 'Cancelled') " +
            "ORDER BY cs.consult_date DESC, c.consult_id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, instructorEmail);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String reason = rs.getString("cancellation_reason");
                    String subCode = rs.getString("sub_code");
                    historyInfo.put(rs.getInt("consult_id"), new String[] {
                        subCode == null ? "N/A" : subCode + " - " + rs.getString("sub_name"),
                        rs.getString("day"),
                        rs.getString("available_time"),
                        rs.getString("stud_email")
                    });
                    historyModel.addRow(new Object[] {
                        rs.getInt("consult_id"), rs.getString("stud_name"),
                        rs.getString("section"), rs.getDate("consult_date"),
                        rs.getString("status"), reason == null ? "" : reason
                    });
                }
            }
        } catch (Exception e) {
            showDbError("Unable to load consultation history", e);
        }
    }

    private void loadConsultTable(
            DefaultTableModel model,
            String status,
            String lastColumn,
            String order,
            String label) {

        model.setRowCount(0);

        String sql =
            "SELECT c.consult_id AS consult_pk, " +
            "s.stud_name, s.stud_email, s.section, cs.consult_date, c.status, " +
            "cs.day, cs.available_time, sub.sub_code, sub.sub_name, " +
            "c.accepted_at, " +
            "GREATEST(0, TIMESTAMPDIFF(SECOND, NOW(), " +
            "DATE_ADD(c.accepted_at, INTERVAL 20 MINUTE))) AS seconds_left " +
            "FROM tbl_consult c " +
            "JOIN tbl_student s ON c.stud_id = s.stud_id " +
            "JOIN tbl_consult_sched cs ON c.sched_id = cs.sched_id " +
            "LEFT JOIN tbl_subject sub ON c.sub_id = sub.sub_id " +
            "WHERE c.inst_id = (" +
            "SELECT inst_id FROM tbl_instructor WHERE inst_email = ?" +
            ") AND c.status = ? " +
            "ORDER BY cs.consult_date " + order;

        try (
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, instructorEmail);
            stmt.setString(2, status);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String action = lastColumn != null ? lastColumn : rs.getString("status");

                if ("Approved".equals(status)) {
                    int seconds = rs.getInt("seconds_left");

                    if (rs.getTimestamp("accepted_at") == null) {
                        action = "Timer unavailable";
                    } else if (seconds <= 0) {
                        action = "Cancellation window ended";
                    } else {
                        action = String.format(
                            "Cancel: %02d:%02d",
                            seconds / 60,
                            seconds % 60
                        );
                    }
                }

                String subCode = rs.getString("sub_code");
                historyInfo.put(rs.getInt("consult_pk"), new String[] {
                    subCode == null ? "N/A" : subCode + " - " + rs.getString("sub_name"),
                    rs.getString("day"),
                    rs.getString("available_time"),
                    rs.getString("stud_email")
                });
                model.addRow(new Object[] {
                    rs.getInt("consult_pk"),
                    rs.getString("stud_name"),
                    rs.getString("section"),
                    rs.getString("consult_date"),
                    action
                });
            }

        } catch (Exception e) {
            showDbError("Unable to load " + label, e);
        }
    }

    private void updateStats() {
        int pending = pendingModel.getRowCount();
        int active = acceptedModel.getRowCount();
        lblTotal.setText(String.valueOf(pending + active));
        lblPending.setText(String.valueOf(pending));
        lblActive.setText(String.valueOf(active));
    }

    private void completeConsultation(int row) {

    int id = (Integer) acceptedModel.getValueAt(row, 0);
    String student = String.valueOf(acceptedModel.getValueAt(row, 1));
    String section = String.valueOf(acceptedModel.getValueAt(row, 2));
    String date = String.valueOf(acceptedModel.getValueAt(row, 3));

    boolean canCancel = String.valueOf(acceptedModel.getValueAt(row, 4)).startsWith("Cancel:");
    String[] options = canCancel
        ? new String[] {"Complete", "Cancel Consultation", "Close"}
        : new String[] {"Complete", "Close"};

    int choice = JOptionPane.showOptionDialog(
        this,
        "<html><b>Accepted Consultation</b><br><br>" +
        detailsHtml(id, student, section, date) + "<br><br>" +
        (canCancel ? "Remaining cancellation time: " + acceptedModel.getValueAt(row, 4)
                   : "The 20-minute cancellation window has ended.") + "</html>",
        "Manage Accepted Consultation",
        JOptionPane.DEFAULT_OPTION,
        JOptionPane.QUESTION_MESSAGE,
        null,
        options,
        options[0]
    );

    if (choice == 0) {
        if (setConsultStatus(id, "Completed", "Approved")) {
            reloadConsultations();
        }
    } else if (choice == 1 && canCancel) {
        cancelConsultation(id, student);
    }
}

    // Full consultation details (student, subject, date, time) used by the pending / accepted dialogs.
    private String detailsHtml(int id, String student, String section, String date) {
        String[] info = historyInfo.getOrDefault(id, new String[] {"N/A", "", "", ""});
        return "Student: <b>" + student + "</b> (" + section + ")<br>" +
            "Email: " + info[3] + "<br>" +
            "Subject: <b>" + info[0] + "</b><br>" +
            "Date: <b>" + date + "</b> (" + info[1] + ")<br>" +
            "Time: <b>" + info[2] + "</b>";
    }

    private void handlePending(int row) {
        int id = (Integer) pendingModel.getValueAt(row, 0);
        String student = String.valueOf(pendingModel.getValueAt(row, 1));
        String section = String.valueOf(pendingModel.getValueAt(row, 2));
        String date = String.valueOf(pendingModel.getValueAt(row, 3));

        String[] opts = {"Approve", "Reject", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
            "<html><b>Consultation Request</b><br><br>" +
            detailsHtml(id, student, section, date) + "</html>",
            "Manage Request",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null, opts, opts[0]);

        if (choice == 0) {
            if (approveConsultation(id)) {
                reloadConsultations();
                loadSchedule();
                JOptionPane.showMessageDialog(this,
                    "Approved! The student will see it in their history.");
            }
        } else if (choice == 1) {
            if (setConsultStatus(id, "Rejected", "Pending")) {
                reloadConsultations();
                JOptionPane.showMessageDialog(this,
                    "Rejected. The student will see it in their history.");
            }
        }
    }

    // Changes a consultation's status, only if it currently has `required` status.
    private boolean setConsultStatus(int consultId, String newStatus, String required) {
        String sql =
            "UPDATE tbl_consult SET status = ? " +
            "WHERE " + CONSULT_PK + " = ? AND status = ?";

        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newStatus);
            stmt.setInt(2, consultId);
            stmt.setString(3, required);

            if (stmt.executeUpdate() == 0) {
                JOptionPane.showMessageDialog(this,
                    "This consultation was already updated.",
                    "Not Updated", JOptionPane.WARNING_MESSAGE);
                reloadConsultations();
                return false;
            }
            return true;

        } catch (Exception e) {
            showDbError("Unable to update consultation", e);
            return false;
        }
    }

    // Approve only this pending request. A schedule is shared availability, not an exclusive booking.
    private boolean approveConsultation(int consultId) {
        String sql =
            "UPDATE tbl_consult SET status = 'Approved', accepted_at = NOW() " +
            "WHERE consult_id = ? AND status = 'Pending' " +
            "AND inst_id = (SELECT inst_id FROM tbl_instructor WHERE inst_email = ?)";
        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, consultId);
            stmt.setString(2, instructorEmail);
            if (stmt.executeUpdate() == 1) return true;
            JOptionPane.showMessageDialog(this, "This request was already handled or is not yours.",
                "Not Updated", JOptionPane.WARNING_MESSAGE);
            return false;
        } catch (Exception e) {
            showDbError("Unable to approve consultation", e);
            return false;
        }
    }

    // Consultation ids selected in a table; empty list = nothing selected (bulk mode).
    private List<Integer> selectedIds(JTable table, DefaultTableModel model) {
        List<Integer> ids = new ArrayList<>();
        if (table == null) {
            return ids;
        }
        for (int viewRow : table.getSelectedRows()) {
            ids.add((Integer) model.getValueAt(table.convertRowIndexToModel(viewRow), 0));
        }
        return ids;
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(i == 0 ? "?" : ",?");
        }
        return sb.toString();
    }

    private static final String WINDOW_OK =
        " AND c.accepted_at IS NOT NULL AND NOW() <= DATE_ADD(c.accepted_at, INTERVAL 20 MINUTE)";

    // Counts this instructor's consultations with a status. ids == null/empty -> all of them,
    // otherwise only those ids. windowOnly -> only those still inside the 20-minute cancel window.
    private int countConsultations(String status, List<Integer> ids, boolean windowOnly) throws Exception {
        boolean some = ids != null && !ids.isEmpty();
        String sql = "SELECT COUNT(*) FROM tbl_consult c " +
            "WHERE c.inst_id = (SELECT inst_id FROM tbl_instructor WHERE inst_email = ?) " +
            "AND c.status = ?" +
            (some ? " AND c.consult_id IN (" + placeholders(ids.size()) + ")" : "") +
            (windowOnly ? WINDOW_OK : "");
        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            int i = 1;
            stmt.setString(i++, instructorEmail);
            stmt.setString(i++, status);
            if (some) {
                for (int id : ids) {
                    stmt.setInt(i++, id);
                }
            }
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // Shared by Accept / Reject: updates the selected pending requests, or all of them if none selected.
    private void bulkPending(boolean accept) {
        String verb = accept ? "accept" : "reject";
        List<Integer> ids = selectedIds(pendingTable, pendingModel);
        boolean some = !ids.isEmpty();
        int count;
        try { count = countConsultations("Pending", ids, false); }
        catch (Exception e) { showDbError("Unable to count pending requests", e); return; }
        if (count == 0) {
            JOptionPane.showMessageDialog(this, "There are no pending requests to " + verb + ".");
            return;
        }
        String who = some ? count + " selected request(s)" : "all " + count + " pending request(s)";
        int choice = JOptionPane.showConfirmDialog(this,
            "Do you want to " + verb + " " + who + "?",
            "Confirm " + (accept ? "Accept" : "Reject"), JOptionPane.YES_NO_OPTION,
            accept ? JOptionPane.QUESTION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        String sql = (accept
            ? "UPDATE tbl_consult SET status = 'Approved', accepted_at = NOW() "
            : "UPDATE tbl_consult SET status = 'Rejected' ") +
            "WHERE status = 'Pending' AND inst_id = (SELECT inst_id FROM tbl_instructor WHERE inst_email = ?)" +
            (some ? " AND consult_id IN (" + placeholders(ids.size()) + ")" : "");
        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            int i = 1;
            stmt.setString(i++, instructorEmail);
            if (some) {
                for (int id : ids) {
                    stmt.setInt(i++, id);
                }
            }
            int changed = stmt.executeUpdate();
            pendingTable.clearSelection();
            reloadConsultations();
            loadSchedule();
            JOptionPane.showMessageDialog(this, (accept ? "Accepted " : "Rejected ") + changed
                + " request(s). Students can see the result in History.");
        } catch (Exception e) { showDbError("Unable to " + verb + " requests", e); }
    }

    private void acceptAllPending() { bulkPending(true); }

    private void rejectAllPending() { bulkPending(false); }

    // Cancel the selected accepted consultations (or all if none selected). Only those still
    // inside the 20-minute window from acceptance can be cancelled; the rest are skipped.
    private void cancelAllAccepted() {
        List<Integer> ids = selectedIds(acceptedTable, acceptedModel);
        boolean some = !ids.isEmpty();
        int total, eligible;
        try {
            total = countConsultations("Approved", ids, false);
            eligible = countConsultations("Approved", ids, true);
        } catch (Exception e) { showDbError("Unable to check cancellation windows", e); return; }
        if (total == 0) {
            JOptionPane.showMessageDialog(this, "There are no accepted consultations to cancel.");
            return;
        }
        if (eligible == 0) {
            JOptionPane.showMessageDialog(this,
                (some ? "The selected consultation(s) are" : "None of the " + total + " accepted consultation(s) are")
                + " no longer within the 20-minute cancellation window, so they can't be cancelled.",
                "Cancellation Window Ended", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JTextArea reasonArea = new JTextArea(4, 28);
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(new JLabel("Reason for cancelling " + (some ? "the selected" : "all eligible") + " consultation(s):"),
            BorderLayout.NORTH);
        panel.add(new JScrollPane(reasonArea), BorderLayout.CENTER);
        panel.add(new JLabel(eligible + " can be cancelled; " + (total - eligible)
            + " past the 20-minute window will be skipped."), BorderLayout.SOUTH);
        int choice = JOptionPane.showConfirmDialog(this, panel, some ? "Cancel Selected" : "Cancel All",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;
        String reason = reasonArea.getText().trim();
        if (reason.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a cancellation reason.", "Reason Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String sql = "UPDATE tbl_consult SET status = 'Cancelled', cancelled_at = NOW(), cancellation_reason = ? " +
            "WHERE inst_id = (SELECT inst_id FROM tbl_instructor WHERE inst_email = ?) " +
            "AND status = 'Approved' AND accepted_at IS NOT NULL " +
            "AND NOW() <= DATE_ADD(accepted_at, INTERVAL 20 MINUTE)" +
            (some ? " AND consult_id IN (" + placeholders(ids.size()) + ")" : "");
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int i = 1;
            stmt.setString(i++, reason);
            stmt.setString(i++, instructorEmail);
            if (some) {
                for (int id : ids) {
                    stmt.setInt(i++, id);
                }
            }
            int cancelled = stmt.executeUpdate();
            acceptedTable.clearSelection();
            reloadConsultations();
            loadSchedule();
            JOptionPane.showMessageDialog(this,
                "Cancelled: " + cancelled +
                (total - cancelled > 0 ? "\nSkipped (past the 20-minute window or already updated): " + (total - cancelled) : ""));
        } catch (Exception e) { showDbError("Unable to cancel consultations", e); }
    }

    private void cancelConsultation(int consultId, String studentName) {

    JTextArea reasonArea = new JTextArea(4, 28);
    reasonArea.setLineWrap(true);
    reasonArea.setWrapStyleWord(true);

    JPanel panel = new JPanel(new BorderLayout(5, 5));
    panel.add(
        new JLabel("Why are you cancelling " + studentName + "'s consultation?"),
        BorderLayout.NORTH
    );
    panel.add(new JScrollPane(reasonArea), BorderLayout.CENTER);

    int choice = JOptionPane.showConfirmDialog(
        this,
        panel,
        "Cancel Consultation",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.WARNING_MESSAGE
    );

    if (choice != JOptionPane.OK_OPTION) {
        return;
    }

    String reason = reasonArea.getText().trim();

    if (reason.isEmpty()) {
        JOptionPane.showMessageDialog(
            this,
            "Please enter a cancellation reason."
        );
        return;
    }

    String sql =
        "UPDATE tbl_consult " +
        "SET status = 'Cancelled', " +
        "cancelled_at = NOW(), cancellation_reason = ? " +
        "WHERE consult_id = ? " +
        "AND inst_id = (SELECT inst_id FROM tbl_instructor WHERE inst_email = ?) " +
        "AND status = 'Approved' " +
        "AND accepted_at IS NOT NULL " +
        "AND NOW() <= DATE_ADD(accepted_at, INTERVAL 20 MINUTE)";

    try (
        Connection conn = DatabaseConnection.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)
    ) {
        stmt.setString(1, reason);
        stmt.setInt(2, consultId);
        stmt.setString(3, instructorEmail);

        if (stmt.executeUpdate() == 1) {
            JOptionPane.showMessageDialog(
                this,
                "Consultation cancelled. The student can see the reason in History."
            );

            reloadConsultations();
            loadSchedule();

        } else {
            JOptionPane.showMessageDialog(
                this,
                "Cancellation failed. The 20-minute window may have ended, " +
                "or the consultation was already updated.",
                "Unable to Cancel",
                JOptionPane.WARNING_MESSAGE
            );

            reloadConsultations();
        }

    } catch (Exception e) {
        showDbError("Unable to cancel consultation", e);
    }
}

    // Schedule calendar: data
    private void loadSchedule() {
        schedule.clear();

        String sql =
            "SELECT cs.sched_id, cs.consult_date, cs.available_time, cs.status, " +
            "EXISTS(SELECT 1 FROM tbl_consult c WHERE c.sched_id = cs.sched_id) AS has_req " +
            "FROM tbl_consult_sched cs " +
            "WHERE cs.inst_id = (" +
            "SELECT inst_id FROM tbl_instructor WHERE inst_email = ?" +
            ") " +
            "ORDER BY cs.consult_date";

        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, instructorEmail);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                LocalDate date = CalendarPanel.parseDate(rs.getString("consult_date"));
                if (date == null) {
                    continue;
                }
                schedule.computeIfAbsent(date, k -> new ArrayList<>()).add(new SchedRow(
                    rs.getInt("sched_id"),
                    rs.getString("available_time"),
                    rs.getString("status"),
                    rs.getBoolean("has_req")));
            }

        } catch (Exception e) {
            showDbError("Unable to load schedule", e);
        }

        for (List<SchedRow> rows : schedule.values()) {
            rows.sort((a, b) -> {
                int c = Integer.compare(ConsultationSchedule.slotIndex(a.time),
                    ConsultationSchedule.slotIndex(b.time));
                return c != 0 ? c : String.valueOf(a.time).compareTo(String.valueOf(b.time));
            });
        }
        paintScheduleMarks();
    }

    private void paintScheduleMarks() {
        Map<LocalDate, String> notes = new HashMap<>();
        Map<LocalDate, Color> tints = new HashMap<>();
        for (Map.Entry<LocalDate, List<SchedRow>> e : schedule.entrySet()) {
            int open = 0;
            int booked = 0;
            int passed = 0;
            for (SchedRow r : e.getValue()) {
                if (!r.open()) {
                    booked++;
                } else if (ConsultationSchedule.isPastSlot(e.getKey(), r.time)) {
                    passed++;   // unused slot whose time is already gone
                } else {
                    open++;
                }
            }
            List<String> lines = new ArrayList<>();
            if (open > 0) {
                lines.add(open + " open");
            }
            if (booked > 0) {
                lines.add(booked + " booked");
            }
            if (passed > 0) {
                lines.add(passed + " passed");
            }
            notes.put(e.getKey(), String.join("\n", lines));
            if (open > 0) {
                tints.put(e.getKey(), Ui.GREEN_LIGHT);
            } else if (booked > 0) {
                tints.put(e.getKey(), Ui.AMBER);
            }
        }
        calendar.setNotes(notes);
        calendar.setTints(tints);
    }

    // Schedule calendar: EDIT / ADD modes
    private void setMode(Mode m) {
        mode = m;
        calendar.clearSelection();
        switch (m) {
            case ADD:
                btnLeft.setText("CANCEL");
                btnRight.setText("SAVE");
                btnLeft.setEnabled(true);
                btnRight.setEnabled(true);
                banner.setText("ADD: click the days you are available, then press SAVE.");
                break;
            case EDIT:
                btnLeft.setText("DONE");
                btnRight.setText("ADD");
                btnLeft.setEnabled(true);
                btnRight.setEnabled(false);
                banner.setText("EDIT: click a scheduled day to change its time slots.");
                break;
            default:
                btnLeft.setText("EDIT");
                btnRight.setText("ADD");
                btnLeft.setEnabled(true);
                btnRight.setEnabled(true);
                banner.setText("Press ADD to schedule new days, or EDIT to change them.");
                break;
        }
        calendar.repaint();
    }

    private void updateSaveButton() {
        if (mode == Mode.ADD && btnRight != null) {
            int n = calendar.getSelected().size();
            btnRight.setText(n > 0 ? "SAVE (" + n + ")" : "SAVE");
        }
    }

    private void onLeftButton() {
        setMode(mode == Mode.VIEW ? Mode.EDIT : Mode.VIEW);
    }

    private void onRightButton() {
        if (mode == Mode.VIEW) {
            setMode(Mode.ADD);
        } else if (mode == Mode.ADD) {
            saveAdd();
        }
    }

    private void onDayClicked(LocalDate date) {
        switch (mode) {
            case ADD:
                calendar.toggle(date);
                break;
            case EDIT:
                editDay(date);
                break;
            default:
                showDay(date);
                break;
        }
    }

    private static String prettyDate(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH));
    }

    // Read-only list of the slots on one day.
    private void showDay(LocalDate date) {
        List<SchedRow> rows = schedule.get(date);
        if (rows == null || rows.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder("<html><b>" + prettyDate(date) + "</b><br><br>");
        for (SchedRow r : rows) {
            String label = r.open() ? (ConsultationSchedule.isPastSlot(date, r.time) ? "Passed" : "Open") : r.status;
            sb.append(r.time).append(" - ").append(label).append("<br>");
        }
        sb.append("</html>");
        JOptionPane.showMessageDialog(this, sb.toString(), "Schedule", JOptionPane.PLAIN_MESSAGE);
    }

    // ADD -> SAVE: ask for the time slots, then add them to every highlighted day.
    private void saveAdd() {
        List<LocalDate> dates = calendar.getSelected();
        dates.removeIf(CalendarPanel::isPast);
        if (dates.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Click one or more days on the calendar first.",
                "No day highlighted", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Ask for the time slots of each day separately.
        Map<LocalDate, List<String>> plan = new LinkedHashMap<>();
        List<String> carry = null;   // times to reuse when "same for remaining days" is ticked
        for (int i = 0; i < dates.size(); i++) {
            LocalDate d = dates.get(i);
            List<String> times;
            if (carry != null) {
                times = new ArrayList<>();
                for (String t : carry) {
                    if (!ConsultationSchedule.isPastSlot(d, t)) {
                        times.add(t);
                    }
                }
            } else {
                boolean[] sameForRest = new boolean[1];
                times = askTimeSlots(d, i + 1, dates.size(), sameForRest);
                if (times == null) {
                    return;   // cancelled: nothing is saved
                }
                if (sameForRest[0]) {
                    carry = new ArrayList<>(times);
                }
            }
            if (!times.isEmpty()) {
                plan.put(d, times);
            }
        }
        if (plan.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No time slots were selected.",
                "Nothing to save", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int added = 0;
        int skipped = 0;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (Map.Entry<LocalDate, List<String>> day : plan.entrySet()) {
                    for (String t : day.getValue()) {
                        if (ConsultationSchedule.isPastSlot(day.getKey(), t)) {
                            continue;   // never store a time that has already passed
                        }
                        if (scheduleExists(conn, day.getKey(), t)) {
                            skipped++;
                            continue;
                        }
                        insertSlot(conn, day.getKey(), t);
                        added++;
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            showDbError("Unable to save schedule", e);
            return;
        }

        loadSchedule();
        setMode(Mode.VIEW);
        JOptionPane.showMessageDialog(this,
            added + " time slot(s) added" +
            (skipped > 0 ? " (" + skipped + " already existed)." : "."));
    }

    // Dialog for ONE day. Returns the ticked times (possibly empty), or null if cancelled.
    // Slots that have already passed (today only) are shown but cannot be ticked.
    private List<String> askTimeSlots(LocalDate date, int index, int total, boolean[] sameForRest) {
        JCheckBox all = new JCheckBox("Select all");
        JCheckBox[] boxes = new JCheckBox[ConsultationSchedule.TIME_SLOTS.length];
        JPanel grid = new JPanel(new GridLayout(0, 2, 18, 4));
        int usable = 0;
        for (int i = 0; i < boxes.length; i++) {
            String t = ConsultationSchedule.TIME_SLOTS[i];
            boolean past = ConsultationSchedule.isPastSlot(date, t);
            boxes[i] = new JCheckBox(past ? t + " (passed)" : t);
            boxes[i].setActionCommand(t);
            boxes[i].setEnabled(!past);
            if (!past) {
                usable++;
            }
            grid.add(boxes[i]);
        }
        if (usable == 0) {
            JOptionPane.showMessageDialog(this,
                "Every time slot on " + prettyDate(date) + " has already passed.",
                "No available time", JOptionPane.INFORMATION_MESSAGE);
            return new ArrayList<>();
        }
        all.addActionListener(e -> {
            for (JCheckBox b : boxes) {
                if (b.isEnabled()) {
                    b.setSelected(all.isSelected());
                }
            }
        });

        JCheckBox same = new JCheckBox("Use these times for the remaining " + (total - index) + " day(s)");
        JPanel south = new JPanel(new GridLayout(0, 1));
        south.add(all);
        if (index < total) {
            south.add(same);
        }

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel("<html>Day " + index + " of " + total + ": <b>" + prettyDate(date)
            + "</b><br>Choose the time slots for this day:</html>"), BorderLayout.NORTH);
        panel.add(grid, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, panel,
                "Add Consultation Schedule (" + index + "/" + total + ")",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return null;
            }
            List<String> picked = new ArrayList<>();
            for (JCheckBox b : boxes) {
                if (b.isSelected() && b.isEnabled()) {
                    picked.add(b.getActionCommand());
                }
            }
            if (!picked.isEmpty()) {
                sameForRest[0] = index < total && same.isSelected();
                return picked;
            }
            JOptionPane.showMessageDialog(this, "Tick at least one time slot.",
                "No time selected", JOptionPane.WARNING_MESSAGE);
        }
    }

    // EDIT: change the time slots of one existing day.
    private void editDay(LocalDate date) {
        List<SchedRow> rows = schedule.get(date);
        if (rows == null) {
            return;
        }

        Map<String, SchedRow> byTime = new LinkedHashMap<>();
        for (SchedRow r : rows) {
            byTime.put(r.time, r);
        }
        List<String> all = new ArrayList<>(Arrays.asList(ConsultationSchedule.TIME_SLOTS));
        for (String t : byTime.keySet()) {
            if (!all.contains(t)) {
                all.add(t);
            }
        }

        Map<String, JCheckBox> boxes = new LinkedHashMap<>();
        JPanel grid = new JPanel(new GridLayout(0, 2, 18, 4));
        for (String t : all) {
            SchedRow r = byTime.get(t);
            String text = t;
            if (r != null && !r.open()) {
                text += " (not available)";
            } else if (r != null && r.hasRequest) {
                text += " (consultation history - retained)";
            } else if (r == null && ConsultationSchedule.isPastSlot(date, t)) {
                text += " (passed)";
            }
            JCheckBox box = new JCheckBox(text, r != null);
            box.setEnabled(r == null ? !ConsultationSchedule.isPastSlot(date, t) : !r.locked());
            boxes.put(t, box);
            grid.add(box);
        }

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel("<html><b>" + prettyDate(date) + "</b><br>" +
            "Tick the time slots you are available. Unticking removes an unused slot.<br>" +
            "<font color='#888888'>Slots referenced by consultation records are retained for history.</font></html>"),
            BorderLayout.NORTH);
        panel.add(grid, BorderLayout.CENTER);

        int choice = JOptionPane.showConfirmDialog(this, panel, "Edit Schedule",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        if (CalendarPanel.isPast(date)) {
            JOptionPane.showMessageDialog(this, "That day has already passed.",
                "Invalid Date", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<String> toAdd = new ArrayList<>();
        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> e : boxes.entrySet()) {
            SchedRow r = byTime.get(e.getKey());
            boolean on = e.getValue().isSelected();
            if (r == null && on) {
                toAdd.add(e.getKey());
            } else if (r != null && !r.locked() && !on) {
                toRemove.add(r.id);
            }
        }
        if (toAdd.isEmpty() && toRemove.isEmpty()) {
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (int id : toRemove) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "DELETE FROM tbl_consult_sched " +
                            "WHERE sched_id = ? AND status = 'Available' " +
                            "AND NOT EXISTS (SELECT 1 FROM tbl_consult c " +
                            "WHERE c.sched_id = tbl_consult_sched.sched_id)")) {
                        stmt.setInt(1, id);
                        stmt.executeUpdate();
                    }
                }
                for (String t : toAdd) {
                    if (!scheduleExists(conn, date, t)) {
                        insertSlot(conn, date, t);
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            showDbError("Unable to update schedule", e);
        }
        loadSchedule();
    }

    private boolean scheduleExists(Connection conn, LocalDate date, String time) throws Exception {
        String sql =
            "SELECT 1 FROM tbl_consult_sched " +
            "WHERE inst_id = (SELECT inst_id FROM tbl_instructor WHERE inst_email = ?) " +
            "AND consult_date = ? AND available_time = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, instructorEmail);
            stmt.setString(2, date.toString());
            stmt.setString(3, time);
            return stmt.executeQuery().next();
        }
    }

    private void insertSlot(Connection conn, LocalDate date, String time) throws Exception {
        String sql =
            "INSERT INTO tbl_consult_sched " +
            "(inst_id, consult_date, day, available_time, status) " +
            "VALUES ((SELECT inst_id FROM tbl_instructor WHERE inst_email = ?), " +
            "?, ?, ?, 'Available')";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, instructorEmail);
            stmt.setString(2, date.toString());
            stmt.setString(3, date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
            stmt.setString(4, time);
            stmt.executeUpdate();
        }
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