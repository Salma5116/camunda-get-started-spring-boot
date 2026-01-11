package org.camunda.bpm.getstarted.loanapproval;

import org.camunda.bpm.client.interceptor.ClientRequestContext;
import org.camunda.bpm.client.interceptor.ClientRequestInterceptor;

public class BasicAuthInterceptor implements ClientRequestInterceptor {

    private final String basicAuthHeader;

    public BasicAuthInterceptor(String basicAuthHeader) {
        this.basicAuthHeader = basicAuthHeader;
    }

    @Override
    public void intercept(ClientRequestContext ctx) {
        ctx.addHeader("Authorization", basicAuthHeader);
    }
}
