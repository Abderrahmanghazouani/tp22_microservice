package com.example.loadtest;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Partie D + E : pour chaque client HTTP (rest / feign / webclient) et chaque charge (10..500 utilisateurs
 * simultanes) on mesure : temps moyen, P95, debit (req/s), taux d'erreur, CPU et RAM du service-client.
 * Resultat : results/perf-<eureka|consul>.md
 */
public class PerfTest {
    static final String[] METHODES = {"rest", "feign", "webclient"};
    static final int[] CHARGES = {10, 50, 100, 200, 500};

    record Mesure(String methode, int users, int requetes, double moyMs, double p95Ms, double debit,
                  double erreurs, double cpuMoy, double cpuMax, double ramMb) {}

    public static void main(String[] args) throws Exception {
        String label = Common.discoveryLabel();
        System.out.println("Decouverte detectee : " + label);
        // echauffement (JIT, pools de connexions, cache LoadBalancer)
        for (String m : METHODES) run(m, 20, 300);

        List<Mesure> res = new ArrayList<>();
        for (int users : CHARGES) {
            for (String m : METHODES) {
                Mesure r = run(m, users, Math.max(1000, users * 6));
                res.add(r);
                System.out.printf(Locale.ROOT, "%-9s %3d users : moy=%.1f ms p95=%.1f ms debit=%.0f req/s err=%.1f%% cpu=%.0f%% ram=%.0f Mo%n",
                        r.methode, r.users, r.moyMs, r.p95Ms, r.debit, r.erreurs, r.cpuMoy * 100, r.ramMb);
            }
        }
        StringBuilder md = new StringBuilder();
        md.append("# Resultats de performance - decouverte : ").append(label).append("\n\n");
        md.append("Date : ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
          .append(" - service-voiture : delai simule 20 ms - endpoint /api/clients/1/car/{methode}\n\n");
        md.append("| Methode | Utilisateurs | Requetes | Moyenne (ms) | P95 (ms) | Debit (req/s) | Erreurs | CPU moy / max (service-client) | RAM (Mo) |\n");
        md.append("|---|---|---|---|---|---|---|---|---|\n");
        for (Mesure r : res) {
            md.append(String.format(Locale.ROOT, "| %s | %d | %d | %.1f | %.1f | %.0f | %.1f %% | %.0f %% / %.0f %% | %.0f |%n",
                    r.methode, r.users, r.requetes, r.moyMs, r.p95Ms, r.debit, r.erreurs, r.cpuMoy * 100, r.cpuMax * 100, r.ramMb));
        }
        var f = Common.resultsDir().resolve("perf-" + label + ".md");
        Files.writeString(f, md.toString());
        System.out.println("\nResultats ecrits dans " + f.toAbsolutePath());
    }

    static Mesure run(String methode, int users, int total) throws Exception {
        String path = "/api/clients/1/car/" + methode;
        ExecutorService pool = Executors.newFixedThreadPool(users);
        long[] lat = new long[total];
        AtomicInteger idx = new AtomicInteger(), err = new AtomicInteger();

        // echantillonnage CPU / RAM du service-client pendant le test (Actuator metrics)
        List<double[]> echantillons = Collections.synchronizedList(new ArrayList<>());
        ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor();
        sampler.scheduleAtFixedRate(() -> echantillons.add(new double[]{
                Common.metric(Common.BASE, "process.cpu.usage"),
                Common.metric(Common.BASE, "jvm.memory.used") / (1024 * 1024)}), 0, 250, TimeUnit.MILLISECONDS);

        CountDownLatch done = new CountDownLatch(total);
        long t0 = System.nanoTime();
        for (int i = 0; i < total; i++) {
            pool.execute(() -> {
                long s = System.nanoTime();
                int code = Common.get(path);
                long d = System.nanoTime() - s;
                if (code != 200) err.incrementAndGet();
                lat[idx.getAndIncrement()] = d;
                done.countDown();
            });
        }
        done.await();
        double dureeS = (System.nanoTime() - t0) / 1e9;
        pool.shutdown();
        sampler.shutdownNow();

        long[] tri = Arrays.copyOf(lat, total);
        Arrays.sort(tri);
        double moy = Arrays.stream(tri).average().orElse(0) / 1e6;
        double p95 = tri[(int) Math.ceil(total * 0.95) - 1] / 1e6;
        double cpuMoy = echantillons.stream().mapToDouble(a -> a[0]).filter(v -> !Double.isNaN(v)).average().orElse(Double.NaN);
        double cpuMax = echantillons.stream().mapToDouble(a -> a[0]).filter(v -> !Double.isNaN(v)).max().orElse(Double.NaN);
        double ram = echantillons.stream().mapToDouble(a -> a[1]).filter(v -> !Double.isNaN(v)).average().orElse(Double.NaN);
        return new Mesure(methode, users, total, moy, p95, total / dureeS, 100.0 * err.get() / total, cpuMoy, cpuMax, ram);
    }
}
