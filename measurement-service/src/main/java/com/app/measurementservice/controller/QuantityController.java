package com.app.measurementservice.controller;

import com.app.measurementservice.client.UserServiceClient;
import com.app.measurementservice.domain.MeasurementCategory;
import com.app.measurementservice.domain.MeasurementOperation;
import com.app.measurementservice.dto.ConversionHistoryRequest;
import com.app.measurementservice.dto.HistoryItemResponse;
import com.app.measurementservice.dto.QuantityRequest;
import com.app.measurementservice.dto.QuantityResponse;
import com.app.measurementservice.entity.MeasurementRecord;
import com.app.measurementservice.repository.MeasurementRecordRepository;
import com.app.measurementservice.service.MeasurementEngine;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/api/v1/quantities")
public class QuantityController {

    private final MeasurementEngine engine;
    private final MeasurementRecordRepository repository;
    private final UserServiceClient userServiceClient;

    public QuantityController(MeasurementEngine engine, MeasurementRecordRepository repository, UserServiceClient userServiceClient) {
        this.engine = engine;
        this.repository = repository;
        this.userServiceClient = userServiceClient;
    }

    @PostMapping("/compare")
    public ResponseEntity<QuantityResponse> compare(@Valid @RequestBody QuantityRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(execute("compare", request, MeasurementOperation.COMPARE, null, http));
    }

    @PostMapping("/convert")
    public ResponseEntity<QuantityResponse> convert(@Valid @RequestBody QuantityRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(execute("convert", request, MeasurementOperation.CONVERT, null, http));
    }

    @PostMapping("/add")
    public ResponseEntity<QuantityResponse> add(@Valid @RequestBody QuantityRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(execute("add", request, MeasurementOperation.ADD, request.thatQuantityDTO().unit(), http));
    }

    @PostMapping("/subtract")
    public ResponseEntity<QuantityResponse> subtract(@Valid @RequestBody QuantityRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(execute("subtract", request, MeasurementOperation.SUBTRACT, request.thatQuantityDTO().unit(), http));
    }

    @PostMapping("/divide")
    public ResponseEntity<QuantityResponse> divide(@Valid @RequestBody QuantityRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(execute("divide", request, MeasurementOperation.DIVIDE, null, http));
    }

    @GetMapping("/my/history")
    public ResponseEntity<List<HistoryItemResponse>> myHistory(HttpServletRequest http) {
        Long userId = extractUserId(http);
        if (userId == null) return ResponseEntity.status(401).build();
        List<MeasurementRecord> records = repository.findByUserIdOrderByRecordedAtDesc(userId);
        return ResponseEntity.ok(records.stream().map(this::toHistoryItem).toList());
    }

    @GetMapping("/my/history/errored")
    public ResponseEntity<List<HistoryItemResponse>> myHistoryErrored(HttpServletRequest http) {
        Long userId = extractUserId(http);
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(repository.findByUserIdOrderByRecordedAtDesc(userId).stream()
                .filter(r -> r.getMessage() != null && r.getMessage().toLowerCase().contains("error"))
                .map(this::toHistoryItem).toList());
    }

    @GetMapping("/my/history/operation/{operation}")
    public ResponseEntity<List<HistoryItemResponse>> myHistoryByOperation(
            @PathVariable String operation, HttpServletRequest http) {
        Long userId = extractUserId(http);
        if (userId == null) return ResponseEntity.status(401).build();
        MeasurementOperation op = parseOperation(operation);
        return ResponseEntity.ok(repository.findByUserIdOrderByRecordedAtDesc(userId).stream()
                .filter(r -> op == null || op == r.getOperation())
                .map(this::toHistoryItem).toList());
    }

    @GetMapping("/my/history/type/{type}")
    public ResponseEntity<List<HistoryItemResponse>> myHistoryByType(
            @PathVariable String type, HttpServletRequest http) {
        Long userId = extractUserId(http);
        if (userId == null) return ResponseEntity.status(401).build();
        MeasurementCategory category = parseCategory(type);
        return ResponseEntity.ok(repository.findByUserIdOrderByRecordedAtDesc(userId).stream()
                .filter(r -> category == null || category == r.getCategory())
                .map(this::toHistoryItem).toList());
    }

    // --- helpers ---

    private QuantityResponse execute(String opName, QuantityRequest request, MeasurementOperation operation, String resultUnit, HttpServletRequest http) {
        QuantityRequest.QuantityDTO left = request.thisQuantityDTO();
        QuantityRequest.QuantityDTO right = request.thatQuantityDTO();
        MeasurementCategory category = resolveCategory(left.measurementType());
        Long userId = extractUserId(http);

        try {
            consumeCreditIfNeeded(userId);
            QuantityResponse response;
            if (operation == MeasurementOperation.CONVERT) {
                double result = engine.convert(category, left.value(), left.unit(), right.unit());
                response = QuantityResponse.ofNumeric("convert", result, right.unit(), "Conversion completed");
                MeasurementRecord savedRecord = saveRecord(userId, category, MeasurementOperation.CONVERT,
                        left.value(), left.unit(), right.value(), right.unit(),
                        result, right.unit(), null, "Conversion completed");
                syncUserHistory(savedRecord);
            } else if (operation == MeasurementOperation.COMPARE) {
                MeasurementEngine.ComputationResult result = engine.compute(
                        category, MeasurementOperation.COMPARE,
                        left.value(), left.unit(), right.value(), right.unit(), null);
                response = QuantityResponse.ofComparison(Boolean.TRUE.equals(result.comparisonResult()));
                MeasurementRecord savedRecord = saveRecord(userId, category, MeasurementOperation.COMPARE,
                        left.value(), left.unit(), right.value(), right.unit(),
                        null, null, result.comparisonResult(), "Comparison completed");
                syncUserHistory(savedRecord);
            } else {
                String targetUnit = resultUnit != null ? resultUnit : left.unit();
                MeasurementEngine.ComputationResult result = engine.compute(
                        category, operation,
                        left.value(), left.unit(), right.value(), right.unit(), targetUnit);
                response = QuantityResponse.ofNumeric(opName, result.resultValue(), result.resultUnit(), result.message());
                MeasurementRecord savedRecord = saveRecord(userId, category, operation,
                        left.value(), left.unit(), right.value(), right.unit(),
                        result.resultValue(), result.resultUnit(), null, result.message());
                syncUserHistory(savedRecord);
            }
            return response;
        } catch (IllegalStateException e) {
            saveRecord(userId, category, operation,
                    left.value(), left.unit(), right.value(), right.unit(),
                    null, null, null, "ERROR: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            saveRecord(userId, category, operation,
                    left.value(), left.unit(), right.value(), right.unit(),
                    null, null, null, "ERROR: " + e.getMessage());
            return QuantityResponse.ofError(opName, e.getMessage());
        }
    }

