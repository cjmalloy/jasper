package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.domain.Ref;
import jasper.plugin.Origin;
import jasper.plugin.Tunnel;
import jasper.repository.UserRepository;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class TunnelClient {
    private static final Logger logger = LoggerFactory.getLogger(TunnelClient.class);

    @Autowired
    UserRepository userRepository;

    @Autowired
    ObjectMapper objectMapper;

    public void proxy(Ref remote, ProxyRequest request) {
        var config = objectMapper.convertValue(remote.getPlugins().get("+plugin/origin"), Origin.class);
        URI url = null;
        try {
            url = new URI(isNotBlank(config.getProxy()) ? config.getProxy() : remote.getUrl());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        var tunnel = objectMapper.convertValue(remote.getPlugins().get("+plugin/origin/tunnel"), Tunnel.class);
        if (tunnel == null) {
            request.go(url);
        } else {
            var user = userRepository.findOneByQualifiedTag(tunnel.getUser());
            if (user.isEmpty() || user.get().getKey() == null) {
                logger.error("Tunnel requested, but user {} does not have a private key set.", tunnel.getUser());
                return;
            }
            try (var client = SshClient.setUpDefaultClient()) {
                client.start();
                var host = isNotBlank(tunnel.getSshHost()) ? tunnel.getSshHost() : url.getHost();
                try (var session = client.connect("root", host, 22)
                        .verify(30, TimeUnit.SECONDS).getSession()) {
                    var pks = SecurityUtils.loadKeyPairIdentities(null, NamedResource.ofName("root"), new ByteArrayInputStream(user.get().getKey()), null);
                    pks.forEach(session::addPublicKeyIdentity);
                    session.auth().verify(30, TimeUnit.SECONDS);
                    try (var tracker = session.createLocalPortForwardingTracker(38080, new SshdSocketAddress("localhost", 8022))) {
						logger.debug("Opened reverse proxy in SSH tunnel.");
                        request.go(new URI("http://localhost:38080"));
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                } catch (IOException | GeneralSecurityException e) {
                    throw new RuntimeException(e);
                } finally {
                    client.stop();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public interface ProxyRequest {
        void go(URI url);
    }
}
