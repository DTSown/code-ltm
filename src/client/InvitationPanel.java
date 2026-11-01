package client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public final class InvitationPanel extends RoundedPanel {
    private boolean answered;
    private final Consumer<Boolean> answer;

    public InvitationPanel(String inviter, Consumer<Boolean> answer) {
        this(inviter, answer, false);
    }

    public InvitationPanel(String inviter, Consumer<Boolean> answer, boolean rematch) {
        this.answer = answer;
        setLayout(new BorderLayout(14, 20));
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JLabel title = new JLabel(rematch ? "T\u00c1I \u0110\u1ea4U!" : "TH\u00c1CH \u0110\u1ea4U!", JLabel.CENTER);
        title.setFont(GameTheme.BODY.deriveFont(Font.BOLD, 26f)); title.setForeground(GameTheme.GOLD);
        add(title, BorderLayout.NORTH);
        JPanel middle = new JPanel(new BorderLayout(16, 0)); middle.setOpaque(false);
        middle.add(new JLabel(new PlayerAvatar(inviter, 80)), BorderLayout.WEST);
        JTextArea message = new JTextArea(inviter + (rematch ? " mu\u1ed1n t\u00e1i \u0111\u1ea5u" : " mu\u1ed1n thi \u0111\u1ea5u v\u1edbi b\u1ea1n"));
        message.setName("invitation-message"); message.setFont(GameTheme.BODY.deriveFont(16f));
        message.setForeground(GameTheme.TEXT); message.setEditable(false); message.setOpaque(false);
        message.setLineWrap(true); message.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(message); GameTheme.styleScrollPane(scroll);
        middle.add(scroll, BorderLayout.CENTER); add(middle, BorderLayout.CENTER);
        GameButton accept = new GameButton(rematch ? "\u0110\u1ed2NG \u00dd" : "CH\u1ea4P NH\u1eacN", GameButton.Variant.GREEN);
        GameButton reject = new GameButton("T\u1eea CH\u1ed0I", GameButton.Variant.RED);
        accept.setName("invitation-accept"); reject.setName("invitation-reject");
        accept.addActionListener(event -> answerOnce(true));
        reject.addActionListener(event -> answerOnce(false));
        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0)); buttons.setOpaque(false);
        buttons.setPreferredSize(new Dimension(0, 48)); buttons.add(accept); buttons.add(reject);
        add(buttons, BorderLayout.SOUTH); setPreferredSize(new Dimension(470, 290));
    }

    public void answerOnce(boolean accepted) {
        if (!answered) { answered = true; answer.accept(accepted); }
    }
}
