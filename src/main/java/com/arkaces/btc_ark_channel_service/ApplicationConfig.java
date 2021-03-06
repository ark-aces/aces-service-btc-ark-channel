package com.arkaces.btc_ark_channel_service;

import ark_java_client.*;
import com.arkaces.ApiClient;
import com.arkaces.aces_listener_api.AcesListenerApi;
import com.arkaces.aces_server.aces_service.config.AcesServiceConfig;
import com.arkaces.aces_server.aces_service.notification.NotificationService;
import com.arkaces.aces_server.aces_service.notification.NotificationServiceFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.arkaces.btc_ark_channel_service.bitcoin_rpc.BitcoinRpcSettings;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.env.Environment;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mail.MailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;

@Configuration
@EnableScheduling
@Import({AcesServiceConfig.class})
@EnableJpaRepositories
@EntityScan
public class ApplicationConfig {

    @Bean
    public ArkClient arkClient(Environment environment) {
        ArkNetworkFactory arkNetworkFactory = new ArkNetworkFactory();
        String arkNetworkConfigPath = environment.getProperty("arkNetworkConfigPath");
        ArkNetwork arkNetwork = arkNetworkFactory.createFromYml(arkNetworkConfigPath);

        HttpArkClientFactory httpArkClientFactory = new HttpArkClientFactory();
        return httpArkClientFactory.create(arkNetwork);
    }

    @Bean
    public AcesListenerApi bitcoinListener(Environment environment) {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(environment.getProperty("bitcoinListener.url"));
        if (environment.containsProperty("bitcoinListener.apikey")) {
            apiClient.setUsername("token");
            apiClient.setPassword(environment.getProperty("bitcoinListener.apikey"));
        }

        return new AcesListenerApi(apiClient);
    }

    @Bean
    public RestTemplate bitcoinRpcRestTemplate(BitcoinRpcSettings bitcoinRpcSettings) {
        return new RestTemplateBuilder()
                .rootUri(bitcoinRpcSettings.getUrl())
                .basicAuthorization(bitcoinRpcSettings.getUsername(), bitcoinRpcSettings.getPassword())
                .build();
    }

    @Bean
    public String bitcoinEventCallbackUrl(Environment environment) {
        return environment.getProperty("bitcoinEventCallbackUrl");
    }
    
    @Bean
    public Integer bitcoinListenerMinConfirmations(Environment environment) {
        return environment.getProperty("bitcoinListener.minConfirmations", Integer.class);
    }

    @Bean(name = "applicationEventMulticaster")
    public ApplicationEventMulticaster simpleApplicationEventMulticaster() {
        SimpleApplicationEventMulticaster eventMulticaster = new SimpleApplicationEventMulticaster();
        eventMulticaster.setTaskExecutor(new SimpleAsyncTaskExecutor());
        
        return eventMulticaster;
    }

    @Bean
    @ConditionalOnProperty(value = "notifications.enabled", havingValue = "true")
    public NotificationService emailNotificationService(Environment environment, MailSender mailSender) {
        return new NotificationServiceFactory().createEmailNotificationService(
                environment.getProperty("serverInfo.name"),
                environment.getProperty("notifications.fromEmailAddress"),
                environment.getProperty("notifications.recipientEmailAddress"),
                mailSender
        );
    }

    @Bean
    @ConditionalOnProperty(value = "notifications.enabled", havingValue = "false", matchIfMissing = true)
    public NotificationService noOpNotificationService() {
        return new NotificationServiceFactory().createNoOpNotificationService();
    }

    @Bean
    public BigDecimal lowCapacityThreshold(Environment environment) {
        return environment.getProperty("lowCapacityThreshold", BigDecimal.class);
    }
}
