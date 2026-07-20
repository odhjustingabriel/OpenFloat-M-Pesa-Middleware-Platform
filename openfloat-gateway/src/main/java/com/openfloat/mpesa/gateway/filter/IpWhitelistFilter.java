package com.openfloat.mpesa.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

@Slf4j
@Component
public class IpWhitelistFilter implements WebFilter {

    @Value("${openfloat.gateway.safaricom-ip-ranges}")
    private List<String> whitelistedRanges;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Apply whitelist checks only to M-Pesa callbacks
        if (path.startsWith("/api/v1/mpesa/callbacks")) {
            InetSocketAddress remoteAddress = request.getRemoteAddress();
            if (remoteAddress == null) {
                log.warn("Blocked callback request: remote address is unresolved");
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            String clientIp = remoteAddress.getAddress().getHostAddress();
            boolean isAllowed = checkIpAllowed(clientIp);

            if (!isAllowed) {
                log.warn("Forbidden callback access attempt from IP: {} on path: {}", clientIp, path);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
            log.info("Allowed callback access from IP: {} on path: {}", clientIp, path);
        }

        return chain.filter(exchange);
    }

    private boolean checkIpAllowed(String ip) {
        for (String range : whitelistedRanges) {
            if (matchIp(ip, range)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchIp(String ip, String range) {
        if (range.contains("/")) {
            String[] parts = range.split("/");
            String subnet = parts[0];
            int mask = Integer.parseInt(parts[1]);
            
            return isInRange(ip, subnet, mask);
        } else {
            return ip.equals(range);
        }
    }

    private boolean isInRange(String ip, String subnet, int mask) {
        try {
            int ipInt = ipToInt(ip);
            int subnetInt = ipToInt(subnet);
            int maskInt = mask == 0 ? 0 : ~((1 << (32 - mask)) - 1);
            
            return (ipInt & maskInt) == (subnetInt & maskInt);
        } catch (Exception e) {
            log.error("Error matching IP: {} to subnet: {}/{}", ip, subnet, mask, e);
            return false;
        }
    }

    private int ipToInt(String ip) {
        String[] parts = ip.split("\\.");
        int ipInt = 0;
        for (int i = 0; i < 4; i++) {
            ipInt |= Integer.parseInt(parts[i]) << (24 - (8 * i));
        }
        return ipInt;
    }
}
