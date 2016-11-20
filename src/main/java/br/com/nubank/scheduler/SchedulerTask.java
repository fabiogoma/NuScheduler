package br.com.nubank.scheduler;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsResult;
import com.amazonaws.services.ec2.model.LaunchSpecification;
import com.amazonaws.services.ec2.model.RequestSpotInstancesRequest;
import com.amazonaws.services.ec2.model.RequestSpotInstancesResult;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageRequest;

import br.com.nubank.helpers.ScheduleParser;
import br.com.nubank.pojos.Job;
import br.com.nubank.pojos.Scheduler;

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
		
		AWSCredentials credentials = new EnvironmentVariableCredentialsProvider().getCredentials();	
		
		// Create the AmazonEC2 client so we can call various APIs.
		AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard().withRegion(System.getenv("REGION")).build();
		
		// Initializes a Spot Instance Request
		RequestSpotInstancesRequest requestRequest = new RequestSpotInstancesRequest();

		// Request 1 x Spot Instance
		requestRequest.setSpotPrice(System.getenv("AWS_SPOT_PRICE"));
		requestRequest.setInstanceCount(Integer.valueOf(1));
		
		// Setup the specifications of the launch.
		LaunchSpecification launchSpecification = new LaunchSpecification();
		launchSpecification.setImageId("ami-cec066ae"); //Amazon Linux 2016.09
		launchSpecification.setInstanceType("t1.micro");
		launchSpecification.withKeyName(System.getenv("AWS_ACCESS_NAME"));
		launchSpecification.setUserData(org.apache.commons.codec.binary.Base64.encodeBase64String(prepareUserData().getBytes()));
		
		// Add the security group to the request.
		ArrayList<String> securityGroups = new ArrayList<String>();
		securityGroups.add(System.getenv("AWS_SECURITY_GROUP"));
		launchSpecification.setSecurityGroups(securityGroups);

		// Add the launch specifications to the request.
		requestRequest.setLaunchSpecification(launchSpecification);

		// Call the RequestSpotInstance API.
		RequestSpotInstancesResult requestResult = ec2.requestSpotInstances(requestRequest);
		List<SpotInstanceRequest> requestResponses = requestResult.getSpotInstanceRequests();
		
		ArrayList<String> spotInstanceRequestIds = new ArrayList<String>();
		
		for (SpotInstanceRequest requestResponse : requestResponses) {
		    logger.info("Created Spot Request: " + requestResponse.getSpotInstanceRequestId());
		    spotInstanceRequestIds.add(requestResponse.getSpotInstanceRequestId());
		}
		updateInstanceId(ec2, credentials, spotInstanceRequestIds);
	}
	
	private String prepareUserData(){
		// Prepare UserData do be executed during the cloud-init
		StringBuilder userData = new StringBuilder();
		userData.append("#!/bin/bash\n");
		
		ScheduleParser scheduleParser = new ScheduleParser();
		Scheduler schedule = null;
		try {
			schedule = scheduleParser.JsonToObject(this.getPayload());
		} catch (JSONException ex) {
			ex.printStackTrace();
		} catch (ParseException ex) {
			ex.printStackTrace();
		}
		
		Iterator<?> it = schedule.getVariables().entrySet().iterator();
	    while (it.hasNext()) {
	        @SuppressWarnings("unchecked")
			Map.Entry<String, String> pair = (Map.Entry<String, String>)it.next();
	        String variable = pair.getKey() + "=" + pair.getValue();
	        userData.append("echo " + variable + " >> /tmp/variables.properties\n");
	    }
		
		// Adding the file script.sh
		LineIterator lit = null;
		try {
			File userDataScript = new File("script.sh");
			lit = FileUtils.lineIterator(userDataScript, "UTF-8");
		    while (lit.hasNext()) {
		    	userData.append(lit.nextLine()+"\n");
		    }
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			lit.close();
		}
		
		return userData.toString();
	}
	
	private void updateInstanceId(AmazonEC2 ec2, AWSCredentials credentials, ArrayList<String> spotInstanceRequestIds){
		boolean anyOpen; // tracks whether any requests are still open

		// a list of instances to tag.
		ArrayList<String> instanceIds = new ArrayList<String>();

		do {
		    DescribeSpotInstanceRequestsRequest describeRequest = new DescribeSpotInstanceRequestsRequest();
		    describeRequest.setSpotInstanceRequestIds(spotInstanceRequestIds);

		    anyOpen=false; // assume no requests are still open

		    try {
		        // Get the requests to monitor
		        DescribeSpotInstanceRequestsResult describeResult = ec2.describeSpotInstanceRequests(describeRequest);

		        List<SpotInstanceRequest> describeResponses = describeResult.getSpotInstanceRequests();

		        // are any requests open?
		        for (SpotInstanceRequest describeResponse : describeResponses) {
		                if (describeResponse.getState().equals("open")) {
		                    anyOpen = true;
		                    break;
		                }
		                // get the corresponding instance ID of the spot request
		                instanceIds.add(describeResponse.getInstanceId());
		                logger.info("Created Instance: " + describeResponse.getInstanceId());
		                logger.info("Sending message to queue sqs_update");
		                AmazonSQS sqs = new AmazonSQSClient(credentials);
		                
		                JSONObject jsonObject = new JSONObject(this.getPayload()).getJSONObject("job");
		            	String schedule = jsonObject.getString("schedule");
		                
		                Job job = new Job();
		                job.setInstanceId(describeResponse.getInstanceId());
		                job.setRequestId(describeResponse.getSpotInstanceRequestId());
		                job.setSchedule(schedule);
		                job.setStatus("running");
		                
		                JSONObject jobJson = new JSONObject(job);
		                
		        		sqs.sendMessage(new SendMessageRequest(System.getenv("SQS_UPDATE_URL"), jobJson.toString()));
		        }
		    }
		    catch (AmazonServiceException e) {
		        // Don't break the loop due to an exception (it may be a temporary issue)
		        anyOpen = true;
		    }

		    try {
		        Thread.sleep(60*1000); // sleep 60s.
		    }
		    catch (Exception e) {
		        // Do nothing if the thread woke up early.
		    }
		} while (anyOpen);
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
