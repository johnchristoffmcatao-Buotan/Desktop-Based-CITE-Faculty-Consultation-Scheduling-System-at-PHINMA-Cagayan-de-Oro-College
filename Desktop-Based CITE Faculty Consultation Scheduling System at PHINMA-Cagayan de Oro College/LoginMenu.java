import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

//The JFrame itself
public class LoginMenu extends JFrame {

    private Ui.PillTextField username;
    private Ui.PillPasswordField password;

    public LoginMenu() {
        Ui.install();
        setTitle("PHINMA COC - Grade Consultation");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        if (Ui.logoImage() != null) {
            setIconImage(Ui.logoImage());
        }
        buildUi();
        setSize(1100, 640);
        setMinimumSize(new Dimension(920, 600));
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(buildBrandPanel(), BorderLayout.WEST);
        getContentPane().add(buildFormPanel(), BorderLayout.CENTER);
    }

    // Green Panel: Logo, title, etc.
    private JPanel buildBrandPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(Ui.GREEN);
        panel.setPreferredSize(new Dimension(420, 0));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(30, 10, 30, 10));

        panel.add(Box.createVerticalGlue());
        panel.add(center(Ui.logoLabel(200)));
        panel.add(Box.createVerticalStrut(18));
        panel.add(center(Ui.label("PHINMA COC", Font.PLAIN, 40, Color.WHITE)));
        panel.add(Box.createVerticalStrut(4));
        panel.add(center(Ui.label("MAKING LIVES THROUGH EDUCATION", Font.PLAIN, 14, Color.WHITE)));
        panel.add(Box.createVerticalStrut(56));
        for (String line : new String[] {
                "Consult with your grade", "Track your progress", "Book consultation easily"}) {
            panel.add(center(Ui.label(line, Font.PLAIN, 28, Color.WHITE)));
            panel.add(Box.createVerticalStrut(6));
        }
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private static <T extends JComponent> T center(T c) {
        c.setAlignmentX(Component.CENTER_ALIGNMENT);
        return c;
    }

    // White Panel: Login form, forgot password, etc.
    private JPanel buildFormPanel() {
        Ui.Shell shell = new Ui.Shell();
        shell.setLayout(new GridBagLayout());

        username = new Ui.PillTextField();
        password = new Ui.PillPasswordField();
        for (JComponent f : new JComponent[] {username, password}) {
            f.setPreferredSize(new Dimension(100, 50));
            f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            f.setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        JButton login = Ui.button("LOGIN");
        login.setPreferredSize(new Dimension(140, 48));
        login.addActionListener(e -> signIn());
        JLabel forgot = Ui.link("Forgot Password", this::forgotPassword);

        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        actions.setBorder(new EmptyBorder(0, 14, 0, 0));
        actions.add(forgot, BorderLayout.WEST);
        actions.add(login, BorderLayout.EAST);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(Ui.left(Ui.label("Welcome Back!", Font.PLAIN, 40, Ui.TEXT)));
        form.add(Ui.left(Ui.label("Sign in to consult grades", Font.PLAIN, 17, Ui.MUTED)));
        form.add(Box.createVerticalStrut(60));
        form.add(Ui.left(Ui.label("USERNAME:", Font.PLAIN, 17, Ui.TEXT)));
        form.add(Box.createVerticalStrut(8));
        form.add(username);
        form.add(Box.createVerticalStrut(34));
        form.add(Ui.left(Ui.label("PASSWORD:", Font.PLAIN, 17, Ui.TEXT)));
        form.add(Box.createVerticalStrut(8));
        form.add(password);
        form.add(Box.createVerticalStrut(30));
        form.add(actions);

        GridBagConstraints gc = new GridBagConstraints();
        gc.weightx = 1;
        gc.weighty = 1;
        gc.anchor = GridBagConstraints.CENTER;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(20, 90, 20, 70);
        shell.add(form, gc);

        getRootPane().setDefaultButton(login);
        return shell;
    }

    // DATABASE: checks the user first if it's a student then checks if it's an instructor. If both fail, it shows an error message.
    private void signIn() {
        String userEmail = username.getText().trim();
        String userPassword = new String(password.getPassword());

        if (userEmail.isEmpty() || userPassword.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter your username and password.",
                "Required Fields", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String studentSQL =
            "SELECT stud_name, stud_email " +
            "FROM tbl_student " +
            "WHERE stud_email = ? AND password = ?";

        String instructorSQL =
            "SELECT inst_name, inst_email " +
            "FROM tbl_instructor " +
            "WHERE inst_email = ? AND inst_pass = ?";

                String name = null;
        String dbEmail = null;
        boolean isStudent = false;

        try {
            // Check student (connection closes before any window opens)
            try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(studentSQL)) {

                stmt.setString(1, userEmail);
                stmt.setString(2, userPassword);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        name = rs.getString("stud_name");
                        dbEmail = rs.getString("stud_email");
                        isStudent = true;
                    }
                }
            }

            // Check instructor only if not a student
            if (!isStudent) {
                try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(instructorSQL)) {

                    stmt.setString(1, userEmail);
                    stmt.setString(2, userPassword);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            name = rs.getString("inst_name");
                            dbEmail = rs.getString("inst_email");
                        }
                    }
                }
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                "Database connection failed:\n" + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            return;
        }

        // Open the window AFTER the connections are closed
        if (name == null) {
            JOptionPane.showMessageDialog(this,
                "Invalid username or password.",
                "Login Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (isStudent) {
            new GradeConsultStudent(name, dbEmail).setVisible(true);
        } else {
            new GradeConsultInstructor(name, dbEmail).setVisible(true);
        }
        dispose();
    }

    // Forgot password area
    private void forgotPassword() {
        Ui.PillTextField emailField = new Ui.PillTextField();
        Ui.PillTextField nameField = new Ui.PillTextField();
        Ui.PillPasswordField newPass = new Ui.PillPasswordField();
        Ui.PillPasswordField confirmPass = new Ui.PillPasswordField();
        for (JComponent f : new JComponent[] {emailField, nameField, newPass, confirmPass}) {
            f.setPreferredSize(new Dimension(340, 42));
        }
        emailField.setText(username.getText().trim());

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 6));
        panel.add(new JLabel("Username (email):"));
        panel.add(emailField);
        panel.add(new JLabel("Full name on your account:"));
        panel.add(nameField);
        panel.add(new JLabel("New password:"));
        panel.add(newPass);
        panel.add(new JLabel("Confirm new password:"));
        panel.add(confirmPass);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, panel, "Reset Password",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }

            String email = emailField.getText().trim();
            String fullName = nameField.getText().trim();
            String pass1 = new String(newPass.getPassword());
            String pass2 = new String(confirmPass.getPassword());

            if (email.isEmpty() || fullName.isEmpty() || pass1.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill in all fields.",
                    "Required Fields", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (!pass1.equals(pass2)) {
                JOptionPane.showMessageDialog(this, "The passwords do not match.",
                    "Reset Password", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                int changed = updatePassword(conn,
                    "UPDATE tbl_student SET password = ? WHERE stud_email = ? AND stud_name = ?",
                    pass1, email, fullName);
                if (changed == 0) {
                    changed = updatePassword(conn,
                        "UPDATE tbl_instructor SET inst_pass = ? WHERE inst_email = ? AND inst_name = ?",
                        pass1, email, fullName);
                }
                if (changed == 0) {
                    JOptionPane.showMessageDialog(this,
                        "No account matches that username and name.",
                        "Reset Failed", JOptionPane.ERROR_MESSAGE);
                    continue;
                }
                username.setText(email);
                password.setText("");
                JOptionPane.showMessageDialog(this,
                    "Your password has been changed. You can now log in.",
                    "Password Reset", JOptionPane.INFORMATION_MESSAGE);
                return;
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this,
                    "Database connection failed:\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
                return;
            }
        }
    }

    private static int updatePassword(Connection conn, String sql, String newPassword,
                                    String email, String fullName) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newPassword);
            stmt.setString(2, email);
            stmt.setString(3, fullName);
            return stmt.executeUpdate();
        }
    }
    //----------------------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Ui.install();
            new LoginMenu().setVisible(true);
        });
    }
}
