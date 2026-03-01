package com.lambda.fusion.autoconfig;

import com.lambda.fusion.dict.DictConfigure;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * DictionaryConfigure
 *
 * @author Jin
 */
@AutoConfiguration
@Import(DictConfigure.class)
public class DictAutoConfiguration {}
