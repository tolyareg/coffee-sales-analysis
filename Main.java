import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Main {
    private static final Path SEPTEMBER = Path.of("data/sales_september.csv");
    private static final Path OCTOBER = Path.of("data/sales_october.csv");

    record Sale(String date, String drink, double price, String barista, String payment) {}

    public static void main(String[] args) throws IOException {
        List<Sale> september = readSales(SEPTEMBER, StandardCharsets.UTF_8);
        List<Sale> october = readSales(OCTOBER, Charset.forName("windows-1251"));

        long blankPayments = countBlankPayments(september) + countBlankPayments(october);
        long zeroPrices = countZeroPrices(september) + countZeroPrices(october);
        long septemberCardPayments = september.stream()
                .filter(s -> s.payment().equalsIgnoreCase("карта")).count();

        Map<String, Double> revenueByDrink = new HashMap<>();
        Map<String, Double> revenueByDate = new HashMap<>();
        for (Sale sale : concat(september, october)) {
            if (isValid(sale)) {
                revenueByDrink.merge(sale.drink(), sale.price(), Double::sum);
                revenueByDate.merge(sale.date(), sale.price(), Double::sum);
            }
        }

        Map<String, Long> octoberOrdersByBarista = new HashMap<>();
        for (Sale sale : october) {
            if (isValid(sale)) {
                octoberOrdersByBarista.merge(sale.barista(), 1L, Long::sum);
            }
        }

        String topDrink = maxKey(revenueByDrink);
        String topDate = maxKey(revenueByDate);
        String topBarista = maxKey(octoberOrdersByBarista);

        String report = """
                ОТЧЁТ ПО ПРОДАЖАМ КОФЕЙНИ
                ==========================
                Строк с пустой оплатой (оба месяца): %d
                Строк с нулевой ценой (оба месяца): %d
                Оплат картой за сентябрь: %d
                Напиток с максимальной выручкой: %s (%.2f)
                Дата с максимальной выручкой: %s (%.2f)
                Бариста с наибольшим числом корректных заказов в октябре: %s (%d)
                """.formatted(
                blankPayments, zeroPrices, septemberCardPayments,
                topDrink, revenueByDrink.get(topDrink),
                topDate, revenueByDate.get(topDate),
                topBarista, octoberOrdersByBarista.get(topBarista));

        Files.writeString(Path.of("report.txt"), report, StandardCharsets.UTF_8);
        System.out.println(report);
        System.out.println("Отчёт сохранён в report.txt");
    }

    private static List<Sale> readSales(Path file, Charset charset) throws IOException {
        List<String> lines = Files.readAllLines(file, charset);
        List<Sale> sales = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            List<String> c = parseCsvLine(lines.get(i));
            if (c.size() < 8) continue;
            try {
                sales.add(new Sale(c.get(1).trim(), c.get(2).trim(),
                        Double.parseDouble(c.get(4).trim().replace(',', '.')),
                        c.get(5).trim(), c.get(6).trim()));
            } catch (NumberFormatException ignored) {
                // Skip rows with an invalid price value.
            }
        }
        return sales;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                result.add(value.toString());
                value.setLength(0);
            } else value.append(ch);
        }
        result.add(value.toString());
        return result;
    }

    private static boolean isValid(Sale s) {
        return !s.payment().isBlank() && s.price() != 0.0;
    }

    private static long countBlankPayments(List<Sale> sales) {
        return sales.stream().filter(s -> s.payment().isBlank()).count();
    }

    private static long countZeroPrices(List<Sale> sales) {
        return sales.stream().filter(s -> s.price() == 0.0).count();
    }

    private static List<Sale> concat(List<Sale> a, List<Sale> b) {
        List<Sale> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static <T> T maxKey(Map<T, ? extends Number> map) {
        return map.entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().doubleValue()))
                .orElseThrow().getKey();
    }
}
