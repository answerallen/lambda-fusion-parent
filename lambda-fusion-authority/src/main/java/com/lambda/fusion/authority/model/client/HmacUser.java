package com.lambda.fusion.authority.model.client;

import com.lambda.security.web.hmac.model.HmacClient;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HmacUser extends HmacClient {

    private List<String> authorities;

}
