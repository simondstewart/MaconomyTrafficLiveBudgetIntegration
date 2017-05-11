package com.deltek.integration;

import java.util.UUID;

import javax.jms.ConnectionFactory;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.jms.Jms;
import org.springframework.integration.endpoint.MethodInvokingMessageSource;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;

import com.sohnar.trafficlite.integration.data.TrafficCompanyMessage;
import com.sohnar.trafficlite.integration.data.TrafficEmployeeMessage;

@Configuration
@EnableIntegration
public class BrokerAndIntegrationConfiguration {
   
	/**
	 * 	<gateway service-interface="com.sohnar.trafficlite.integration.gateway.TrafficUserMessageGateway" id="userMessageGateway"/>
		<channel id="toJmsMessageChannel"/>
		<!-- Channel adapter to take from the channel and push send to the jms topic -->
		<jms:outbound-channel-adapter id="jmsOut" destination="jmsEventTopic" channel="toJmsMessageChannel" />
		<channel id="fromJmsMessageChannel"/>
		<jms:message-driven-channel-adapter id="jmsIn" destination="jmsEventTopic" channel="fromJmsMessageChannel" />

	 * @return
	 */

	@Bean
	public Topic topic() {
		return new ActiveMQTopic("topic.flex.jms.event");
	}
	
	@Bean
	public IntegrationFlow jmsOut(JmsTemplate jmsTemplate, Topic topic) {
		return IntegrationFlows.from("toJmsMessageChannel")
				.handle(Jms.outboundAdapter(jmsTemplate).destination(topic))
				.get();
		
	}
	
	@Bean
	public IntegrationFlow jmsIn(PooledConnectionFactory pooledConnectionFactory, Topic topic) {
		return IntegrationFlows.from(
				Jms.messageDrivenChannelAdapter(pooledConnectionFactory).destination(topic))
				.channel("fromJmsMessageChannel")
				.handle(h -> System.out.print(h))
				.get();
				
	}
	
	@Bean
	public IntegrationFlow inputFlow() {
		return IntegrationFlows.from("toJmsMessageChannel")
		  .handle(System.out::println)
		  .get();
	}
	
//	@Bean
//    public IntegrationFlow outputFlow() {
//        return IntegrationFlows.from(randomMessageSource(), c -> 
//                                    c.poller(Pollers.fixedRate(5000)))
//                    .channel("toJmsMessageChannel")
//                    .get();
//    }
	
//    @Bean // Serialize message content to json using TextMessage
//    public MessageConverter jacksonJmsMessageConverter() {
//        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
//        converter.setTargetType(MessageType.TEXT);
//        converter.setTypeIdPropertyName("_type");
//        return converter;
//    }
    
    private MessageSource<?> randomMessageSource() {
    	MethodInvokingMessageSource source = new MethodInvokingMessageSource();
    	source.setObject(this);
    	source.setMethodName("createMessage");
    	return source;
    }
    
    private TrafficEmployeeMessage createMessage() {
    	return new TrafficEmployeeMessage(1l, "Description: "+UUID.randomUUID());
    }
    
	@Bean
	public ConnectionFactory connectionFactory(@Value("${integration.brokerUrl}") String url) {
		return new ActiveMQConnectionFactory(url);
	}
	
	@Bean
	public PooledConnectionFactory pooledFactory(ConnectionFactory connectionFactory) {
		PooledConnectionFactory result = new PooledConnectionFactory();
		result.setConnectionFactory(connectionFactory);
		return result ;
	}
	
	@Bean
	public JmsTemplate jmsTemplate(PooledConnectionFactory pooledConnectionFactory) {
		JmsTemplate jmsTemplate = new JmsTemplate(pooledConnectionFactory);
		jmsTemplate.setPubSubDomain(true);
		return jmsTemplate;
	}
	
}
