package com.example.client.web;

import com.example.client.feign.VoitureFeignClient;
import com.example.client.model.Car;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clients")
public class ClientController {
    private static final String URL = "http://SERVICE-VOITURE/api/cars/byClient/";

    private final RestTemplate restTemplate;
    private final VoitureFeignClient feign;
    private final WebClient webClient;
    private final DiscoveryClient discoveryClient;

    public ClientController(RestTemplate restTemplate, VoitureFeignClient feign,
                            WebClient.Builder builder, DiscoveryClient discoveryClient) {
        this.restTemplate = restTemplate;
        this.feign = feign;
        this.webClient = builder.build();
        this.discoveryClient = discoveryClient;
    }

    // B1 : RestTemplate (synchrone classique)
    @GetMapping("/{id}/car/rest")
    public Car viaRestTemplate(@PathVariable Long id) {
        return restTemplate.getForObject(URL + id, Car.class);
    }

    // B2 : Feign (declaratif)
    @GetMapping("/{id}/car/feign")
    public Car viaFeign(@PathVariable Long id) {
        return feign.byClient(id);
    }

    // B3 : WebClient utilise en synchrone (block) pour comparer a armes egales
    @GetMapping("/{id}/car/webclient")
    public Car viaWebClient(@PathVariable Long id) {
        return webClient.get().uri(URL + id).retrieve().bodyToMono(Car.class).block();
    }

    // Diagnostic : quel registre est utilise et ou se trouve SERVICE-VOITURE
    @GetMapping("/discovery")
    public Map<String, Object> discovery() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("discoveryClients", discoveryClient instanceof CompositeDiscoveryClient c
                ? c.getDiscoveryClients().stream().map(DiscoveryClient::description).toList()
                : List.of(discoveryClient.description()));
        List<ServiceInstance> inst = discoveryClient.getInstances("SERVICE-VOITURE");
        m.put("SERVICE-VOITURE", inst.stream().map(i -> i.getHost() + ":" + i.getPort()).toList());
        m.put("services", discoveryClient.getServices());
        return m;
    }
}
