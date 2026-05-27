package com.fundtracker.controller;

import com.fundtracker.model.vo.ApiResponse;
import com.fundtracker.service.DataSyncService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    private final DataSyncService dataSyncService;

    public SyncController(DataSyncService dataSyncService) {
        this.dataSyncService = dataSyncService;
    }

    @PostMapping
    public ApiResponse<DataSyncService.SyncResult> sync() {
        DataSyncService.SyncResult result = dataSyncService.syncAll();
        return ApiResponse.success(result);
    }
}
