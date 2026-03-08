package com.lambda.fusion.autoconfig;

import com.lambda.fusion.upload.UploadConfigure;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(UploadConfigure.class)
public class UploadAutoConfiguration {}
