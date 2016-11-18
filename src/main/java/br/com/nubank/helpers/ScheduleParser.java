package br.com.nubank.helpers;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;

import br.com.nubank.pojos.Scheduler;


public class ScheduleParser {

	public Scheduler JsonToObject(String payload) throws JSONException, ParseException{
		Scheduler sched = new Scheduler();
		
		JSONObject jsonObject = new JSONObject(payload).getJSONObject("job");
		JSONObject jsonVariables = jsonObject.getJSONObject("variables");

		HashMap<String, String> variables = new HashMap<String, String>();

		Iterator<?> keys = jsonVariables.keys();

		while( keys.hasNext() ) {
		    String key = (String)keys.next();
		    String value = jsonVariables.getString(key);
		    
		    variables.put(key, value);
		    
		}
		
		Date scheduleDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").parse(jsonObject.getString("schedule"));
		
		sched.setImage(jsonObject.getString("image"));
		sched.setSchedule(scheduleDate);
		sched.setVariables(variables);
		
		return sched;
	}
}
