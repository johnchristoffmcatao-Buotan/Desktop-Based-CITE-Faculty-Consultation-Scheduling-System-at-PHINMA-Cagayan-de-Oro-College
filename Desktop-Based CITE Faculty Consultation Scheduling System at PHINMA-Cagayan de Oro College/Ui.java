import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.JTextComponent;

/**
 * Shared look-and-feel for the PHINMA COC Grade Consultation screens:
 * colors, fonts, vector icons, sidebar, rounded fields, buttons and tables.
 */
public final class Ui {

    private Ui() {
    }

    // ------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------
    public static final Color GREEN       = new Color(70, 151, 43);
    public static final Color GREEN_DARK  = new Color(52, 120, 32);
    public static final Color GREEN_LIGHT = new Color(214, 237, 204);
    public static final Color AMBER       = new Color(255, 232, 178);
    public static final Color FIELD       = new Color(239, 239, 239);
    public static final Color BORDER      = new Color(202, 209, 216);
    public static final Color MUTED       = new Color(150, 150, 150);
    public static final Color TEXT        = new Color(17, 17, 17);
    public static final Color LINK        = new Color(66, 165, 245);

    private static String family;
    private static boolean installed;
    private static Image logoImage;
    private static boolean logoLoaded;

    /** Call once before creating any window. */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        Font body = font(Font.PLAIN, 14);
        for (String key : new String[] {"Label.font", "TextField.font", "ComboBox.font",
                "CheckBox.font", "List.font", "OptionPane.messageFont", "ToolTip.font",
                "RadioButton.font", "Table.font"}) {
            UIManager.put(key, body);
        }
        UIManager.put("Button.font", font(Font.BOLD, 13));
        UIManager.put("OptionPane.buttonFont", font(Font.BOLD, 13));
        UIManager.put("Button.disabledText", new Color(240, 245, 238));
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));
        UIManager.put("CheckBox.focus", new Color(0, 0, 0, 0));
        UIManager.put("ComboBox.selectionBackground", GREEN_LIGHT);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("List.selectionBackground", GREEN_LIGHT);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("OptionPane.background", Color.WHITE);
        UIManager.put("Panel.background", Color.WHITE);
        UIManager.put("CheckBox.background", Color.WHITE);
        UIManager.put("Table.selectionBackground", GREEN_LIGHT);
        UIManager.put("Table.selectionForeground", TEXT);
    }

    public static Font font(int style, float size) {
        if (family == null) {
            family = "SansSerif";
            Set<String> names = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String candidate : new String[] {"Roboto", "Segoe UI", "Helvetica Neue", "Arial"}) {
                if (names.contains(candidate)) {
                    family = candidate;
                    break;
                }
            }
        }
        return new Font(family, style, Math.round(size));
    }

    public static JLabel label(String text, int style, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    // ------------------------------------------------------------------
    // Logo
    // ------------------------------------------------------------------
    public static Image logoImage() {
        if (!logoLoaded) {
            logoLoaded = true;
            try (InputStream in = Ui.class.getResourceAsStream("/logo.png")) {
                if (in != null) {
                    logoImage = ImageIO.read(in);
                }
            } catch (Exception ignored) {
            }
            if (logoImage == null) {
                for (String path : new String[] {"logo.png", "src/logo.png"}) {
                    try {
                        File f = new File(path);
                        if (f.isFile()) {
                            logoImage = ImageIO.read(f);
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return logoImage;
    }

    public static JLabel logoLabel(int height) {
        JLabel l = new JLabel();
        Image img = logoImage();
        if (img != null) {
            int w = Math.max(1, img.getWidth(null) * height / img.getHeight(null));
            l.setIcon(new ImageIcon(img.getScaledInstance(w, height, Image.SCALE_SMOOTH)));
        } else {
            l.setPreferredSize(new Dimension(height, height));
        }
        return l;
    }

    // ------------------------------------------------------------------
    // Vector icons
    // ------------------------------------------------------------------
    public enum Glyph {
        DASHBOARD, CALENDAR, HISTORY, CHECK, LOGOUT, USER, PENDING, SUBJECTS,
        CHEVRON_LEFT, CHEVRON_RIGHT, CHEVRON_DOWN
    }

    private static Path2D path(float s, float... p) {
        Path2D.Float path = new Path2D.Float();
        for (int i = 0; i < p.length; i += 2) {
            if (i == 0) {
                path.moveTo(p[i] * s, p[i + 1] * s);
            } else {
                path.lineTo(p[i] * s, p[i + 1] * s);
            }
        }
        return path;
    }

    public static Icon icon(Glyph glyph, int size, Color color) {
        return new Icon() {
            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                g2.setColor(color);
                float s = size;
                g2.setStroke(new BasicStroke(Math.max(1.6f, s / 15f),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                switch (glyph) {
                    case DASHBOARD: {
                        float cell = s * 0.38f, gap = s * 0.16f, off = (s - 2 * cell - gap) / 2f;
                        for (int i = 0; i < 2; i++) {
                            for (int j = 0; j < 2; j++) {
                                g2.draw(new RoundRectangle2D.Float(off + i * (cell + gap),
                                    off + j * (cell + gap), cell, cell, s * 0.12f, s * 0.12f));
                            }
                        }
                        break;
                    }
                    case CALENDAR: {
                        g2.draw(new RoundRectangle2D.Float(s * .08f, s * .16f, s * .84f, s * .76f, s * .16f, s * .16f));
                        g2.draw(new Line2D.Float(s * .08f, s * .36f, s * .92f, s * .36f));
                        g2.draw(new Line2D.Float(s * .30f, s * .06f, s * .30f, s * .24f));
                        g2.draw(new Line2D.Float(s * .70f, s * .06f, s * .70f, s * .24f));
                        float d = s * .075f;
                        for (int r = 0; r < 3; r++) {
                            for (int col = 0; col < 4; col++) {
                                g2.fill(new Rectangle2D.Float(s * (.2f + col * .17f), s * (.47f + r * .14f), d, d));
                            }
                        }
                        break;
                    }
                    case HISTORY: {
                        float cx = s * .55f, cy = s * .5f, r = s * .36f;
                        g2.draw(new Arc2D.Float(cx - r, cy - r, 2 * r, 2 * r, -110, 300, Arc2D.OPEN));
                        double th = Math.toRadians(190);
                        double ex = cx + r * Math.cos(th), ey = cy - r * Math.sin(th);
                        double dx = -Math.sin(th), dy = -Math.cos(th);
                        double arm = s * .2;
                        for (double ang : new double[] {Math.toRadians(150), Math.toRadians(-150)}) {
                            double rx = dx * Math.cos(ang) - dy * Math.sin(ang);
                            double ry = dx * Math.sin(ang) + dy * Math.cos(ang);
                            g2.draw(new Line2D.Double(ex, ey, ex + rx * arm, ey + ry * arm));
                        }
                        g2.draw(path(1f, cx, cy - .5f * r, cx, cy + .1f * r, cx + .5f * r, cy + .1f * r));
                        break;
                    }
                    case CHECK:
                        g2.draw(new Ellipse2D.Float(s * .06f, s * .06f, s * .88f, s * .88f));
                        g2.draw(path(s, .28f, .52f, .44f, .68f, .72f, .36f));
                        break;
                    case LOGOUT:
                        g2.draw(path(s, .45f, .12f, .12f, .12f, .12f, .88f, .45f, .88f));
                        g2.draw(new Line2D.Float(s * .34f, s * .5f, s * .9f, s * .5f));
                        g2.draw(path(s, .7f, .28f, .9f, .5f, .7f, .72f));
                        break;
                    case USER:
                        g2.draw(new Ellipse2D.Float(s * .33f, s * .1f, s * .34f, s * .34f));
                        g2.draw(new Arc2D.Float(s * .16f, s * .56f, s * .68f, s * .7f, 0, 180, Arc2D.OPEN));
                        break;
                    case PENDING: {
                        g2.setStroke(new BasicStroke(Math.max(1.6f, s / 15f), BasicStroke.CAP_BUTT,
                            BasicStroke.JOIN_ROUND, 10f, new float[] {s * .11f, s * .09f}, 0f));
                        g2.draw(new Ellipse2D.Float(s * .08f, s * .08f, s * .84f, s * .84f));
                        g2.setStroke(new BasicStroke(Math.max(1.6f, s / 15f),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.draw(path(s, .5f, .28f, .5f, .52f, .68f, .62f));
                        break;
                    }
                    case SUBJECTS:
                        g2.draw(new RoundRectangle2D.Float(s * .12f, s * .06f, s * .62f, s * .88f, s * .1f, s * .1f));
                        g2.draw(new Ellipse2D.Float(s * .33f, s * .17f, s * .2f, s * .2f));
                        g2.draw(new Line2D.Float(s * .24f, s * .55f, s * .6f, s * .55f));
                        g2.draw(new Line2D.Float(s * .24f, s * .70f, s * .46f, s * .70f));
                        g2.setStroke(new BasicStroke(s * .13f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                        g2.draw(new Line2D.Float(s * .66f, s * .86f, s * .9f, s * .54f));
                        break;
                    case CHEVRON_LEFT:
                        g2.draw(path(s, .62f, .25f, .38f, .5f, .62f, .75f));
                        break;
                    case CHEVRON_RIGHT:
                        g2.draw(path(s, .38f, .25f, .62f, .5f, .38f, .75f));
                        break;
                    case CHEVRON_DOWN:
                        g2.draw(path(s, .25f, .38f, .5f, .64f, .75f, .38f));
                        break;
                    default:
                        break;
                }
                g2.dispose();
            }
        };
    }

    // ------------------------------------------------------------------
    // Buttons and links
    // ------------------------------------------------------------------
    public static JButton button(String text) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Color c = !isEnabled() ? new Color(176, 196, 170)
                    : getModel().isPressed() ? GREEN_DARK.darker()
                    : getModel().isRollover() ? GREEN_DARK : GREEN;
                g2.setColor(c);
                g2.fillRect(0, 0, getWidth(), getHeight());
                if (isEnabled()) {
                    g2.setColor(new Color(124, 196, 84));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setRolloverEnabled(true);
        b.setForeground(Color.WHITE);
        b.setFont(font(Font.BOLD, 16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMargin(new Insets(8, 24, 8, 24));
        return b;
    }

    /** Small flat icon-only button (calendar month arrows). */
    public static JButton iconButton(Glyph glyph, int size) {
        JButton b = new JButton(icon(glyph, size, TEXT)) {
            @Override
            protected void paintComponent(Graphics g) {
                if (getModel().isRollover()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(FIELD);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setRolloverEnabled(true);
        b.setMargin(new Insets(4, 6, 4, 6));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static JLabel link(String text, Runnable action) {
        JLabel l = new JLabel("<html><u>" + text + "</u></html>");
        l.setFont(font(Font.PLAIN, 16));
        l.setForeground(LINK);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
        });
        return l;
    }

    // ------------------------------------------------------------------
    // Rounded fields
    // ------------------------------------------------------------------
    private static void paintPill(Graphics g, JComponent c) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = c.getWidth() - 2, h = c.getHeight() - 2;
        g2.setColor(FIELD);
        g2.fillRoundRect(1, 1, w, h, h, h);
        g2.setColor(c.hasFocus() ? GREEN : BORDER);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(1, 1, w, h, h, h);
        g2.dispose();
    }

    private static void stylePillText(JTextComponent c) {
        c.setOpaque(false);
        c.setFont(font(Font.PLAIN, 16));
        c.setForeground(TEXT);
        c.setCaretColor(TEXT);
        c.setBorder(new EmptyBorder(0, 22, 0, 22));
        c.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                c.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                c.repaint();
            }
        });
    }

    public static final class PillTextField extends JTextField {
        public PillTextField() {
            stylePillText(this);
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintPill(g, this);
            super.paintComponent(g);
        }
    }

    public static final class PillPasswordField extends JPasswordField {
        public PillPasswordField() {
            stylePillText(this);
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintPill(g, this);
            super.paintComponent(g);
        }
    }

    public static final class PillCombo<E> extends JComboBox<E> {
        public PillCombo() {
            setOpaque(false);
            setFont(font(Font.PLAIN, 16));
            setForeground(TEXT);
            setUI(new BasicComboBoxUI() {
                @Override
                protected JButton createArrowButton() {
                    JButton b = new JButton(icon(Glyph.CHEVRON_DOWN, 22, TEXT));
                    b.setBorder(new EmptyBorder(0, 0, 0, 12));
                    b.setContentAreaFilled(false);
                    b.setFocusPainted(false);
                    b.setOpaque(false);
                    b.setPreferredSize(new Dimension(44, 10));
                    return b;
                }

                @Override
                public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                    // transparent: the pill is painted by the combo itself
                }
            });
            setBorder(new EmptyBorder(0, 16, 0, 0));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                            boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    setFont(font(Font.PLAIN, 16));
                    setBorder(new EmptyBorder(6, 8, 6, 8));
                    if (index == -1) {
                        setOpaque(false);
                    }
                    return this;
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintPill(g, this);
            super.paintComponent(g);
        }
    }

    // ------------------------------------------------------------------
    // Layout building blocks
    // ------------------------------------------------------------------

    /** Green backdrop with a white panel that has rounded left corners. */
    public static final class Shell extends JPanel {
        private static final int RADIUS = 56;

        public Shell() {
            setLayout(new BorderLayout());
            setBackground(GREEN);
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() + RADIUS, getHeight(), RADIUS * 2, RADIUS * 2));
            g2.dispose();
        }
    }

    public static final class NavItem extends JComponent {
        private final Glyph glyph;
        private final String[] lines;
        private boolean active;
        private boolean hover;

        NavItem(Glyph glyph, String text, Runnable action) {
            this.glyph = glyph;
            this.lines = text.split("\n");
            setPreferredSize(new Dimension(100, 78));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    action.run();
                }
            });
        }

        void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (hover && !active) {
                g2.setColor(new Color(255, 255, 255, 30));
                g2.fillRoundRect(0, 0, w, h - 4, 16, 16);
            }
            Icon ic = icon(glyph, 38, Color.WHITE);
            ic.paintIcon(this, g2, 18, (h - 38) / 2);
            g2.setColor(Color.WHITE);
            g2.setFont(font(Font.PLAIN, 20));
            FontMetrics fm = g2.getFontMetrics();
            int lineHeight = fm.getHeight();
            int y = (h - lineHeight * lines.length) / 2 + fm.getAscent();
            for (String line : lines) {
                g2.drawString(line, 74, y);
                y += lineHeight;
            }
            if (active) {
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(8, h - 3, w - 8, h - 3);
            }
            g2.dispose();
        }
    }

    public static final class Sidebar extends JPanel {
        private final List<NavItem> items = new ArrayList<>();
        private final JPanel navBox = new JPanel();
        private final JPanel bottom = new JPanel(new BorderLayout());

        public Sidebar() {
            setBackground(GREEN);
            setPreferredSize(new Dimension(350, 0));
            setLayout(new BorderLayout());

            JPanel header = new JPanel(new BorderLayout(12, 0));
            header.setOpaque(false);
            header.setBorder(new EmptyBorder(26, 18, 10, 12));
            header.add(logoLabel(80), BorderLayout.WEST);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(Box.createVerticalGlue());
            text.add(label("PHINMA COC", Font.PLAIN, 32, Color.WHITE));
            text.add(label("MAKING LIVES THROUGH EDUCATION", Font.PLAIN, 11, Color.WHITE));
            text.add(Box.createVerticalGlue());
            header.add(text, BorderLayout.CENTER);
            add(header, BorderLayout.NORTH);

            navBox.setOpaque(false);
            navBox.setLayout(new BoxLayout(navBox, BoxLayout.Y_AXIS));
            navBox.setBorder(new EmptyBorder(40, 10, 0, 10));
            add(navBox, BorderLayout.CENTER);

            bottom.setOpaque(false);
            bottom.setBorder(new EmptyBorder(0, 10, 22, 10));
            add(bottom, BorderLayout.SOUTH);
        }

        public NavItem addNav(Glyph glyph, String text, Runnable action) {
            NavItem item = new NavItem(glyph, text, action);
            items.add(item);
            navBox.add(item);
            navBox.add(Box.createVerticalStrut(6));
            return item;
        }

        public void addLogout(String text, Runnable action) {
            NavItem item = new NavItem(Glyph.LOGOUT, text, action);
            item.setPreferredSize(new Dimension(100, 64));
            item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
            bottom.add(item, BorderLayout.CENTER);
        }

        public void select(NavItem selected) {
            for (NavItem item : items) {
                item.setActive(item == selected);
            }
        }
    }

    public static JPanel titleRow(String text, Glyph glyph) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.add(label(text, Font.PLAIN, 32, TEXT));
        if (glyph != null) {
            JLabel icon = new JLabel(icon(glyph, 40, TEXT));
            icon.setBorder(new EmptyBorder(0, 14, 0, 0));
            row.add(icon);
        }
        return row;
    }

    public static JPanel statCard(JLabel number, String caption) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(FIELD);
        card.setBorder(javax.swing.BorderFactory.createCompoundBorder(
            new LineBorder(BORDER, 2), new EmptyBorder(18, 10, 14, 10)));
        number.setFont(font(Font.PLAIN, 40));
        number.setForeground(TEXT);
        number.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel cap = label(caption, Font.PLAIN, 16, TEXT);
        cap.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(number, BorderLayout.CENTER);
        card.add(cap, BorderLayout.SOUTH);
        return card;
    }

    public static JPanel statRow(JPanel a, JPanel b, JPanel c) {
        JPanel row = new JPanel(new GridLayout(1, 3, 56, 0));
        row.setOpaque(false);
        row.add(a);
        row.add(b);
        row.add(c);
        row.setPreferredSize(new Dimension(100, 128));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 128));
        return row;
    }

    public static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    public static JPanel pagePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(34, 56, 30, 40));
        return p;
    }

    // ------------------------------------------------------------------
    // Tables
    // ------------------------------------------------------------------
    public static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(new Object[][] {}, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /**
     * Styles a table like the mockups. {@code widths} gives a preferred width per
     * model column; when {@code hideFirst} is true the first column (a hidden id) is removed
     * from the view but kept in the model.
     */
    public static JScrollPane table(JTable t, DefaultTableModel model, boolean hideFirst, int... widths) {
        t.setModel(model);
        t.setRowHeight(40);
        t.setFont(font(Font.PLAIN, 15));
        t.setForeground(TEXT);
        t.setBackground(FIELD);
        t.setGridColor(BORDER);
        t.setShowGrid(false);
        t.setShowHorizontalLines(true);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setFillsViewportHeight(true);
        t.setSelectionBackground(GREEN_LIGHT);
        t.setSelectionForeground(TEXT);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        for (int i = 0; i < widths.length && i < t.getColumnModel().getColumnCount(); i++) {
            t.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        if (hideFirst) {
            t.removeColumn(t.getColumnModel().getColumn(0));
        }

        DefaultTableCellRenderer body = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean sel,
                                                           boolean focus, int row, int col) {
                super.getTableCellRendererComponent(table, value, sel, false, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                setBorder(new EmptyBorder(0, 8, 0, 8));
                return this;
            }
        };
        t.setDefaultRenderer(Object.class, body);

        DefaultTableCellRenderer head = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean sel,
                                                           boolean focus, int row, int col) {
                super.getTableCellRendererComponent(table, value, false, false, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                setOpaque(true);
                setBackground(FIELD);
                setForeground(TEXT);
                setFont(font(Font.PLAIN, 15));
                setBorder(new LineBorder(BORDER, 1));
                return this;
            }
        };
        t.getTableHeader().setDefaultRenderer(head);
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setPreferredSize(new Dimension(100, 42));

        JScrollPane sp = new JScrollPane(t);
        sp.getViewport().setBackground(FIELD);
        sp.setBorder(new LineBorder(BORDER, 2));
        styleScroll(sp);
        return sp;
    }

    /** Renders one visible column as a green, bold "action" label. */
    public static void actionColumn(JTable t, int viewColumn) {
        t.getColumnModel().getColumn(viewColumn).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean sel,
                                                        boolean focus, int row, int col) {
                super.getTableCellRendererComponent(table, value, sel, false, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                setForeground(GREEN_DARK);
                setFont(font(Font.BOLD, 14));
                setBorder(new EmptyBorder(0, 8, 0, 8));
                return this;
            }
        });
    }

    public static void styleScroll(JScrollPane sp) {
        sp.getVerticalScrollBar().setUI(new FlatScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        sp.getHorizontalScrollBar().setUI(new FlatScrollBarUI());
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 10));
    }

    private static final class FlatScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(188, 196, 204);
            trackColor = FIELD;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 8, 8);
            g2.dispose();
        }
    }
}
