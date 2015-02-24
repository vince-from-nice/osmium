package org.openstreetmap.osmium.service;

import javax.annotation.PostConstruct;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public class AuthentifiedRestClient extends RestTemplate {

    @Value("${osmApi.login}")
    private String login;

    @Value("${osmApi.password}")
    private String password;
    
    @PostConstruct
    public void init(/*String username, String password*/) {
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(null, -1),
                // TODO login and password are not yet initialized by spring when spring init the template
                // new UsernamePasswordCredentials(this.login, this.password));
                //new UsernamePasswordCredentials("Vince", "gluar2osm"));
                new UsernamePasswordCredentials(login, password));
        HttpClient httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
        setRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));
    }
    
        public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}