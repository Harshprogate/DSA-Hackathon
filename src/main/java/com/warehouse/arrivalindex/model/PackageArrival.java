package com.warehouse.arrivalindex.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Payload for registering a single package arrival")
public class PackageArrival {

    @Schema(description = "Unique package ID (nonnegative, maximum 1,000,000,000)", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "packageId cannot be null")
    @Min(value = 0, message = "packageId cannot be negative")
    @Max(value = 1_000_000_000L, message = "packageId cannot exceed 1,000,000,000")
    private Long packageId;

    public PackageArrival() {
    }

    public PackageArrival(Long packageId) {
        this.packageId = packageId;
    }

    public Long getPackageId() {
        return packageId;
    }

    public void setPackageId(Long packageId) {
        this.packageId = packageId;
    }
}
