package com.BajajFin.webhookapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class WebhookService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void execute() {
        try {
            // 1. Call generateWebhook API
            String initUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";
            Map<String, Object> requestBody = Map.of(
                    "name", "ANVESHA",
                    "regNo", "RA2211031010053",  // Replace with your actual regNo
                    "email", "aa2498@srmist.edu.in"
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(initUrl, HttpMethod.POST, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            String webhookUrl = root.get("webhook").asText();
            String accessToken = root.get("accessToken").asText();
            JsonNode users = root.get("data").get("users");

            // 2. Solve "Mutual Followers"
            List<List<Integer>> outcome = findMutualFollowers(users);

            // 3. POST to webhook with JWT Authorization
            headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", accessToken);

            Map<String, Object> finalOutput = Map.of(
                    "regNo", "REG12345",
                    "outcome", outcome
            );

            HttpEntity<Map<String, Object>> webhookRequest = new HttpEntity<>(finalOutput, headers);

            int attempts = 0;
            boolean success = false;
            while (attempts < 4 && !success) {
                try {
                    ResponseEntity<String> webhookResp = restTemplate.postForEntity(webhookUrl, webhookRequest, String.class);
                    if (webhookResp.getStatusCode().is2xxSuccessful()) {
                        success = true;
                        System.out.println("Webhook posted successfully!");
                    }
                } catch (Exception e) {
                    attempts++;
                    Thread.sleep(1000); // wait a bit before retrying
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<List<Integer>> findMutualFollowers(JsonNode users) {
        Map<Integer, Set<Integer>> followsMap = new HashMap<>();
        for (JsonNode user : users) {
            int id = user.get("id").asInt();
            Set<Integer> follows = new HashSet<>();
            for (JsonNode followee : user.get("follows")) {
                follows.add(followee.asInt());
            }
            followsMap.put(id, follows);
        }

        Set<String> seen = new HashSet<>();
        List<List<Integer>> mutuals = new ArrayList<>();

        for (Map.Entry<Integer, Set<Integer>> entry : followsMap.entrySet()) {
            int u = entry.getKey();
            for (int v : entry.getValue()) {
                if (followsMap.containsKey(v) && followsMap.get(v).contains(u)) {
                    int min = Math.min(u, v);
                    int max = Math.max(u, v);
                    String key = min + ":" + max;
                    if (!seen.contains(key)) {
                        mutuals.add(Arrays.asList(min, max));
                        seen.add(key);
                    }
                }
            }
        }

        return mutuals;
    }
}
