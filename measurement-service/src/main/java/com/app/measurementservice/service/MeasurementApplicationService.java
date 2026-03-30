package com.app.measurementservice.service;

import com.app.measurementservice.client.UserServiceClient;
import com.app.measurementservice.domain.MeasurementOperation;
import com.app.measurementservice.dto.ComputationRequest;
import com.app.measurementservice.dto.ConversionHistoryRequest;
import com.app.measurementservice.dto.ConversionRequest;
import com.app.measurementservice.dto.MeasurementResponse;
import com.app.measurementservice.entity.MeasurementRecord;
import com.app.measurementservice.repository.MeasurementRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class MeasurementApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(MeasurementApplicationService.class);

    private final MeasurementEngine measurementEngine;
    private final MeasurementRecordRepository measurementRecordRepository;
    private final UserServiceClient userServiceClient;

    public MeasurementApplicationService(
            MeasurementEngine measurementEngine,
            MeasurementRecordRepository measurementRecordRepository,
            UserServiceClient userServiceClient
    ) {
        this.measurementEngine = measurementEngine;
        this.measurementRecordRepository = measurementRecordRepository;
        this.userServiceClient = userServiceClient;
    }

    public MeasurementResponse convert(ConversionRequest request) {
        MeasurementRecord record = new MeasurementRecord();
        record.setUserId(request.userId());
        record.setCategory(request.category());
        record.setOperation(MeasurementOperation.CONVERT);
        record.setInputValue(request.value());
        record.setInputUnit(request.fromUnit().toUpperCase());
        record.setResultValue(measurementEngine.convert(
                request.category(),
                request.value(),
                request.fromUnit(),
                request.toUnit()
        ));
        record.setResultUnit(request.toUnit().toUpperCase());
        record.setMessage("Conversion completed");
        record.setRecordedAt(Instant.now());
        MeasurementRecord savedRecord = measurementRecordRepository.save(record);
        syncUserHistory(savedRecord);
        return toResponse(savedRecord);
    }

    public MeasurementResponse compute(ComputationRequest request) {
        MeasurementEngine.ComputationResult result = measurementEngine.compute(
                request.category(),
                request.operation(),
                request.leftValue(),
                request.leftUnit(),
                request.rightValue(),
                request.rightUnit(),
                request.resultUnit()
        );

        MeasurementRecord record = new MeasurementRecord();
        record.setUserId(request.userId());
        record.setCategory(request.category());
        record.setOperation(request.operation());
        record.setInputValue(request.leftValue());
        record.setInputUnit(request.leftUnit().toUpperCase());
        record.setSecondaryValue(request.rightValue());
        record.setSecondaryUnit(request.rightUnit().toUpperCase());
        record.setResultValue(result.resultValue());
        record.setResultUnit(result.resultUnit());
        record.setComparisonResult(result.comparisonResult());
        record.setMessage(result.message());
        record.setRecordedAt(Instant.now());
        MeasurementRecord savedRecord = measurementRecordRepository.save(record);
        syncUserHistory(savedRecord);
        return toResponse(savedRecord);
    }

    public List<MeasurementResponse> history() {
        return measurementRecordRepository.findAll().stream().map(this::toResponse).toList();
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
        } catch (Exception exception) {
            logger.warn("user-service unavailable, skipping user history sync for user {}", record.getUserId());
        }
    }

    private MeasurementResponse toResponse(MeasurementRecord record) {
        return new MeasurementResponse(
                record.getId(),
                record.getUserId(),
                record.getCategory(),
                record.getOperation(),
                record.getInputValue(),
                record.getInputUnit(),
                record.getSecondaryValue(),
                record.getSecondaryUnit(),
                record.getResultValue(),
                record.getResultUnit(),
                record.getComparisonResult(),
                record.getMessage(),
                record.getRecordedAt()
        );
    }
}
