package com.deltek.integration;

import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.catalina.servlet4preview.http.Mapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.core.MessageProcessorSpec;
import org.springframework.integration.dsl.jms.Jms;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;

import com.deltek.integration.budget.AsyncTaskRequestHandler;
import com.sohnar.trafficlite.datamodel.enums.event.TrafficEmployeeEventType;
import com.sohnar.trafficlite.integration.data.TrafficCompanyMessage;
import com.sohnar.trafficlite.integration.data.TrafficEmployeeMessage;
import com.sohnar.trafficlite.integration.data.async.AsyncTaskMessage;

@Configuration
@EnableIntegration
public class BrokerAndIntegrationConfiguration {
   
	private static final String INTEGRATION_ERROR_CHANNEL = "integrationErrorChannel";

	//May  need to listen for the User Generated Event.  It is more likely we
	//will listen to the Platform Event (ASyncTask...) that isnt exposed to the client.
	public static final String BACKGROUND_TASK_REQUEST_CHANNEL = "backgroundTaskRequestChannel";

	//Async Task Requests are taken off the QUEUE and arrive on this channel.
	public static final String MACONOMY_BUDGET_ASYNC_REQUEST_CHANNEL = "maconomyBudgetAsyncRequestChannel";

	@Bean
	public Queue queue() {
		return new ActiveMQQueue("queue.jms.asynctask");
	}
	
	@Bean
	public IntegrationFlow queueOut(JmsTemplate jmsTemplate, Queue queue) {
		return IntegrationFlows.from("toJmsQueueChannel")
				.handle(Jms.outboundAdapter(jmsTemplate).destination(queue))
				.get();
	}

	@Bean
	public IntegrationFlow queueIn(PooledConnectionFactory pooledConnectionFactory, Queue queue) {
		return IntegrationFlows.from(
				Jms.messageDrivenChannelAdapter(pooledConnectionFactory).destination(queue))
				.channel("fromJmsQueueChannel")
				.filter(GenericMessage.class, i -> i.getPayload().getClass().equals(AsyncTaskMessage.class) )
//				.handle(h -> System.out.println("A MESSAGE HAS ARRIVED: "+h))
				.filter(AsyncTaskMessage.class, i -> "MACONOMY_BUDGET".equals(i.getType()))
				.channel(MACONOMY_BUDGET_ASYNC_REQUEST_CHANNEL)
				.get();
	}
	
	@Bean
	public Topic topic() {
		return new ActiveMQTopic("topic.flex.jms.event");
	}
	
	@Bean
	public IntegrationFlow topicOut(JmsTemplate jmsTemplate, Topic topic) {
		return IntegrationFlows.from("toJmsMessageChannel")
				.handle(Jms.outboundAdapter(jmsTemplate).destination(topic))
				.get();
		
	}
	
	/**
	 * Filter out all the Pub/Sub messages from the platform to the ones we care about, then pass them over to the 
	 * BACKGROUND_REQUEST_CHANNEL.
	 * 
	 * @param pooledConnectionFactory
	 * @param topic
	 * @return
	 */
//	@Bean
//	public IntegrationFlow topicIn(PooledConnectionFactory pooledConnectionFactory, Topic topic) {
//		return IntegrationFlows.from(
//				Jms.messageDrivenChannelAdapter(pooledConnectionFactory).destination(topic).errorChannel(INTEGRATION_ERROR_CHANNEL))
//				.channel("fromJmsMessageChannel")
//				.filter(TrafficEmployeeMessage.class::equals)
//				.filter(TrafficEmployeeMessage.class, f -> TrafficEmployeeEventType.BACKGROUND_TASK_REQUSTED.equals(
//							f.getTrafficEmployeeEvent().getTrafficEmployeeEventType()))
//				.channel(BACKGROUND_TASK_REQUEST_CHANNEL)
//				.get();
//	}
//	
//	@Bean
//	public IntegrationFlow employeeChannelFlow() {
//		return IntegrationFlows.from("fromJmsMessageChannel")
//	.route(payload -> payload.getClass().getName(), 
//			m -> m.suffix("Channel")
//			.channelMapping(TrafficCompanyMessage.class.getTypeName(), "company")
//			.channelMapping(TrafficEmployeeMessage.class.getTypeName(), "employee"))
//		  .filter(TrafficEmployeeMessage.class, f -> TrafficEmployeeEventType.BACKGROUND_TASK_REQUSTED.equals(
//				  											f.getTrafficEmployeeEvent().getTrafficEmployeeEventType()))		
//		  .channel(BACKGROUND_TASK_REQUEST_CHANNEL)
//		  .handle(System.out::println)
//		  .get();
//	}

	@Bean
	public IntegrationFlow errorFlow() {
		return IntegrationFlows.from(INTEGRATION_ERROR_CHANNEL)
				.handle(System.out::println)
				.get();
	}
	
	@Bean
	public AsyncTaskRequestHandler asyncTaskRequestHandler() {
		return new AsyncTaskRequestHandler();
	}
	
	@MessagingGateway
	public interface EmployeeMessageGateway {
		
		@Gateway(requestChannel="toJmsMessageChannel")
		void sendMessage(TrafficEmployeeMessage<?> employeeMessage);
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
