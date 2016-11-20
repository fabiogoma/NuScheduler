package br.com.nubank.scheduler;

import java.text.ParseException;
import java.util.List;

import org.apache.log4j.Logger;
import org.json.JSONException;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;

public class SchedulerListener {
	private static Logger logger = Logger.getLogger(SchedulerListener.class);

	public static void main(String[] args) throws InterruptedException, JSONException, ParseException {
		AWSCredentials credentials = new EnvironmentVariableCredentialsProvider().getCredentials();
		AmazonSQS sqs = new AmazonSQSClient(credentials);
		
		logger.info("Receiving messages from sqs_launch");
		String myQueueUrl = System.getenv("SQS_LAUNCH_URL");
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(myQueueUrl);
        
        while(true){
        	List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
	        for (Message message : messages) {
	        	
	        	Launcher launcher = new Launcher();
	        	launcher.scheduleLauncher(message.getBody().toString());
	        	
	        	String messageRecieptHandle = messages.get(0).getReceiptHandle();
	        	sqs.deleteMessage(new DeleteMessageRequest(myQueueUrl, messageRecieptHandle));
	        }
	        Thread.sleep(1000);
        }
        
	}

}
