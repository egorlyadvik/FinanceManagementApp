package cli;

import core.FinanceService;
import core.model.Operation;
import core.model.OperationType;
import core.model.Wallet;
import infra.CsvWalletFile;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ConsoleApp {
    private final FinanceService finance;
    private final CsvWalletFile csv;
    private final Scanner input;
    private final PrintWriter output;
    private final ConsoleReport report;

    public ConsoleApp(FinanceService finance, CsvWalletFile csv, Scanner input, PrintWriter output) {
        this.finance = finance;
        this.csv = csv;
        this.input = input;
        this.output = output;
        this.report = new ConsoleReport(output);
    }

    public void run() {
        output.println("Добро пожаловать в приложение по ведению личных финансов! Введите команду help для справки");
        while (true) {
            try {
                String login = finance.getCurrentLogin();
                String command = ask(login == null ? "> " : login + " > ").toLowerCase(Locale.ROOT);
                if (command.equals("exit")) {
                    finance.logout();
                    break;
                }
                execute(command);
            } catch (NoSuchElementException e) {
                try {
                    finance.logout();
                } catch (IOException saveError) {
                    output.println("Ошибка сохранения кошелька: " + saveError.getMessage());
                }
                break;
            } catch (IllegalArgumentException | IllegalStateException e) {
                output.println(e.getMessage());
            } catch (IOException e) {
                output.println("Ошибка работы с файлом: " + e.getMessage());
            }
        }
        output.println("Работа завершена");
        output.flush();
    }

    private void execute(String command) throws IOException {
        switch (command) {
            case "register" -> {
                finance.register(ask("Логин: "), askRaw("Пароль: "));
                output.println("Аккаунт создан, для входа введите команду login");
            }
            case "login" -> {
                finance.validateLoggedOut();
                String login = ask("Логин: ");
                finance.validateLogin(login);
                finance.login(login, askRaw("Пароль: "));
                output.println("Вход выполнен");
            }
            case "logout" -> {
                if (finance.getCurrentLogin() == null) {
                    throw new IllegalStateException("Сначала войдите в аккаунт");
                }
                finance.logout();
                output.println("Выход выполнен, кошелёк сохранён");
            }
            case "category" -> {
                finance.getWallet().addCategory(ask("Новая категория: "));
                output.println("Категория создана");
            }
            case "rename" -> {
                Wallet wallet = finance.getWallet();
                String oldName = ask("Текущая категория: ");
                wallet.validateExistingCategory(oldName);
                String newName = ask("Новая категория: ");
                wallet.renameCategory(oldName, newName);
                output.println("Категория переименована");
            }
            case "income" -> readAndAddOperation(OperationType.INCOME);
            case "expense" -> readAndAddOperation(OperationType.EXPENSE);
            case "budget" -> readAndSetBudget();
            case "report" -> showReport(report);
            case "report-file" -> saveReport();
            case "export" -> {
                Wallet wallet = finance.getWallet();
                csv.write(askPath("Путь к CSV-файлу: "), wallet);
                output.println("Кошелёк выгружен");
            }
            case "import" -> {
                if (finance.getCurrentLogin() == null) {
                    throw new IllegalStateException("Сначала войдите в аккаунт");
                }
                Wallet imported = csv.read(askPath("Путь к CSV для импорта кошелька: "));
                finance.replaceWallet(imported);
                output.println("Кошелёк загружен");
                imported.getCategories().forEach(category -> report.warnBudget(imported, category));
                report.warnBalance(imported);
            }
            case "transfer" -> {
                Wallet wallet = finance.getWallet();
                String recipient = ask("Логин получателя: ");
                finance.transfer(recipient, askAmount("Сумма: "));
                output.println("Перевод выполнен");
                report.warnBudget(wallet, Wallet.TRANSFER_CATEGORY);
                report.warnBalance(wallet);
            }
            case "help" -> printHelp();
            default -> output.println("Неизвестная команда. Если нужна помощь, введите команду help");
        }
    }

    private void readAndAddOperation(OperationType operationType) {
        Wallet wallet = finance.getWallet();
        String category = ask("Категория: ");
        wallet.validateExistingCategory(category);
        BigDecimal amount = askAmount("Сумма: ");
        wallet.addOperation(new Operation(operationType, category, amount, ""));
        output.println("Операция добавлена");
        if (operationType == OperationType.EXPENSE) {
            report.warnBudget(wallet, category);
        }
        report.warnBalance(wallet);
    }

    private void readAndSetBudget() {
        Wallet wallet = finance.getWallet();
        String category = ask("Категория: ");
        wallet.validateExistingCategory(category);
        BigDecimal limit = askAmount("Лимит: ");
        wallet.setBudget(category, limit);
        output.println("Бюджет установлен");
        report.warnBudget(wallet, category);
    }

    private Set<String> selectCategories(Wallet wallet) {
        String selection = ask("Введите категории через ';' или Enter для всех: ");
        if (selection.isEmpty()) {
            return wallet.getCategories();
        }
        Set<String> categories = new LinkedHashSet<>();
        for (String name : selection.split(";", -1)) {
            String category = name.strip();
            if (category.isEmpty()) {
                throw new IllegalArgumentException("Укажите название каждой категории");
            }
            categories.add(category);
        }
        return categories;
    }

    private void showReport(ConsoleReport destination) {
        Wallet wallet = finance.getWallet();
        Set<String> categories = selectCategories(wallet);
        destination.print(wallet, categories);
    }

    private void saveReport() throws IOException {
        StringWriter text = new StringWriter();
        showReport(new ConsoleReport(new PrintWriter(text)));
        Path path = askPath("Путь к файлу отчета: ");
        Files.writeString(path, text.toString());
        output.println("Отчёт сохранён");
    }

    private String ask(String prompt) {
        return askRaw(prompt).strip();
    }

    private String askRaw(String prompt) {
        output.print(prompt);
        output.flush();
        return input.nextLine();
    }

    private Path askPath(String prompt) {
        String path = ask(prompt);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Введите путь к файлу");
        }
        return Path.of(path);
    }

    private BigDecimal askAmount(String prompt) {
        String value = ask(prompt);
        if (!value.matches("[0-9]+(\\.[0-9]+)?")) {
            throw new IllegalArgumentException("Введите число, дробная часть должна отделяться знаком '.'");
        }
        return new BigDecimal(value);
    }

    private void printHelp() {
        output.print("""
                Команда      Что спрашивает программа                                            Действие
                ------------- -------------------------------------------------------------------- -------------------------
                register     Логин, Пароль                                                       Создать аккаунт
                login        Логин, Пароль                                                       Войти в аккаунт
                logout       -                                                                   Выйти из аккаунта
                category     Новая категория                                                     Создать категорию
                rename       Текущая категория, Новая категория                                  Переименовать категорию
                income       Категория, Сумма                                                    Добавить доход
                expense      Категория, Сумма                                                    Добавить расход
                budget       Категория, Лимит                                                    Установить лимит расходов
                report       Введите категории через ';' или Enter для всех                      Показать отчет
                report-file  Введите категории через ';' или Enter для всех, Путь к файлу отчета Сохранить отчет
                export       Путь к CSV-файлу                                                    Выгрузить кошелёк
                import       Путь к CSV для импорта кошелька                                     Загрузить кошелёк
                transfer     Логин получателя, Сумма                                             Оформить перевод
                help         -                                                                   Показать справку
                exit         -                                                                   Завершить работу

                Каждый параметр вводится отдельной строкой по подсказке программы.
                Категорию нужно создать командой category до income, expense и budget.

                Примеры ввода
                  category
                  Новая категория: Еда

                  expense
                  Категория: Еда
                  Сумма: 300.50

                  report
                  Введите категории через ';' или Enter для всех: Еда;Такси

                  report-file
                  Введите категории через ';' или Enter для всех: Еда;Такси
                  Путь к файлу отчета: report.txt
                """);
    }
}
