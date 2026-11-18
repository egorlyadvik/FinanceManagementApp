import cli.ConsoleApp;
import core.FinanceService;
import infra.CsvWalletFile;
import infra.FileFinanceStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleIntegrationTest {
    @TempDir
    Path directory;

    @Test
    void rejectsUnknownLoginWithoutAskingForPassword() throws IOException {
        String output = runApp("""
                login
                unknown
                exit
                """);

        assertTrue(output.contains("Такой логин не существует"));
        assertFalse(output.contains("Пароль: "));
        assertTrue(output.contains("Работа завершена"));
    }

    @Test
    void rejectsLoginWhileSignedInWithoutAskingForCredentials() throws IOException {
        String output = run("""
                login
                exit
                """);

        assertTrue(output.contains("mary > Сначала нужно выйти из аккаунта"));
        assertTrue(output.contains("Работа завершена"));
    }

    @Test
    void printsFullAndFilteredReportsForAssignmentExample() throws IOException {
        String output = run("""
                category
                Еда
                category
                Развлечения
                category
                Коммунальные услуги
                category
                Такси
                category
                Зарплата
                category
                Бонус
                expense
                Еда
                300
                expense
                Еда
                500
                expense
                Развлечения
                3000
                expense
                Коммунальные услуги
                3000
                expense
                Такси
                1500
                income
                Зарплата
                20000
                income
                Зарплата
                40000
                income
                Бонус
                3000
                budget
                Еда
                4000
                budget
                Развлечения
                3000
                budget
                Коммунальные услуги
                2500
                report

                report
                Еда;Такси;Еда
                exit
                """);

        String fullReport = """
                Общий доход: 63000.00
                Доходы по категориям:
                  Зарплата: 60000.00
                  Бонус: 3000.00

                Общие расходы: 8300.00
                Расходы по категориям:
                  Еда: 800.00
                  Развлечения: 3000.00
                  Коммунальные услуги: 3000.00
                  Такси: 1500.00

                Бюджет по категориям:
                  Еда: 4000.00, Оставшийся бюджет: 3200.00
                  Развлечения: 3000.00, Оставшийся бюджет: 0.00
                  Коммунальные услуги: 2500.00, Оставшийся бюджет: -500.00
                """.replace("\n", System.lineSeparator());
        assertTrue(output.contains(fullReport));
        assertTrue(output.contains("Общие расходы: 2300.00" + System.lineSeparator()));
    }

    @Test
    void overwritesTextFileWithFilteredReportAndBudget() throws IOException {
        Path file = directory.resolve("report.txt");
        String output = run("""
                category
                Еда
                category
                Такси
                category
                Зарплата
                income
                Зарплата
                100
                budget
                Еда
                40.00
                expense
                Еда
                10.0
                expense
                Такси
                15.25
                report-file

                %s
                report-file
                Еда;Такси
                %s
                exit
                """.formatted(file, file));

        String text = Files.readString(file);
        assertTrue(output.contains("Отчёт сохранён"));
        assertTrue(text.contains("Общие расходы: 25.25"));
        assertTrue(text.contains("Расходы по категориям:"));
        assertTrue(text.contains("  Еда: 10.00" + System.lineSeparator()));
        assertTrue(text.contains("  Еда: 40.00, Оставшийся бюджет: 30.00" + System.lineSeparator()));
        assertTrue(text.contains("  Такси: 15.25"));
        assertFalse(text.contains("Зарплата"));
    }

    @Test
    void reportsInvalidInputAndContinuesReading() throws IOException {
        String output = run("""
                help
                category
                Еда
                income
                Еда
                не число
                expense
                Еда
                -3
                expense
                Еда
                3,12
                expense
                Еда
                3.123
                report
                Еда;Такси
                report
                Еда
                exit
                """);

        assertTrue(output.contains("Примеры ввода"));
        assertTrue(output.contains("Команда"));
        assertTrue(output.contains("Что спрашивает программа"));
        assertTrue(output.contains("Действие"));
        assertTrue(output.contains("Новая категория: Еда"));
        assertTrue(output.contains("Категория: Еда"));
        assertTrue(output.contains("Сумма: 300.50"));
        assertTrue(output.contains("Введите категории через ';' или Enter для всех: Еда;Такси"));
        assertTrue(output.contains("Путь к файлу отчета: report.txt"));
        assertTrue(output.contains("Введите число"));
        assertTrue(output.contains("Значение должно быть положительным, до двух знаков после запятой"));
        assertTrue(output.contains("Категория не найдена: Такси"));
        assertTrue(output.contains("В выбранных категориях пока нет операций"));
        assertTrue(output.contains("Работа завершена"));
    }

    @Test
    void rejectsUnknownCategoriesBeforeAskingForAmounts() throws IOException {
        String output = run("""
                income
                Зарплата
                expense
                Такси
                budget
                Еда
                exit
                """);

        assertTrue(output.contains("Категория не найдена: Зарплата"));
        assertTrue(output.contains("Категория не найдена: Такси"));
        assertTrue(output.contains("Категория не найдена: Еда"));
        assertFalse(output.contains("Сумма: "));
        assertFalse(output.contains("Лимит: "));
        assertTrue(output.contains("Работа завершена"));
    }

    @Test
    void warnsAtEightyPercentZeroBalanceAndOverspending() throws IOException {
        String output = run("""
                category
                Еда
                budget
                Еда
                100000
                income
                Еда
                80000.00
                expense
                Еда
                80000
                expense
                Еда
                21000
                exit
                """);

        assertTrue(output.contains("Оповещение: В категории 'Еда' использовано 80% бюджета или больше. "
                + "Остаток: 20000.00"));
        assertTrue(output.contains("Оповещение: Баланс равен нулю"));
        assertTrue(output.contains("Оповещение: Бюджет категории 'Еда' превышен на 1000.00"));
        assertTrue(output.contains("Оповещение: Расходы превысили доходы"));
    }

    @Test
    void keepsTransferBudgetAfterRejectedRenameAndRestarting() throws IOException {
        String firstOutput = run("""
                register
                jack
                two
                category
                Зарплата
                income
                Зарплата
                100
                transfer
                jack
                20
                budget
                Переводы
                50
                rename
                Переводы
                Банковские переводы
                exit
                """);

        assertTrue(firstOutput.contains("Нельзя переименовать категорию: Переводы"));

        String output = runApp("""
                login
                mary
                one
                transfer
                jack
                35
                report
                Переводы
                exit
                """);

        assertTrue(output.contains("Оповещение: Бюджет категории 'Переводы' превышен на 5.00"));
        assertTrue(output.contains("Общие расходы: 55.00" + System.lineSeparator()));
    }

    @Test
    void malformedImportLeavesWalletIntact() throws IOException {
        Path file = directory.resolve("broken.csv");
        Files.writeString(file, """
                type;category;amount;anotherUserLogin
                CATEGORY;Другая категория;;
                INCOME;Другая категория;не число;
                """);
        String output = run("""
                category
                Зарплата
                income
                Зарплата
                100
                import
                %s
                report

                exit
                """.formatted(file));

        assertTrue(output.contains("Некорректная строка CSV под номером 3"));
        assertTrue(output.contains("Общий доход: 100.00" + System.lineSeparator()));
        assertFalse(output.contains("Другая категория"));
    }

    @Test
    void importsSnapshotAsReplacement() throws IOException {
        Path file = directory.resolve("backup.csv");
        String output = run("""
                category
                Зарплата
                income
                Зарплата
                100
                export
                %s
                income
                Зарплата
                70
                import
                %s
                report

                exit
                """.formatted(file, file));

        assertTrue(output.contains("Кошелёк выгружен"));
        assertTrue(output.contains("Кошелёк загружен"));
        assertTrue(output.contains("Общий доход: 100.00" + System.lineSeparator()));
    }

    @Test
    void rejectsTruncatedCsvWithoutChangingWalletOrStoppingCli() throws IOException {
        Path file = directory.resolve("truncated.csv");
        Files.writeString(file, """
                type;category;amount;anotherUserLogin
                CATEGORY;Другая категория;;
                INCOME;Другая категория;50
                """);

        String output = run("""
                category
                Зарплата
                income
                Зарплата
                100
                budget
                Зарплата
                200
                import
                %s
                report

                exit
                """.formatted(file));

        assertTrue(output.contains("Некорректная строка CSV под номером 3"));
        assertTrue(output.contains("Ожидается ровно 4 столбца, получено: 3"));
        assertTrue(output.contains("Общий доход: 100.00" + System.lineSeparator()));
        assertFalse(output.contains("Другая категория"));
        assertTrue(output.contains("Работа завершена"));

        FinanceService restarted = new FinanceService(new FileFinanceStorage(directory.resolve("data")));
        restarted.login("mary", "one");
        assertEquals(new BigDecimal("100"), restarted.getWallet().balance());
        assertEquals(new BigDecimal("200"), restarted.getWallet().getBudget("Зарплата"));
        assertEquals(Set.of("Зарплата"), restarted.getWallet().getCategories());
        assertEquals(1, restarted.getWallet().getOperations().size());
    }

    @Test
    void savesWalletOnEndOfInputBeforeCommandOrAmount() throws IOException {
        String[] endings = {"", "expense\nЗарплата\n"};
        for (int i = 0; i < endings.length; i++) {
            Path data = directory.resolve("eof-" + i);
            FinanceService finance = new FinanceService(new FileFinanceStorage(data));
            StringWriter output = new StringWriter();
            String commands = """
                    register
                    mary
                    one
                    login
                    mary
                    one
                    category
                    Зарплата
                    income
                    Зарплата
                    100
                    budget
                    Зарплата
                    200
                    """ + endings[i];

            try (Scanner scanner = new Scanner(commands)) {
                new ConsoleApp(finance, new CsvWalletFile(), scanner, new PrintWriter(output)).run();
            }

            assertTrue(output.toString().contains("Работа завершена"));
            FinanceService restarted = new FinanceService(new FileFinanceStorage(data));
            restarted.login("mary", "one");
            assertEquals(new BigDecimal("100"), restarted.getWallet().balance());
            assertEquals(new BigDecimal("200"), restarted.getWallet().getBudget("Зарплата"));
            assertEquals(Set.of("Зарплата"), restarted.getWallet().getCategories());
            assertEquals(1, restarted.getWallet().getOperations().size());
        }
    }

    private String run(String commands) throws IOException {
        return runApp("""
                register
                mary
                one
                login
                mary
                one
                """ + commands);
    }

    private String runApp(String commands) throws IOException {
        FinanceService finance = new FinanceService(new FileFinanceStorage(directory.resolve("data")));
        StringWriter output = new StringWriter();
        try (Scanner scanner = new Scanner(commands)) {
            new ConsoleApp(finance, new CsvWalletFile(), scanner, new PrintWriter(output)).run();
        }
        return output.toString();
    }
}
