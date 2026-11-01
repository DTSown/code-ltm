package client;

import javax.swing.SwingUtilities;

public class GameClient {
    public static void main(String[] args) {
        ClientConnection connection = new ClientConnection();
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame(connection);
            connection.setMessageListener(mainFrame);
            mainFrame.setVisible(true);
            mainFrame.showLogin();
        });
    }
}
