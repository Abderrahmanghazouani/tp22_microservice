package com.example.client.model;

// DTO du JSON renvoye par SERVICE-VOITURE
public record Car(Long id, String marque, String modele, Long clientId) {}
