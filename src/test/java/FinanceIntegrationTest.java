import core.FinanceService;
import core.model.Operation;
import core.model.OperationType;
import core.model.Wallet;
import infra.CsvWalletFile;
import infra.FileFinanceStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinanceIntegrationTest {
    @TempDir
    Path directory;

    @Test
    void checksCredentialsAfterRestart() throws IOException {
        FinanceService service = open();
        service.register("1mary", "пароль");

        FinanceService restarted = open();
        assertThrows(IllegalArgumentException.class, () -> restarted.login("1mary", "ошибка"));
        assertThrows(IllegalArgumentException.class, () -> restarted.login("jack", "пароль"));
        restarted.login("1mary", "пароль");

        assertEquals("1mary", restarted.getCurrentLogin());
        assertEquals("пароль", new FileFinanceStorage(directory).loadUsers().get("1mary"));
    }

    @Test
    void rejectsInvalidRegistration() throws IOException {
        FinanceService service = open();
        service.register("mary", "secret");

        assertThrows(IllegalArgumentException.class, () -> service.register("mary", "another"));
        assertThrows(IllegalArgumentException.class, () -> service.register("../jack", "secret"));
        assertThrows(IllegalArgumentException.class, () -> service.register("", "secret"));
        assertThrows(IllegalArgumentException.class, () -> service.register("A", "secret"));
        assertThrows(IllegalArgumentException.class, () -> service.register("a".repeat(31), "secret"));
        assertThrows(IllegalArgumentException.class, () -> service.register("jack", " "));
        service.login("mary", "secret");
    }

    @Test
    void restoresSeparateWallets() throws IOException {
        FinanceService service = withTwoUsers();
        service.login("mary", "one");
        addIncome(service.getWallet(), "600");
        service.getWallet().setBudget("Зарплата", new BigDecimal("250"));
        service.logout();
        service.login("jack", "two");
        addIncome(service.getWallet(), "100");
        service.logout();

        FinanceService restarted = open();
        restarted.login("mary", "one");
        assertEquals(new BigDecimal("600"), restarted.getWallet().balance());
        assertEquals(new BigDecimal("250"), restarted.getWallet().getBudget("Зарплата"));
        restarted.logout();
        restarted.login("jack", "two");
        assertEquals(new BigDecimal("100"), restarted.getWallet().balance());
    }

    @Test
    void transfersToOfflineUserAndSavesBothOperations() throws IOException {
        FinanceService service = withTwoUsers();
        service.login("jack", "two");
        addIncome(service.getWallet(), "100");
        service.logout();

        service.login("mary", "one");
        addIncome(service.getWallet(), "500");
        service.transfer("jack", new BigDecimal("40"));
        service.transfer("jack", new BigDecimal("60"));
        service.logout();

        FinanceService restarted = open();
        restarted.login("mary", "one");
        assertEquals(new BigDecimal("400"), restarted.getWallet().balance());
        assertEquals(new Operation(OperationType.EXPENSE, "Переводы", new BigDecimal("40"), "jack"),
                restarted.getWallet().getOperations().get(1));
        restarted.logout();

        restarted.login("jack", "two");
        assertEquals(new BigDecimal("200"), restarted.getWallet().balance());
        assertEquals(new Operation(OperationType.INCOME, "Переводы", new BigDecimal("60"), "mary"),
                restarted.getWallet().getOperations().get(2));
    }

    @Test
    void rejectsInvalidTransfersWithoutChangingWallets() throws IOException {
        FinanceService service = withTwoUsers();
        service.login("mary", "one");
        addIncome(service.getWallet(), "50");

        assertThrows(IllegalArgumentException.class, () -> service.transfer("jack", new BigDecimal("51")));
        assertThrows(IllegalArgumentException.class, () -> service.transfer("mary", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> service.transfer("other", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> service.transfer("jack", BigDecimal.ZERO));
        assertEquals(new BigDecimal("50"), service.getWallet().balance());
        service.logout();
        service.login("jack", "two");
        assertEquals(BigDecimal.ZERO, service.getWallet().balance());
    }

    @Test
    void exportsAndImportsFullWalletSnapshot() throws IOException {
        Wallet wallet = new Wallet();
        wallet.addCategory("Еда, напитки");
        wallet.addCategory("Такси");
        wallet.setBudget("Еда, напитки", new BigDecimal("50.25"));
        wallet.addOperation(new Operation(OperationType.EXPENSE, "Еда, напитки", new BigDecimal("12.30"), "jack"));

        CsvWalletFile csv = new CsvWalletFile();
        Path file = directory.resolve("wallet.csv");
        csv.write(file, wallet);
        assertEquals("type;category;amount;anotherUserLogin", Files.readAllLines(file).get(0));
        Wallet restored = csv.read(file);

        assertEquals(Set.of("Еда, напитки", "Такси"), restored.getCategories());
        assertEquals(wallet.getBudget("Еда, напитки"), restored.getBudget("Еда, напитки"));
        assertEquals(wallet.getOperations(), restored.getOperations());
        csv.write(file, new Wallet());
        assertEquals(1, Files.readAllLines(file).size());
    }

    private FinanceService open() throws IOException {
        return new FinanceService(new FileFinanceStorage(directory));
    }

    private FinanceService withTwoUsers() throws IOException {
        FinanceService service = open();
        service.register("mary", "one");
        service.register("jack", "two");
        return service;
    }

    private void addIncome(Wallet wallet, String amount) {
        wallet.addCategory("Зарплата");
        wallet.addOperation(new Operation(OperationType.INCOME, "Зарплата", new BigDecimal(amount), ""));
    }
}
