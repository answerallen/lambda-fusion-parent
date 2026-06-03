package com.lambda.fusion.authority.client.model;

import com.lambda.security.web.hmac.model.HmacClient;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HmacUser extends HmacClient {

    private List<String> authorities;
}
