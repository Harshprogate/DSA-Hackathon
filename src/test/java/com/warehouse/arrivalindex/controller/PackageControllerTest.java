package com.warehouse.arrivalindex.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.arrivalindex.dto.BatchResponse;
import com.warehouse.arrivalindex.dto.MissingIntervalsResponse;
import com.warehouse.arrivalindex.dto.RangeIntervalsResponse;
import com.warehouse.arrivalindex.model.*;
import com.warehouse.arrivalindex.service.PackageIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PackageController.class)
@DisplayName("PackageController REST API Tests")
class PackageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PackageIndexService packageIndexService;

    @Test
    @DisplayName("POST /api/packages - New package registration returns 201 Created")
    void testRegisterPackageSuccess() throws Exception {
        PackageResponse mockResponse = new PackageResponse(
                4L,
                "ADDED",
                "Package successfully registered",
                List.of(new Interval(1, 8))
        );
        when(packageIndexService.addPackage(4L)).thenReturn(mockResponse);

        PackageArrival payload = new PackageArrival(4L);

        mockMvc.perform(post("/api/packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.packageId").value(4))
                .andExpect(jsonPath("$.status").value("ADDED"))
                .andExpect(jsonPath("$.message").value("Package successfully registered"))
                .andExpect(jsonPath("$.currentIntervals[0].start").value(1))
                .andExpect(jsonPath("$.currentIntervals[0].end").value(8));
    }

    @Test
    @DisplayName("POST /api/packages - Duplicate package returns 200 OK")
    void testRegisterPackageDuplicate() throws Exception {
        PackageResponse mockResponse = new PackageResponse(
                3L,
                "DUPLICATE",
                "Package ID already exists",
                List.of(new Interval(1, 8))
        );
        when(packageIndexService.addPackage(3L)).thenReturn(mockResponse);

        PackageArrival payload = new PackageArrival(3L);

        mockMvc.perform(post("/api/packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packageId").value(3))
                .andExpect(jsonPath("$.status").value("DUPLICATE"))
                .andExpect(jsonPath("$.message").value("Package ID already exists"));
    }

    @Test
    @DisplayName("POST /api/packages - Negative package ID returns 400 Bad Request")
    void testRegisterPackageNegativeId() throws Exception {
        PackageArrival payload = new PackageArrival(-5L);

        mockMvc.perform(post("/api/packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("POST /api/packages/batch - Register multiple packages returns 200 OK")
    void testRegisterBatch() throws Exception {
        BatchResponse batchResponse = new BatchResponse(
                5,
                4,
                1,
                List.of(new Interval(1, 3), new Interval(6, 7))
        );
        when(packageIndexService.addBatch(List.of(1L, 3L, 7L, 2L, 6L))).thenReturn(batchResponse);

        PackageBatchRequest payload = new PackageBatchRequest(List.of(1L, 3L, 7L, 2L, 6L));

        mockMvc.perform(post("/api/packages/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessed").value(5))
                .andExpect(jsonPath("$.newlyAdded").value(4))
                .andExpect(jsonPath("$.duplicates").value(1))
                .andExpect(jsonPath("$.currentIntervals.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/packages/intervals - Returns all intervals")
    void testGetAllIntervals() throws Exception {
        IntervalResponse response = new IntervalResponse(
                5,
                2,
                List.of(new Interval(1, 3), new Interval(6, 7))
        );
        when(packageIndexService.getAllIntervals()).thenReturn(response);

        mockMvc.perform(get("/api/packages/intervals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceived").value(5))
                .andExpect(jsonPath("$.intervalCount").value(2))
                .andExpect(jsonPath("$.intervals[0].start").value(1))
                .andExpect(jsonPath("$.intervals[0].end").value(3))
                .andExpect(jsonPath("$.intervals[1].start").value(6))
                .andExpect(jsonPath("$.intervals[1].end").value(7));
    }

    @Test
    @DisplayName("GET /api/packages/missing - Bounded query returns missing intervals")
    void testGetMissingIntervals() throws Exception {
        MissingIntervalsResponse response = new MissingIntervalsResponse(
                1,
                7,
                List.of(new Interval(4, 5))
        );
        when(packageIndexService.getMissingIntervals(1L, 7L)).thenReturn(response);

        mockMvc.perform(get("/api/packages/missing")
                        .param("start", "1")
                        .param("end", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rangeStart").value(1))
                .andExpect(jsonPath("$.rangeEnd").value(7))
                .andExpect(jsonPath("$.missingIntervals[0].start").value(4))
                .andExpect(jsonPath("$.missingIntervals[0].end").value(5));
    }

    @Test
    @DisplayName("GET /api/packages/intervals/range - Returns clipped overlapping intervals")
    void testGetRangeIntervals() throws Exception {
        RangeIntervalsResponse response = new RangeIntervalsResponse(
                3,
                10,
                List.of(new Interval(3, 5), new Interval(8, 10))
        );
        when(packageIndexService.getBoundedIntervals(3L, 10L)).thenReturn(response);

        mockMvc.perform(get("/api/packages/intervals/range")
                        .param("start", "3")
                        .param("end", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rangeStart").value(3))
                .andExpect(jsonPath("$.rangeEnd").value(10))
                .andExpect(jsonPath("$.intervals[0].start").value(3))
                .andExpect(jsonPath("$.intervals[0].end").value(5))
                .andExpect(jsonPath("$.intervals[1].start").value(8))
                .andExpect(jsonPath("$.intervals[1].end").value(10));
    }

    @Test
    @DisplayName("GET /api/packages/statistics - Returns system statistics")
    void testGetStatistics() throws Exception {
        StatisticsResponse response = new StatisticsResponse(5, 2, 1L, 7L);
        when(packageIndexService.getStatistics()).thenReturn(response);

        mockMvc.perform(get("/api/packages/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUniquePackages").value(5))
                .andExpect(jsonPath("$.intervalCount").value(2))
                .andExpect(jsonPath("$.smallestId").value(1))
                .andExpect(jsonPath("$.largestId").value(7));
    }

    @Test
    @DisplayName("GET /api/packages/missing - Non-numeric parameter returns 400 Bad Request")
    void testInvalidParameterType() throws Exception {
        mockMvc.perform(get("/api/packages/missing")
                        .param("start", "abc")
                        .param("end", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("POST /api/packages - Malformed JSON body returns 400 Bad Request")
    void testMalformedJson() throws Exception {
        mockMvc.perform(post("/api/packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
