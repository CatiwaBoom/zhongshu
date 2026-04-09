package org.cycle.seatunnel.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cycle.seatunnel.config.SeatunnelProperties;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatunnelRestClient {

    private final SeatunnelProperties properties;
    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    public JsonNode submitJob(String config, String format, String jobId, String jobName) {
        String base = normalizeBaseUrl();
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(base + "/submit-job");
        String f = safeTrim(format);
        if (!f.isEmpty()) {
            builder.queryParam("format", f);
        }
        String id = safeTrim(jobId);
        if (!id.isEmpty()) {
            builder.queryParam("jobId", id);
        }
        String name = safeTrim(jobName);
        if (!name.isEmpty()) {
            builder.queryParam("jobName", name);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> entity = new HttpEntity<>(config == null ? "" : config, headers);
        ResponseEntity<String> response = restTemplate.exchange(builder.toUriString(), HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("submit-job failed, httpStatus=" + response.getStatusCodeValue());
        }
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("submit-job parse response failed: " + e.getMessage(), e);
        }
    }

    public JsonNode submitJobs(JsonNode jobsArray) {
        String url = normalizeBaseUrl() + "/submit-jobs";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(jobsArray.toString(), headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("submit-jobs failed, httpStatus=" + response.getStatusCodeValue());
        }
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("submit-jobs parse response failed: " + e.getMessage(), e);
        }
    }

    public JsonNode getJobInfo(String jobId) {
        String url = normalizeBaseUrl() + "/job-info/" + jobId;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("job-info failed, httpStatus=" + response.getStatusCodeValue());
        }
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("job-info parse response failed: " + e.getMessage(), e);
        }
    }

    public void stopJob(String jobId, boolean stopWithSavePoint) {
        String url = normalizeBaseUrl() + "/stop-job";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"jobId\":" + jobId + ",\"isStopWithSavePoint\":" + (stopWithSavePoint ? "true" : "false") + "}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("stop-job failed, httpStatus=" + response.getStatusCodeValue());
        }
    }

    private String normalizeBaseUrl() {
        String base = safeTrim(properties.getRestBaseUrl());
        if (base.isEmpty()) {
            base = "http://localhost:8080";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}

