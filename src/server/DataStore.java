package server;

import java.io.IOException;
import java.util.Map;
import model.MatchHistory;

/** Persistent account and match snapshots owned by one ServerManager. */
public interface DataStore {
    void initialize() throws IOException;
    void load(Map<String, Account> accounts, Map<String, MatchHistory> history) throws IOException;
    void save(Map<String, Account> accounts, Map<String, MatchHistory> history) throws IOException;

    default void saveRegistration(Map<String, Account> accounts, Map<String, MatchHistory> history,
            String username, String password) throws IOException {
        save(accounts, history);
    }

    /** MySQL demo support; file-based storage does not retain plaintext passwords. */
    default void recordSuccessfulLogin(String username, String password) throws IOException { }
}
