package client;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

final class FantasyTable extends JTable {
    static final Color WIN = new Color(122, 230, 154), LOSE = new Color(255, 140, 161), DRAW = new Color(171, 222, 237);
    static final Color[] MEDALS = {GameTheme.GOLD, new Color(213, 231, 237), new Color(225, 163, 106)};
    private final boolean ranking;

    FantasyTable(TableModel model, boolean ranking) {
        super(model); this.ranking = ranking; setFont(GameTheme.BODY.deriveFont(13f)); setRowHeight(46);
        setBackground(GameTheme.INK); setForeground(GameTheme.TEXT); setGridColor(new Color(35, 81, 100));
        setShowVerticalLines(false); setShowHorizontalLines(true); setIntercellSpacing(new Dimension(0, 1));
        setRowSelectionAllowed(false); setColumnSelectionAllowed(false); setAutoResizeMode(AUTO_RESIZE_ALL_COLUMNS);
        setFillsViewportHeight(true); getTableHeader().setReorderingAllowed(false);
        getTableHeader().setResizingAllowed(false); getTableHeader().setPreferredSize(new Dimension(0, 38));
        getTableHeader().setFont(GameTheme.BODY.deriveFont(Font.BOLD, 12f));
        getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int col) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, selected, focus, row, col);
                c.setFont(table.getTableHeader().getFont()); c.setForeground(GameTheme.GOLD); c.setBackground(new Color(17, 68, 93));
                c.setHorizontalAlignment(SwingConstants.CENTER); c.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, GameTheme.GOLD)); return c;
            }
        });
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int col) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, selected, focus, row, col);
                c.setBackground(row % 2 == 0 ? new Color(12, 46, 67) : new Color(17, 59, 78));
                c.setForeground(GameTheme.TEXT); c.setFont(FantasyTable.this.getFont());
                c.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                int modelCol = convertColumnIndexToModel(col);
                c.setHorizontalAlignment(modelCol == 1 ? SwingConstants.LEFT : SwingConstants.CENTER);
                if (FantasyTable.this.ranking && row < 3) {
                    c.setForeground(MEDALS[row]); c.setFont(FantasyTable.this.getFont().deriveFont(Font.BOLD));
                    if (col == 0) { c.setText("#" + value); }
                } else if (!FantasyTable.this.ranking && modelCol == 2) {
                    c.setForeground("TH\u1eaeNG".equals(value) ? WIN : "THUA".equals(value) ? LOSE : DRAW);
                    c.setFont(FantasyTable.this.getFont().deriveFont(Font.BOLD));
                }
                c.setToolTipText(value == null ? null : value.toString()); return c;
            }
        };
        for (int col = 0; col < getColumnCount(); col++) { getColumnModel().getColumn(col).setCellRenderer(renderer); }
    }

    @Override public String getToolTipText(MouseEvent event) {
        int row = rowAtPoint(event.getPoint()), col = columnAtPoint(event.getPoint());
        return row < 0 || col < 0 ? null : String.valueOf(getValueAt(row, col));
    }

    void fitColumns(int[] minimums) {
        for (int col = 0; col < getColumnCount(); col++) {
            int width = Math.max(minimums[col], getTableHeader().getFontMetrics(getTableHeader().getFont()).stringWidth(getColumnName(col)) + 24);
            for (int row = 0; row < getRowCount(); row++) {
                String text = String.valueOf(getValueAt(row, col));
                Font font = ranking && row < 3 || !ranking && convertColumnIndexToModel(col) == 2 ? getFont().deriveFont(Font.BOLD) : getFont();
                width = Math.max(width, getFontMetrics(font).stringWidth(text) + 28);
            }
            getColumnModel().getColumn(col).setMinWidth(width);
            getColumnModel().getColumn(col).setPreferredWidth(width); getColumnModel().getColumn(col).setWidth(width);
        }
        revalidate();
    }

    @Override public boolean getScrollableTracksViewportWidth() {
        return getParent() != null && getParent().getWidth() >= getMinimumSize().width;
    }

    JScrollPane scrollPane() {
        JScrollPane scroll = new JScrollPane(this); GameTheme.styleScrollPane(scroll);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.getHorizontalScrollBar().setUnitIncrement(36);
        scroll.getHorizontalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() { thumbColor = GameTheme.GOLD; trackColor = GameTheme.INK; }
            @Override protected javax.swing.JButton createDecreaseButton(int orientation) { return emptyArrow(); }
            @Override protected javax.swing.JButton createIncreaseButton(int orientation) { return emptyArrow(); }
            private javax.swing.JButton emptyArrow() {
                javax.swing.JButton button = new javax.swing.JButton(); button.setPreferredSize(new Dimension(0, 0)); return button;
            }
        });
        scroll.setColumnHeaderView(getTableHeader()); scroll.getViewport().setOpaque(true); scroll.getViewport().setBackground(GameTheme.INK);
        scroll.setBorder(BorderFactory.createLineBorder(GameTheme.GOLD, 2)); return scroll;
    }
}
