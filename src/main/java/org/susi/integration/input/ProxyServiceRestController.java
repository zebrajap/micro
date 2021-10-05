package org.susi.integration.input;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.jms.TextMessage;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.susi.integration.CodeobeListener;
import org.susi.integration.CodeobeLog;


@RestController
@RequestMapping("/hrservice-proxy")
@EnableIntegration
public class ProxyServiceRestController  extends CodeobeListener  {
	

	@Autowired 
	CodeobeLog codeobeLog;
	
	@Value("${output.http_endpoint}")
	String outputEndpoint;
	
	@GetMapping("/hello/{user}")
	public String hello(@PathVariable String user) {
		return "Hello " +  user + " " +new Date().toString();
	}

	@PostMapping(value = "/order")
	public String order(@RequestBody String request) {
		System.out.println("1. Request received @REST interface request= " + request);
		TextMessage tm1 = codeobeLog.logMessageBeforeProcess(request);
		List<String> reponseList = process(tm1);

		String singleResponse = listToString(reponseList); 
		System.out.println("5. Sending reposnse out = " + singleResponse);
		return singleResponse;
	}


	@Override
	public List<String> process(TextMessage tm) {
		
		String msg = getPayload(tm);
		System.out.println("2. Start processing request =" + msg);
		List<String> orderArray = convert(msg);
		List<TextMessage> processedList = new ArrayList<TextMessage>();
		
		TextMessage tm2 = null;
		if (orderArray != null) {
			for (String order : orderArray) {
				System.out.println("2.1 Processing array element =" + order);
				tm2 = codeobeLog.logMessageAfterProcess(tm, order);
				processedList.add(tm2);
			}
		} else {
			System.out.println("2.2 Pizza order error=" + msg);
			tm2 = codeobeLog.logErrorAfterProcess(tm, "Invalid Request");
			processedList.add(tm2);
		}
		
		//Make sure you call send method from process to make it work for resends.replays
		List<String> results  = send(processedList);
		return results;
	}
	
	
	@Override
	public List<String> send(List<TextMessage> processedList)  {
		
		List<String> reponseList = new ArrayList<String>();
		for (TextMessage tm : processedList)  {
			String msg = getPayload(tm);
			
			System.out.println("3. sendToHttpEndpoint ....." + msg);
			CloseableHttpClient client = HttpClients.createDefault();
		    HttpPost httpPost = new HttpPost(outputEndpoint);
	
		    String tmOut = null;
		    try {
			    StringEntity entity = new StringEntity(tm.getText());
			    httpPost.setEntity(entity);
			    httpPost.setHeader("Accept", "application/json");
			    httpPost.setHeader("Content-type", "application/json");
			  
			    CloseableHttpResponse response = client.execute(httpPost);
			    if (response != null && response.getStatusLine().getStatusCode() == 200) {
			    	tmOut = EntityUtils.toString(response.getEntity());
			    	codeobeLog.logResponse(tm, tmOut);
					System.out.println("3.1 logResponse ....." );
			    } else {
			    	tmOut = "Error from endpoint";
			    	codeobeLog.logResponseError(tm, tmOut);
					System.out.println("3.2 logResponseError ....." );
			    }
		    }  catch (Exception ex) {
		    	tmOut = "Error sening message out";
			    codeobeLog.logResponseError(tm, tmOut);
				System.out.println("3.3 logResponseException ....." );
		    } finally {
		    	try {
					client.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		    }
		    reponseList.add(tmOut);
		}
	    return reponseList;
	}

	
	
	//users convert method
	private static List<String> convert(String reqString) {
		List<String> outArray = new ArrayList<String>();
		try {
			String template = "{\"name\":\"%s\", \"age\":\"%s\"}";
			String[] messages = reqString.split("\n");
			if (messages.length > 0) {
				for (String m : messages) {
					String[] mParts = m.split(",");
					outArray.add(String.format(template, mParts[0], mParts[1]));
				}
			}
		} catch (Exception e) {
			outArray = null;
		}
		return outArray;
	}
	
	//users output aggregation method
	private String listToString(List<String> result) {
		String out = "[ ";
		for (int i=0; i < result.size(); i++) {
			if (i < result.size() - 1) {
				out += result.get(i) + ", ";
			} else {
				out += result.get(i);
			}
		}
		out += " ]";
		return out;
	}
 
 
}
