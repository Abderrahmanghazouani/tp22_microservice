package com.example.voiture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@EnableDiscoveryClient
@RestController
@RequestMapping("/api/cars")
public class ServiceVoitureApplication {

    // Petit JSON (< 1 Ko) : la BD n'est pas utile ici et fausserait la latence (remarque A1.2)
    public record Car(Long id, String marque, String modele, Long clientId) {}

    @Value("${voiture.delay-ms:20}")
    private long delayMs;

    public static void main(String[] args) {
        SpringApplication.run(ServiceVoitureApplication.class, args);
    }

    // GET /api/cars/byClient/{clientId}
    @GetMapping("/byClient/{clientId}")
    public Car byClient(@PathVariable Long clientId) throws InterruptedException {
        Thread.sleep(delayMs);   // traitement simule (20 ms) -> differences plus visibles (A1.3)
        return new Car(10L, "Toyota", "Yaris", clientId);
    }
}
