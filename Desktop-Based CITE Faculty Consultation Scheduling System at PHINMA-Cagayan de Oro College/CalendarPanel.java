import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.*;
import javax.swing.border.MatteBorder;

//Calendar Panel
public class CalendarPanel extends JPanel {

    private static final DayOfWeek[] COLUMNS = {
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
    };

    private YearMonth month = YearMonth.now();
    private final JLabel monthLabel = new JLabel();
    private final JPanel grid = new JPanel();
    private final Map<LocalDate, String> notes = new HashMap<>();
    private final Map<LocalDate, Color> tints = new HashMap<>();
    private final Set<LocalDate> selected = new TreeSet<>();

    private Predicate<LocalDate> clickable = d -> false;
    private Consumer<LocalDate> clickHandler = d -> { };
    private Runnable selectionListener = () -> { };

    private LocalDate lastToday = LocalDate.now();
    private final Timer dayWatcher;

    public CalendarPanel() {
        setOpaque(false);
        setLayout(new BorderLayout(0, 10));

        monthLabel.setFont(Ui.font(Font.PLAIN, 24));
        monthLabel.setForeground(Ui.TEXT);

        JButton prev = Ui.iconButton(Ui.Glyph.CHEVRON_LEFT, 26);
        prev.setToolTipText("Previous month");
        prev.addActionListener(e -> setMonth(month.minusMonths(1)));
        JButton next = Ui.iconButton(Ui.Glyph.CHEVRON_RIGHT, 26);
        next.setToolTipText("Next month");
        next.addActionListener(e -> setMonth(month.plusMonths(1)));
        JLabel today = Ui.link("Today", () -> setMonth(YearMonth.now()));
        today.setFont(Ui.font(Font.PLAIN, 15));

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        nav.setOpaque(false);
        nav.add(today);
        nav.add(prev);
        nav.add(next);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(monthLabel, BorderLayout.WEST);
        top.add(nav, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JPanel head = new JPanel(new GridLayout(1, COLUMNS.length, 0, 0));
        head.setOpaque(false);
        for (DayOfWeek d : COLUMNS) {
            JLabel l = new JLabel(d.getDisplayName(TextStyle.FULL, Locale.ENGLISH), SwingConstants.CENTER);
            l.setFont(Ui.font(Font.PLAIN, 15));
            l.setForeground(Ui.TEXT);
            l.setOpaque(true);
            l.setBackground(Ui.FIELD);
            l.setBorder(new MatteBorder(1, 1, 0, 0, Ui.BORDER));
            l.setPreferredSize(new Dimension(10, 40));
            head.add(l);
        }

        grid.setOpaque(false);
        JPanel box = new JPanel(new BorderLayout());
        box.setOpaque(false);
        box.setBorder(new MatteBorder(0, 0, 1, 1, Ui.BORDER));
        box.add(head, BorderLayout.NORTH);
        box.add(grid, BorderLayout.CENTER);
        add(box, BorderLayout.CENTER);

        dayWatcher = new Timer(15000, e -> {
            LocalDate now = LocalDate.now();
            if (!now.equals(lastToday)) {
                lastToday = now;
                if (selected.removeIf(d -> d.isBefore(now))) {
                    selectionListener.run();
                }
                repaint();
            }
        });

        refresh();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        dayWatcher.start();
    }

    @Override
    public void removeNotify() {
        dayWatcher.stop();
        super.removeNotify();
    }

    // Public API
    public static boolean isPast(LocalDate d) {
        return d.isBefore(LocalDate.now());
    }

    public static LocalDate parseDate(String s) {
        if (s == null || s.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(s.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    public YearMonth getMonth() {
        return month;
    }

    public void setMonth(YearMonth m) {
        month = m;
        refresh();
    }

    /** Small caption under the day number, e.g. "2 open". Use \n for several lines. */
    public void setNotes(Map<LocalDate, String> newNotes) {
        notes.clear();
        notes.putAll(newNotes);
        grid.repaint();
    }

    public void setTints(Map<LocalDate, Color> newTints) {
        tints.clear();
        tints.putAll(newTints);
        grid.repaint();
    }

    /** Which days react to clicks. Past days are always excluded on top of this. */
    public void setClickable(Predicate<LocalDate> p) {
        clickable = p;
    }

    public void setClickHandler(Consumer<LocalDate> handler) {
        clickHandler = handler;
    }

    public void setSelectionListener(Runnable r) {
        selectionListener = r;
    }

    /** Highlights / un-highlights a day. Past days are ignored. */
    public void toggle(LocalDate d) {
        if (isPast(d) || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return;
        }
        if (!selected.remove(d)) {
            selected.add(d);
        }
        grid.repaint();
        selectionListener.run();
    }

    public List<LocalDate> getSelected() {
        return new ArrayList<>(selected);
    }

    public void clearSelection() {
        if (!selected.isEmpty()) {
            selected.clear();
            grid.repaint();
            selectionListener.run();
        }
    }
//------------------------------------------------------------------
// Rendering
    private void refresh() {
        grid.removeAll();

        List<LocalDate> days = new ArrayList<>();
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate date = month.atDay(d);
            if (date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(date);
            }
        }
        List<LocalDate> cells = new ArrayList<>();
        int lead = days.get(0).getDayOfWeek().getValue() - 1;
        for (int i = 0; i < lead; i++) {
            cells.add(null);
        }
        cells.addAll(days);
        while (cells.size() % COLUMNS.length != 0) {
            cells.add(null);
        }

        grid.setLayout(new GridLayout(cells.size() / COLUMNS.length, COLUMNS.length, 0, 0));
        for (LocalDate d : cells) {
            grid.add(new DayCell(d));
        }
        monthLabel.setText(month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            + " " + month.getYear());
        revalidate();
        repaint();
    }

    private static Color mix(Color a, Color b, float t) {
        return new Color(
            Math.round(a.getRed() * (1 - t) + b.getRed() * t),
            Math.round(a.getGreen() * (1 - t) + b.getGreen() * t),
            Math.round(a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    private final class DayCell extends JComponent {
        private final LocalDate date;
        private boolean hover;

        DayCell(LocalDate date) {
            this.date = date;
            if (date != null) {
                ToolTipManager.sharedInstance().registerComponent(this);
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = true;
                        setCursor(Cursor.getPredefinedCursor(canClick() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = false;
                        repaint();
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (contains(e.getPoint()) && canClick()) {
                            clickHandler.accept(date);
                        }
                    }
                });
            }
        }

        private boolean canClick() {
            return date != null && !isPast(date) && clickable.test(date);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            return date != null && isPast(date) ? "This day has already passed" : null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            g2.setColor(Ui.FIELD);
            g2.fillRect(0, 0, w, h);

            if (date != null) {
                boolean past = isPast(date);
                boolean sel = selected.contains(date);
                Color tint = tints.get(date);

                Color fill = Ui.FIELD;
                if (sel) {
                    fill = Ui.GREEN;
                } else if (tint != null) {
                    fill = past ? mix(tint, new Color(226, 226, 226), 0.6f) : tint;
                } else if (past) {
                    fill = new Color(228, 228, 228);
                }
                g2.setColor(fill);
                g2.fillRect(0, 0, w, h);

                if (past) {
                    Shape oldClip = g2.getClip();
                    g2.clipRect(0, 0, w, h);
                    g2.setColor(new Color(0, 0, 0, 14));
                    for (int i = -h; i < w; i += 9) {
                        g2.drawLine(i, h, i + h, 0);
                    }
                    g2.setClip(oldClip);
                }
                if (hover && !sel && canClick()) {
                    g2.setColor(new Color(70, 151, 43, 46));
                    g2.fillRect(0, 0, w, h);
                }

                // day number (today gets a ring)
                Color numberColor = sel ? Color.WHITE : past ? new Color(170, 170, 170) : Ui.TEXT;
                g2.setFont(Ui.font(Font.PLAIN, 16));
                FontMetrics fm = g2.getFontMetrics();
                String num = String.valueOf(date.getDayOfMonth());
                int nx = 12, ny = 10 + fm.getAscent();
                if (date.equals(LocalDate.now())) {
                    g2.setColor(sel ? Color.WHITE : Ui.GREEN);
                    g2.setStroke(new BasicStroke(2f));
                    int d = fm.getHeight() + 6;
                    g2.draw(new Ellipse2D.Float(nx - (d - fm.stringWidth(num)) / 2f, 7, d, d));
                }
                g2.setColor(numberColor);
                g2.drawString(num, nx, ny);

                // note lines, anchored to the bottom
                String note = notes.get(date);
                if (note != null && !note.isEmpty()) {
                    g2.setFont(Ui.font(Font.PLAIN, 12));
                    g2.setColor(sel ? Color.WHITE : past ? new Color(150, 150, 150) : Ui.GREEN_DARK);
                    FontMetrics nfm = g2.getFontMetrics();
                    String[] lines = note.split("\n");
                    int y = h - 9 - (lines.length - 1) * nfm.getHeight();
                    for (String line : lines) {
                        g2.drawString(line, 12, y);
                        y += nfm.getHeight();
                    }
                }
            }

            // grid lines (left + top; the outer right/bottom come from the container border)
            g2.setColor(Ui.BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(0, 0, 0, h - 1);
            g2.drawLine(0, 0, w - 1, 0);
            g2.dispose();
        }
//----------------------------------------------------------------
    }
}
