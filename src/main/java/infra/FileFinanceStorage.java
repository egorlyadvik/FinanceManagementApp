package infra;

import core.FinanceStorage;
import core.model.Wallet;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class FileFinanceStorage implements FinanceStorage {
    public static final String ACCOUNTS_FILE = "accounts.properties";
    public static final String WALLETS_FILE = "wallets.properties";

    private final Path directory;

    public FileFinanceStorage(Path directory) {
        this.directory = directory;
    }

    @Override
    public Map<String, String> loadUsers() throws IOException {
        Properties data = read(ACCOUNTS_FILE);
        Map<String, String> users = new HashMap<>();
        for (String login : data.stringPropertyNames()) {
            users.put(login, data.getProperty(login));
        }
        return users;
    }

    @Override
    public void saveUsers(Map<String, String> users) throws IOException {
        Properties data = new Properties();
        data.putAll(users);
        write(ACCOUNTS_FILE, data);
    }

    @Override
    public Wallet loadWallet(String login) throws IOException {
        String content = read(WALLETS_FILE).getProperty(login);
        return content == null ? new Wallet() : CsvWalletFile.decode(content);
    }

    @Override
    public void saveWallets(Map<String, Wallet> wallets) throws IOException {
        Properties data = read(WALLETS_FILE);
        for (Map.Entry<String, Wallet> entry : wallets.entrySet()) {
            String login = entry.getKey();
            Wallet wallet = entry.getValue();
            data.setProperty(login, CsvWalletFile.encode(wallet));
        }
        write(WALLETS_FILE, data);
    }

    private Properties read(String name) throws IOException {
        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(directory.resolve(name))) {
            data.load(reader);
        } catch (NoSuchFileException e) {
            return data;
        }
        return data;
    }

    private void write(String name, Properties data) throws IOException {
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, "finance-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp)) {
                data.store(writer, null);
            }
            Files.move(temp, directory.resolve(name), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
