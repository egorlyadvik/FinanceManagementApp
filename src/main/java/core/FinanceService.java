package core;

import core.model.Operation;
import core.model.OperationType;
import core.model.Wallet;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class FinanceService {
    private final FinanceStorage storage;
    private final Map<String, String> users;
    private final Map<String, Wallet> wallets = new LinkedHashMap<>();
    private String currentLogin;

    public FinanceService(FinanceStorage storage) throws IOException {
        this.storage = storage;
        this.users = storage.loadUsers();
    }

    public void register(String login, String password) throws IOException {
        if (!login.matches("[a-z0-9_-]{1,30}")) {
            throw new IllegalArgumentException(
                    "Логин должен содержать только строчные латинские буквы, цифры, дефисы, подчёркивания. " +
                            "Длина от 1 до 30");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException("Пароль не может быть пустым");
        }
        if (users.containsKey(login)) {
            throw new IllegalArgumentException("Такой логин уже занят");
        }

        Map<String, String> updatedUsers = new LinkedHashMap<>(users);
        updatedUsers.put(login, password);
        storage.saveUsers(updatedUsers);
        users.put(login, password);
    }

    public void login(String login, String password) throws IOException {
        validateLoggedOut();
        validateLogin(login);
        String expectedPassword = users.get(login);
        if (expectedPassword == null || !expectedPassword.equals(password)) {
            throw new IllegalArgumentException("Некорректный логин или пароль");
        }
        wallets.put(login, storage.loadWallet(login));
        currentLogin = login;
    }

    public String getCurrentLogin() {
        return currentLogin;
    }

    public Wallet getWallet() {
        if (currentLogin == null) {
            throw new IllegalStateException("Сначала войдите в аккаунт");
        }
        return wallets.get(currentLogin);
    }

    public void replaceWallet(Wallet wallet) {
        if (currentLogin == null) {
            throw new IllegalStateException("Сначала войдите в аккаунт");
        }
        wallets.put(currentLogin, wallet);
    }

    public void transfer(String recipient, BigDecimal amount) throws IOException {
        Wallet senderWallet = getWallet();
        Wallet.validatePositiveAmount(amount);
        if (!users.containsKey(recipient)) {
            throw new IllegalArgumentException("Получатель не найден");
        }
        if (currentLogin.equals(recipient)) {
            throw new IllegalArgumentException("Перевод самому себе запрещен");
        }
        if (senderWallet.balance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Недостаточно денег для перевода");
        }
        Wallet recipientWallet = wallets.get(recipient);
        if (recipientWallet == null) {
            recipientWallet = storage.loadWallet(recipient);
            wallets.put(recipient, recipientWallet);
        }
        addTransfer(senderWallet, OperationType.EXPENSE, amount, recipient);
        addTransfer(recipientWallet, OperationType.INCOME, amount, currentLogin);
    }

    public void logout() throws IOException {
        if (!wallets.isEmpty()) {
            storage.saveWallets(wallets);
        }
        wallets.clear();
        currentLogin = null;
    }

    public void validateLoggedOut() {
        if (currentLogin != null) {
            throw new IllegalStateException("Сначала нужно выйти из аккаунта");
        }
    }

    public void validateLogin(String login) {
        if (!users.containsKey(login)) {
            throw new IllegalArgumentException("Такой логин не существует");
        }
    }

    private void addTransfer(Wallet wallet, OperationType operationType, BigDecimal amount, String anotherUserLogin) {
        String category = Wallet.TRANSFER_CATEGORY;
        if (!wallet.getCategories().contains(category)) {
            wallet.addCategory(category);
        }
        wallet.addOperation(new Operation(operationType, category, amount, anotherUserLogin));
    }
}