    private MeasurementRecord saveRecord(Long userId, MeasurementCategory category, MeasurementOperation operation,
                                         double inputValue, String inputUnit, double secondaryValue, String secondaryUnit,
                                         Double resultValue, String resultUnit, Boolean comparisonResult, String message) {
        MeasurementRecord record = new MeasurementRecord();
        record.setUserId(userId);
        record.setCategory(category);
        record.setOperation(operation);
        record.setInputValue(inputValue);
        record.setInputUnit(inputUnit.toUpperCase());
        record.setSecondaryValue(secondaryValue);
        record.setSecondaryUnit(secondaryUnit.toUpperCase());
        record.setResultValue(resultValue);
        record.setResultUnit(resultUnit != null ? resultUnit.toUpperCase() : null);
        record.setComparisonResult(comparisonResult);
        record.setMessage(message);
        record.setRecordedAt(Instant.now());
        return repository.save(record);
    }

    private void consumeCreditIfNeeded(Long userId) {
        if (userId == null) {
            return;
        }

        try {
            userServiceClient.deductCredit(userId);
        } catch (FeignException exception) {
            if (exception.status() == 402) {
                throw new IllegalStateException("No credits remaining. Please recharge to continue.");
            }
            throw exception;
        }
    }

    private void syncUserHistory(MeasurementRecord record) {
        if (record.getUserId() == null) {
            return;
        }

        try {
            userServiceClient.saveHistory(record.getUserId(), new ConversionHistoryRequest(
                    record.getCategory(),
                    record.getOperation(),
                    record.getInputValue(),
                    record.getInputUnit(),
                    record.getSecondaryValue(),
                    record.getSecondaryUnit(),
                    record.getResultValue(),
                    record.getResultUnit(),
                    record.getComparisonResult(),
                    record.getMessage()
            ));
        } catch (Exception ignored) {
        }
    }

    private HistoryItemResponse toHistoryItem(MeasurementRecord r) {
        boolean isError = r.getMessage() != null && r.getMessage().startsWith("ERROR:");
        String opName = r.getOperation() != null ? r.getOperation().name().toLowerCase() : "unknown";
        String categoryName = r.getCategory() != null ? r.getCategory().name() : "UNKNOWN";
        String resultString = r.getOperation() == MeasurementOperation.COMPARE
                ? String.valueOf(Boolean.TRUE.equals(r.getComparisonResult())) : null;
        return new HistoryItemResponse(
                r.getInputValue() != null ? r.getInputValue() : 0,
                r.getInputUnit() != null ? r.getInputUnit() : "",
                categoryName,
                r.getSecondaryValue() != null ? r.getSecondaryValue() : 0,
                r.getSecondaryUnit() != null ? r.getSecondaryUnit() : "",
                categoryName,
                opName,
                resultString,
                r.getResultValue(),
                r.getResultUnit(),
                categoryName,
                isError ? r.getMessage() : null,
                isError
        );
    }

    private MeasurementCategory resolveCategory(String measurementType) {
        if (measurementType == null) throw new IllegalArgumentException("measurementType is required");
        return switch (measurementType.toUpperCase()) {
            case "LENGTHUNIT", "LENGTH" -> MeasurementCategory.LENGTH;
            case "WEIGHTUNIT", "WEIGHT" -> MeasurementCategory.WEIGHT;
            case "TEMPERATUREUNIT", "TEMPERATURE" -> MeasurementCategory.TEMPERATURE;
            case "VOLUMEUNIT", "VOLUME" -> MeasurementCategory.VOLUME;
            default -> throw new IllegalArgumentException("Unknown measurementType: " + measurementType);
        };
    }

    private MeasurementOperation parseOperation(String op) {
        try { return MeasurementOperation.valueOf(op.toUpperCase()); } catch (Exception e) { return null; }
    }

    private MeasurementCategory parseCategory(String type) {
        try { return resolveCategory(type); } catch (Exception e) { return null; }
    }

    /** Extracts userId claim from the JWT Bearer token without full verification. */
    private Long extractUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        try {
            String[] parts = auth.substring(7).split("\\.");
            if (parts.length < 2) return null;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            // parse "userId": <number>
            int idx = payload.indexOf("\"userId\"");
            if (idx < 0) return null;
            int colon = payload.indexOf(':', idx);
            int comma = payload.indexOf(',', colon);
            int brace = payload.indexOf('}', colon);
            int end = (comma > 0 && comma < brace) ? comma : brace;
            return Long.parseLong(payload.substring(colon + 1, end).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
