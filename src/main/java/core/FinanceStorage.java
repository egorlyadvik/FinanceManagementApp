package core;

import core.model.Wallet;

import java.io.IOException;
import java.util.Map;

public interface FinanceStorage {
    Map<String, String> loadUsers() throws IOException;

    void saveUsers(Map<String, String> users) throws IOException;

    Wallet loadWallet(String login) throws IOException;

    void saveWallets(Map<String, Wallet> wallets) throws IOException;
}
