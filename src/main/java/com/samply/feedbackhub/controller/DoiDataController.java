package com.samply.feedbackhub.controller;
import com.macasaet.fernet.Key;
import com.macasaet.fernet.Token;
import com.samply.feedbackhub.exception.DoiDataAlreadyPresentException;
import com.samply.feedbackhub.model.DoiData;
import com.samply.feedbackhub.repository.DoiDataRepository;
import com.samply.feedbackhub.exception.DoiDataNotFoundException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpHeaders;
import java.net.http.HttpEntity;
import java.util.List;

@RestController
public class DoiDataController {
    @Autowired
    DoiDataRepository doiDataRepository;

    // Get all DoiData
    // only for testing
    @CrossOrigin(origins = "http://localhost:9000")
    @GetMapping("/doi-data")
    public List<DoiData> getAllDoiData() {
        return doiDataRepository.findAll();
    }

    // Create a new DoiData
    @CrossOrigin(origins = "http://localhost:9000")
    @PostMapping("/doi-data")
    public DoiData createDoiData(@Valid @RequestBody DoiData doi_data) throws DoiDataAlreadyPresentException {
        if (doiDataRepository.findByRequest(doi_data.getRequestID()).size() > 0) {
            throw new DoiDataAlreadyPresentException(doi_data.getRequestID());
        };
        final Key key = Key.generateKey();
        doi_data.setSymEncKey(key.serialise());

        DoiData returnData = doiDataRepository.save(doi_data); //shouldn't return key

        JSONObject beamProxyTask = new JSONObject();

        beamProxyTask.put("id", String.valueOf(doi_data.getId()));
        beamProxyTask.put("from", "app1.proxy1.broker");
        JSONArray toArray = new JSONArray();
        //here add all proxies plausible for DOI addition
        toArray.add("app1.proxy2.broker");
        beamProxyTask.put("to", toArray);

        JSONObject bodyObject = new JSONObject();
        bodyObject.put("key", doi_data.getSymEncKey());
        bodyObject.put("request_id", doi_data.getRequestID());
        beamProxyTask.put("body", bodyObject);

        JSONObject failureStrategyObject = new JSONObject();
        JSONObject retryObject = new JSONObject();
        retryObject.put("backoff_millisecs", 1000);
        retryObject.put("max_tries", 5);
        failureStrategyObject.put("retry", retryObject);
        beamProxyTask.put("failure_strategy", failureStrategyObject);

        beamProxyTask.put("ttl", "30s");
        beamProxyTask.put("metadata", null);

        // To print in JSON format.
        System.out.print(beamProxyTask);

        final String uri = "http://localhost:8081/v1/tasks";
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "ApiKey app1.proxy1.broker App1Secret");

        HttpEntity<JSONObject> request = new HttpEntity<>(beamProxyTask, headers);
        JSONObject result = restTemplate.postForObject(uri, request, JSONObject.class);
        System.out.println(result);

        returnData.setSymEncKey("hidden");
        return returnData;
    }

    // Get a Single DoiData
    /*@CrossOrigin(origins = "http://localhost:9000")
    @GetMapping("/specimen-feedback/{id}")
    public DoiData getDoiDataById(@PathVariable(value = "id") Long specimenFeedbackId) throws DoiDataNotFoundException {
        return doiDataRepository.findById(specimenFeedbackId)
                .orElseThrow(() -> new DoiDataNotFoundException(specimenFeedbackId));
    }*/

    // Get SpecimenFeedbacks by request ID
    @CrossOrigin(origins = "http://localhost:9000")
    @GetMapping("/doi-token/{req_id}")
    public String getDoiTokenByRequestID(@PathVariable(value = "req_id") String requestId) throws DoiDataNotFoundException {
        List<DoiData> data = doiDataRepository.findByRequest(requestId);
        if (data.size() == 0) throw new DoiDataNotFoundException(requestId);
        return Token.generate(new Key(data.get(0).getSymEncKey()), data.get(0).getPublicationReference()).serialise();
    }
}