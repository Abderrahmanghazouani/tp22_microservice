package com.example.loadtest;

import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Partie F : charge constante pendant DUREE secondes sur les 3 endpoints. Pendant ce temps on arrete
 * puis redemarre un composant (service-voiture, Eureka/Consul ou service-client) depuis l'IDE.
 * Chaque seconde : nombre de succes / echecs par methode -> results/resilience-<label>-<HHmmss>.csv
 */
public class ResilienceTest {
    static final int DUREE = 150;          // secondes
    static final int THREADS_PAR_METHODE = 5;

    public static void main(String[] args) throws Exception {
        String label = Common.discoveryLabel();
        String[] methodes = {"rest", "feign", "webclient"};
        AtomicIntegerArray ok = new AtomicIntegerArray(DUREE * 3), ko = new AtomicIntegerArray(DUREE * 3);
        long t0 = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(methodes.length * THREADS_PAR_METHODE);
        for (int m = 0; m < methodes.length; m++) {
            final int mi = m;
            for (int t = 0; t < THREADS_PAR_METHODE; t++) {
                pool.execute(() -> {
                    while (true) {
                        int sec = (int) ((System.currentTimeMillis() - t0) / 1000);
                        if (sec >= DUREE) return;
                        int code = Common.get("/api/clients/1/car/" + methodes[mi]);
                        if (code == 200) ok.incrementAndGet(sec * 3 + mi); else ko.incrementAndGet(sec * 3 + mi);
                        if (code != 200) sleep(50);   // evite une boucle infinie tres rapide en cas d'echec immediat
                    }
                });
            }
        }
        for (int s = 0; s < DUREE; s += 10) {
            Thread.sleep(10_000);
            System.out.println("t=" + (s + 10) + "s ...");
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        StringBuilder csv = new StringBuilder("seconde,rest_ok,rest_ko,feign_ok,feign_ko,webclient_ok,webclient_ko\n");
        for (int s = 0; s < DUREE; s++) {
            csv.append(s);
            for (int m = 0; m < 3; m++) csv.append(',').append(ok.get(s * 3 + m)).append(',').append(ko.get(s * 3 + m));
            csv.append('\n');
        }
        String nom = "resilience-" + label + "-" + LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss")) + ".csv";
        var f = Common.resultsDir().resolve(nom);
        Files.writeString(f, csv.toString());
        System.out.println("Resultats ecrits dans " + f.toAbsolutePath());
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
