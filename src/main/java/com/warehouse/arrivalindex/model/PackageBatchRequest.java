package com.warehouse.arrivalindex.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Payload for registering a batch of package arrivals")
public class PackageBatchRequest {

    @Schema(description = "List of package IDs to register sequentially", example = "[1, 3, 7, 2, 6]", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "packageIds list cannot be null")
    @NotEmpty(message = "packageIds list cannot be empty")
    private List<Long> packageIds;

    public PackageBatchRequest() {
    }

    public PackageBatchRequest(List<Long> packageIds) {
        this.packageIds = packageIds;
    }

    public List<Long> getPackageIds() {
        return packageIds;
    }

    public void setPackageIds(List<Long> packageIds) {
        this.packageIds = packageIds;
    }
}
