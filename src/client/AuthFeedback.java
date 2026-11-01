package client;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public final class AuthFeedback {
    private AuthFeedback() { }

    public static JPanel createContent(String title, String message, boolean error, Runnable close) {
        RoundedPanel panel = new RoundedPanel();
        panel.setLayout(new BorderLayout(12, 16));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JLabel heading = new JLabel(title);
        heading.setFont(GameTheme.BODY.deriveFont(java.awt.Font.BOLD, 18f));
        heading.setForeground(GameTheme.GOLD);
        panel.add(heading, BorderLayout.NORTH);
        JTextArea text = new JTextArea(message);
        text.setName("auth-dialog-message"); text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true);
        text.setFont(GameTheme.BODY); text.setForeground(GameTheme.TEXT); text.setOpaque(false);
        text.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(text);
        GameTheme.styleScrollPane(scroll);
        scroll.setPreferredSize(new Dimension(370, 100));
        panel.add(scroll, BorderLayout.CENTER);
        GameButton button = new GameButton(error ? "TH\u1eec L\u1ea0I" : "\u0110\u1ed2NG \u00dd", true);
        button.setName("auth-dialog-close"); button.addActionListener(event -> close.run());
        panel.add(button, BorderLayout.SOUTH);
        return panel;
    }

    public static void show(Component parent, String title, String message, boolean error) {
        if (GraphicsEnvironment.isHeadless()) { return; }
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.DOCUMENT_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel content = createContent(title, message, error, dialog::dispose);
        if (owner instanceof MainFrame) { ((MainFrame) owner).installSounds(content); }
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton((javax.swing.JButton) content.getComponent(content.getComponentCount() - 1));
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack(); dialog.setMinimumSize(new Dimension(400, 260));
        dialog.setLocationRelativeTo(parent); dialog.setVisible(true);
    }
}
