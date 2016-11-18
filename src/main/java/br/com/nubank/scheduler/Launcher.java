package br.com.nubank.scheduler;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

public class Launcher {
	private static Logger logger = Logger.getLogger(Launcher.class);

	public void scheduleLauncher(String payload) throws JSONException, ParseException{
		JSONObject jsonObject = new JSONObject(payload).getJSONObject("job");
		
		Date scheduleDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").parse(jsonObject.getString("schedule"));
		Date now = new Date();
		
		long delay = scheduleDate.getTime() - now.getTime();
		
		if (delay > 0){
			Timer timer;
			timer = new Timer();
			logger.info("Scheduling a new Spot Request to be executed on: " + scheduleDate.toString());
	        timer.schedule(new SchedulerTask(timer, payload), delay);			
		}
	}	
}
