package scheduler;

import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.LaunchSpecification;
import com.amazonaws.services.ec2.model.RequestSpotInstancesRequest;
import com.amazonaws.services.ec2.model.RequestSpotInstancesResult;

public class SchedulerTask extends TimerTask{
	private static Logger logger = Logger.getLogger(SchedulerTask.class);

	private Timer timer;
	private String payload;
	
	public SchedulerTask(Timer timer, String payload) {
		this.setTimer(timer);
		this.setPayload(payload);
	}
	
	@Override
	public void run() {
		launchSpotInstance();
        this.getTimer().cancel(); //Terminate the timer thread
	}

	private void launchSpotInstance(){
		logger.info("Launching new Spot Request");
		AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
		
		// Create the AmazonEC2 client so we can call various APIs.
		AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();

		// Initializes a Spot Instance Request
		RequestSpotInstancesRequest requestRequest = new RequestSpotInstancesRequest();

		// Request 1 x t1.micro instance with a bid price of $0.03.
		requestRequest.setSpotPrice("0.02");
		requestRequest.setInstanceCount(Integer.valueOf(1));
		
		// Setup the specifications of the launch. This includes the
		// instance type (e.g. t1.micro) and the latest Amazon Linux
		// AMI id available. Note, you should always use the latest
		// Amazon Linux AMI id or another of your choosing.
		LaunchSpecification launchSpecification = new LaunchSpecification();
		launchSpecification.setImageId("ami-cec066ae"); //Amazon Linux 2016.09
		launchSpecification.setInstanceType("t1.micro");	
		launchSpecification.withKeyName("fabiom");
		
		// Add the security group to the request.
		ArrayList<String> securityGroups = new ArrayList<String>();
		securityGroups.add("nu-default-sg");
		launchSpecification.setSecurityGroups(securityGroups);

		// Add the launch specifications to the request.
		requestRequest.setLaunchSpecification(launchSpecification);

		// Call the RequestSpotInstance API.
		RequestSpotInstancesResult requestResult = ec2.requestSpotInstances(requestRequest);
		
	}
	
	public Timer getTimer() {
		return timer;
	}

	public void setTimer(Timer timer) {
		this.timer = timer;
	}

	public String getPayload() {
		return payload;
	}

	public void setPayload(String payload) {
		this.payload = payload;
	}
}
