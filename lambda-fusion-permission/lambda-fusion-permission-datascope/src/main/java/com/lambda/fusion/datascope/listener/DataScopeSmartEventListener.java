package com.lambda.fusion.datascope.listener;

import com.lambda.fusion.datascope.DataScopeProperties;
import com.lambda.fusion.datascope.event.DataScopeObjectChangedEvent;
import com.lambda.fusion.datascope.service.DataScopeSmartService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@SuppressFBWarnings("EI_EXPOSE_REP")
public class DataScopeSmartEventListener {

    private final DataScopeSmartService dataScopeSmartService;
    private final DataScopeProperties dataScopeProperties;

    public DataScopeSmartEventListener(
            DataScopeSmartService dataScopeSmartService, DataScopeProperties dataScopeProperties) {
        this.dataScopeSmartService = dataScopeSmartService;
        this.dataScopeProperties = dataScopeProperties;
    }

    @EventListener
    public void onObjectChanged(DataScopeObjectChangedEvent event) {
        Integer[] types = dataScopeProperties.getSmartTypesByBusiness(event.getBusinessKey());
        if (ArrayUtils.isEmpty(types)) {
            return;
        }
        for (Integer type : types) {
            if (type == null) {
                continue;
            }
            switch (event.getChangeType()) {
                case CREATED ->
                    dataScopeSmartService.smartForCreated(
                            type, event.getObjectId(), event.getParentId(), event.getOperator());
                case UPDATED ->
                    dataScopeSmartService.smartForUpdated(
                            type,
                            event.getObjectId(),
                            event.getParentId(),
                            event.getPreviousParentId(),
                            event.getOperator());
                case DELETED -> dataScopeSmartService.smartForDeleted(type, event.getObjectId());
                case MOVED ->
                    dataScopeSmartService.smartForMoved(
                            type,
                            event.getObjectId(),
                            event.getParentId(),
                            event.getPreviousParentId(),
                            event.getCascadeObjectIds(),
                            event.getOperator());
                default -> {}
            }
        }
    }
}
