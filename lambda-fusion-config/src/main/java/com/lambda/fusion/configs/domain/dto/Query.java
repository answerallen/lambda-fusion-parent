package com.lambda.fusion.configs.domain.dto;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@SuppressFBWarnings("EI_EXPOSE_REP")
public class Query {

    String application;

    List<String> ids;

}
