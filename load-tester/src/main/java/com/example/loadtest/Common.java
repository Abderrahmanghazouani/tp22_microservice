package com.example.loadtest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Outils communs : client HTTP, lecture des metriques Actuator, dossier de resultats
final class Common {
    static final String BASE = "http://localhost:8081";
    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    static int get(String path) {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(10)).GET().build();
            return HTTP.send(r, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) {
            return -1;   // erreur reseau (connexion refusee, timeout...)
        }
    }

    static String getBody(String url) {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
            return HTTP.send(r, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            return "";
        }
    }

    // /actuator/metrics/{name} -> premiere "value" (ex : process.cpu.usage = 0.12)
    static double metric(String base, String name) {
        Matcher m = Pattern.compile("\"value\"\\s*:\\s*([0-9.Ee+-]+)").matcher(getBody(base + "/actuator/metrics/" + name));
        return m.find() ? Double.parseDouble(m.group(1)) : Double.NaN;
    }

    static String discoveryLabel() {
        String d = getBody(BASE + "/api/clients/discovery").toLowerCase();
        if (d.contains("consul")) return "consul";
        if (d.contains("eureka")) return "eureka";
        return "inconnu";
    }

    static Path resultsDir() throws IOException {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path dir = cwd.getFileName() != null && cwd.getFileName().toString().equals("load-tester")
                ? cwd.resolveSibling("results") : cwd.resolve("results");
        Files.createDirectories(dir);
        return dir;
    }
}
