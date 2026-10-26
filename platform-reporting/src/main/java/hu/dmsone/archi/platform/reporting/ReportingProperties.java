package hu.dmsone.archi.platform.reporting;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tenantonkénti MongoDB elérés ({@code platform.reporting.*}). */
@ConfigurationProperties(prefix = "platform.reporting")
public class ReportingProperties {

    private String host = "localhost";
    private int port = 27017;
    /** Extra kapcsolati paraméterek, pl. {@code tls=true&replicaSet=rs0}. */
    private String options = "";

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }
}
