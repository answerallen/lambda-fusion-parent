package com.lambda.fusion.configs.domain.dto;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;

@SuppressFBWarnings("EI_EXPOSE_REP")
public class Query {

    String application;

    List<String> ids;

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }
}
