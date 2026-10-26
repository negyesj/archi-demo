package hu.dmsone.archi.irat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** A függő szolgáltatások belső címei. */
@ConfigurationProperties(prefix = "irat")
public record IratProperties(String partnerUrl, String iamUrl) { }
