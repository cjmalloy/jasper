package jasper.security;

import jasper.component.ConfigCache;
import jasper.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HostCheckTest {

@Mock
ConfigCache configs;

@Mock
Config rootConfig;

HostCheck hostCheck;

@BeforeEach
void init() {
hostCheck = new HostCheck();
hostCheck.configs = configs;
when(configs.root()).thenReturn(rootConfig);
}

@Test
void testValidPublicHost() throws Exception {
when(rootConfig.getHostWhitelist()).thenReturn(null);
when(rootConfig.getHostBlacklist()).thenReturn(new ArrayList<>());

var uri = new URI("https://example.com");
var result = hostCheck.validHost(uri);

assertThat(result).isTrue();
}

@Test
void testInvalidLoopbackHost() throws Exception {
when(rootConfig.getHostWhitelist()).thenReturn(null);
when(rootConfig.getHostBlacklist()).thenReturn(new ArrayList<>());

var uri = new URI("http://localhost");
var result = hostCheck.validHost(uri);

assertThat(result).isFalse();
}

@Test
void testInvalidLoopbackIP() throws Exception {
when(rootConfig.getHostWhitelist()).thenReturn(null);
when(rootConfig.getHostBlacklist()).thenReturn(new ArrayList<>());

var uri = new URI("http://127.0.0.1");
var result = hostCheck.validHost(uri);

assertThat(result).isFalse();
}

@Test
void testInvalidPrivateNetwork() throws Exception {
when(rootConfig.getHostWhitelist()).thenReturn(null);
when(rootConfig.getHostBlacklist()).thenReturn(new ArrayList<>());

var uri = new URI("http://192.168.1.1");
var result = hostCheck.validHost(uri);

assertThat(result).isFalse();
}

@Test
void testWhitelistAllowsHost() throws Exception {
when(rootConfig.getHostWhitelist()).thenReturn(List.of("example.com", "trusted.com"));
when(rootConfig.getHostBlacklist()).thenReturn(new ArrayList<>());

var uri = new URI("https://example.com");
var result = hostCheck.validHost(uri);

assertThat(result).isTrue();
}

@Test
void testWhitelistBlocksUnlistedHost() throws Exception {
when(rootConfig.getHostWhitelist()).thenReturn(List.of("trusted.com"));
when(rootConfig.getHostBlacklist()).thenReturn(new ArrayList<>());

var uri = new URI("https://example.com");
var result = hostCheck.validHost(uri);

assertThat(result).isFalse();
}

@Test
void testWhitelistAllowsLocalhost() throws Exception {
when(rootConfig.getHostWhitelist()).thenReturn(List.of("localhost"));
when(rootConfig.getHostBlacklist()).thenReturn(new ArrayList<>());

var uri = new URI("http://localhost");
var result = hostCheck.validHost(uri);

assertThat(result).isTrue();
}

@Test
void testBlacklistBlocksHost() throws Exception {
when(rootConfig.getHostWhitelist()).thenReturn(null);
when(rootConfig.getHostBlacklist()).thenReturn(List.of("blocked.com"));

var uri = new URI("https://blocked.com");
var result = hostCheck.validHost(uri);

assertThat(result).isFalse();
}

@Test
void testBlacklistAllowsOtherHosts() throws Exception {
when(rootConfig.getHostWhitelist()).thenReturn(null);
when(rootConfig.getHostBlacklist()).thenReturn(List.of("blocked.com"));

var uri = new URI("https://example.com");
var result = hostCheck.validHost(uri);

assertThat(result).isTrue();
}

@Test
void testBlacklistTakesPrecedenceOverWhitelist() throws Exception {
when(rootConfig.getHostWhitelist()).thenReturn(List.of("example.com"));
when(rootConfig.getHostBlacklist()).thenReturn(List.of("example.com"));

var uri = new URI("https://example.com");
var result = hostCheck.validHost(uri);

assertThat(result).isFalse();
}

@Test
void testInvalidHostname() throws Exception {
when(rootConfig.getHostWhitelist()).thenReturn(null);
when(rootConfig.getHostBlacklist()).thenReturn(new ArrayList<>());

var uri = new URI("https://invalid..hostname");
var result = hostCheck.validHost(uri);

assertThat(result).isFalse();
}

@Test
void testEmptyWhitelist() throws Exception {
when(rootConfig.getHostWhitelist()).thenReturn(new ArrayList<>());
when(rootConfig.getHostBlacklist()).thenReturn(new ArrayList<>());

var uri = new URI("https://example.com");
var result = hostCheck.validHost(uri);

assertThat(result).isTrue();
}

@Test
void testMultipleHostsInWhitelist() throws Exception {
when(rootConfig.getHostWhitelist()).thenReturn(List.of("host1.com", "host2.com", "host3.com"));
when(rootConfig.getHostBlacklist()).thenReturn(new ArrayList<>());

assertThat(hostCheck.validHost(new URI("https://host1.com"))).isTrue();
assertThat(hostCheck.validHost(new URI("https://host2.com"))).isTrue();
assertThat(hostCheck.validHost(new URI("https://host3.com"))).isTrue();
assertThat(hostCheck.validHost(new URI("https://host4.com"))).isFalse();
}

@Test
void testMultipleHostsInBlacklist() throws Exception {
when(rootConfig.getHostWhitelist()).thenReturn(null);
when(rootConfig.getHostBlacklist()).thenReturn(List.of("bad1.com", "bad2.com", "bad3.com"));

assertThat(hostCheck.validHost(new URI("https://bad1.com"))).isFalse();
assertThat(hostCheck.validHost(new URI("https://bad2.com"))).isFalse();
assertThat(hostCheck.validHost(new URI("https://bad3.com"))).isFalse();
assertThat(hostCheck.validHost(new URI("https://good.com"))).isTrue();
}
}
