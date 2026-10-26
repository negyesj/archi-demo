package hu.dmsone.archi.platform.web.client;

import hu.dmsone.archi.platform.web.security.CurrentUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/** A szolgáltatások közötti szinkron hívásban továbbadja a felhasználó tokenjét (jogosultság a hívott oldalon is). */
public class TokenRelayInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String token = CurrentUser.bearerToken();
        if (token != null && !request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            request.getHeaders().setBearerAuth(token);
        }
        return execution.execute(request, body);
    }
}
