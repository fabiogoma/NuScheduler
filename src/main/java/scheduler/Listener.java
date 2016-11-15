package scheduler;

import java.util.List;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;

public class Listener {

	public static void main(String[] args) throws InterruptedException {
		AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
		AmazonSQS sqs = new AmazonSQSClient(credentials);
		
		System.out.println("Receiving messages from sqs_launch");
		String myQueueUrl = "https://us-west-2.queue.amazonaws.com/678982507510/sqs_launch";
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(myQueueUrl);
        
        while(true){
        	List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
	        for (Message message : messages) {
	        	
	        	Launcher launcher = new Launcher();
	        	launcher.launchSpotInstance(message.getBody().toString());
	        	
	        	String messageRecieptHandle = messages.get(0).getReceiptHandle();
	        	sqs.deleteMessage(new DeleteMessageRequest(myQueueUrl, messageRecieptHandle));
	        }
	        Thread.sleep(1000);
        }
        
	}

}
