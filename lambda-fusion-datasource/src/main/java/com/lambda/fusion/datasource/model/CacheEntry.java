package com.lambda.fusion.datasource.model;

import com.lambda.fusion.core.FusionConstants;

public record CacheEntry(FusionConstants.IsolationMode mode, long expiresAt) {}
