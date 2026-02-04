package com.ai2qa.application.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Configuration properties for TargetGuardService.
 *
 * <p>Externalizes the security blacklists so they can be updated
 * without recompiling the application.
 *
 * <p>Configuration is loaded via @Value annotations from application.yml.
 * Use comma-separated lists in configuration files.
 */
@Component
public class TargetGuardProperties {

    // Default values as comma-separated strings for @Value defaults
    private static final String DEFAULT_SELF_PROTECTION =
            "ai2qa.com,www.ai2qa.com,api.ai2qa.com,app.ai2qa.com";

    private static final String DEFAULT_METADATA_HOSTS =
            "169.254.169.254,metadata.google.internal,169.254.169.253,169.254.170.2," +
            "kubernetes.default.svc,kubernetes.default,kubernetes.default.svc.cluster.local";

    private static final String DEFAULT_BLOCKED_TLDS =
            ".gov,.mil,.gov.uk,.gov.au,.gov.ca,.bank,.insurance," +
            ".internal,.local,.localhost,.lan,.home,.corp,.tk,.ml,.ga,.cf,.gq";

    private static final String DEFAULT_BLOCKED_DOMAINS =
            "google.com,facebook.com,meta.com,instagram.com,twitter.com,x.com,linkedin.com,tiktok.com," +
            "amazon.com,apple.com,microsoft.com," +
            "paypal.com,stripe.com,square.com,braintreepayments.com,adyen.com,checkout.com,worldpay.com," +
            "chase.com,bankofamerica.com,wellsfargo.com,citi.com," +
            "doubleclick.net,googlesyndication.com,googleadservices.com,facebook.net,fbcdn.net,amazon-adsystem.com," +
            "criteo.com,taboola.com,outbrain.com," +
            "cloudflare.com,akamai.com,fastly.com," +
            "gmail.com,outlook.com,yahoo.com";

    private static final String DEFAULT_BLOCKED_URL_PATTERNS =
            "/admin(?:/|$),/wp-admin(?:/|$),/administrator(?:/|$),/cpanel(?:/|$),/phpmyadmin(?:/|$),/adminer(?:/|$)," +
            "/api/.*/login(?:/|$),/api/.*/auth(?:/|$),/oauth(?:/|$),/auth/.*,/login(?:/|$),/signin(?:/|$)," +
            "/\\.env(?:/|$),/\\.git(?:/|$),/\\.aws(?:/|$),/\\.ssh(?:/|$),/server-status(?:/|$),/phpinfo(?:/|$)";

    @Value("${ai2qa.security.target-guard.self-protection-hosts:" + DEFAULT_SELF_PROTECTION + "}")
    private String selfProtectionHostsConfig;

    @Value("${ai2qa.security.target-guard.blocked-metadata-hosts:" + DEFAULT_METADATA_HOSTS + "}")
    private String blockedMetadataHostsConfig;

    @Value("${ai2qa.security.target-guard.blocked-tlds:" + DEFAULT_BLOCKED_TLDS + "}")
    private String blockedTldsConfig;

    @Value("${ai2qa.security.target-guard.blocked-domains:" + DEFAULT_BLOCKED_DOMAINS + "}")
    private String blockedDomainsConfig;

    @Value("${ai2qa.security.target-guard.blocked-url-patterns:" + DEFAULT_BLOCKED_URL_PATTERNS + "}")
    private String blockedUrlPatternsConfig;

    private List<String> selfProtectionHosts;
    private List<String> blockedMetadataHosts;
    private List<String> blockedTlds;
    private List<String> blockedDomains;
    private List<String> blockedUrlPatterns;
    private List<Pattern> compiledUrlPatterns;

    /**
     * Default constructor for test usage.
     */
    public TargetGuardProperties() {
        // Initialize with defaults for testing
        this.selfProtectionHosts = parseList(DEFAULT_SELF_PROTECTION);
        this.blockedMetadataHosts = parseList(DEFAULT_METADATA_HOSTS);
        this.blockedTlds = parseList(DEFAULT_BLOCKED_TLDS);
        this.blockedDomains = parseList(DEFAULT_BLOCKED_DOMAINS);
        this.blockedUrlPatterns = parseList(DEFAULT_BLOCKED_URL_PATTERNS);
        this.compiledUrlPatterns = compilePatterns(this.blockedUrlPatterns);
    }

    @PostConstruct
    void init() {
        // Parse comma-separated values from @Value injected strings
        this.selfProtectionHosts = parseList(selfProtectionHostsConfig);
        this.blockedMetadataHosts = parseList(blockedMetadataHostsConfig);
        this.blockedTlds = parseList(blockedTldsConfig);
        this.blockedDomains = parseList(blockedDomainsConfig);
        this.blockedUrlPatterns = parseList(blockedUrlPatternsConfig);
        this.compiledUrlPatterns = compilePatterns(this.blockedUrlPatterns);
    }

    private List<String> parseList(String config) {
        if (config == null || config.isBlank()) {
            return List.of();
        }
        return Arrays.stream(config.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<Pattern> compilePatterns(List<String> patterns) {
        return patterns.stream()
                .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE))
                .toList();
    }

    // Getters

    public List<String> getSelfProtectionHosts() {
        return selfProtectionHosts;
    }

    public List<String> getBlockedMetadataHosts() {
        return blockedMetadataHosts;
    }

    public List<String> getBlockedTlds() {
        return blockedTlds;
    }

    public List<String> getBlockedDomains() {
        return blockedDomains;
    }

    public List<String> getBlockedUrlPatterns() {
        return blockedUrlPatterns;
    }

    /**
     * Returns compiled blocked URL patterns for efficient matching.
     */
    public List<Pattern> getCompiledBlockedUrlPatterns() {
        return compiledUrlPatterns;
    }

    /**
     * Returns self-protection hosts as a Set for efficient lookup.
     */
    public Set<String> getSelfProtectionHostsSet() {
        return Set.copyOf(selfProtectionHosts);
    }

    /**
     * Returns blocked metadata hosts as a Set for efficient lookup.
     */
    public Set<String> getBlockedMetadataHostsSet() {
        return Set.copyOf(blockedMetadataHosts);
    }

    /**
     * Returns blocked TLDs as a Set for efficient lookup.
     */
    public Set<String> getBlockedTldsSet() {
        return Set.copyOf(blockedTlds);
    }

    /**
     * Returns blocked domains as a Set for efficient lookup.
     */
    public Set<String> getBlockedDomainsSet() {
        return Set.copyOf(blockedDomains);
    }
}
