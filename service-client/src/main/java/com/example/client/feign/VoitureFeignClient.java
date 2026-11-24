package com.example.client.feign;

import com.example.client.model.Car;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// B2.2 : client DECLARATIF - Spring genere l'implementation (nom logique + mapping)
@FeignClient(name = "SERVICE-VOITURE")
public interface VoitureFeignClient {
    @GetMapping("/api/cars/byClient/{clientId}")
    Car byClient(@PathVariable("clientId") Long clientId);
}
