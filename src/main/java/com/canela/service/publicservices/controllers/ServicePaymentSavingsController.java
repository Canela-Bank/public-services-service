package com.canela.service.publicservices.controllers;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

@RestController
@RequestMapping(value = "/api/service-payments")
public class ServicePaymentSavingsController {

    @PutMapping(value = "/savings")
    public ResponseEntity<String> servicePaymentSavings (@RequestBody ServiceRequest request){

    	String BillId = request.accountId;
    	int salesService = 0;
   	
    	//Provider connection
    	try {
    		URL getPriceUrl = new URL("http://localhost:9016/"+ request.serviceType +"/pay");
        	HttpURLConnection httpCon = (HttpURLConnection) getPriceUrl.openConnection();
        	httpCon.setDoOutput(true);
        	httpCon.setRequestMethod("POST");
        	httpCon.setRequestProperty("Content-Type", "application/json");
        	httpCon.setRequestProperty("Accept", "application/json");
        	httpCon.setDoOutput(true);

       //Build RequestBody
        	
        	 JSONObject dataReturn = new JSONObject(){{
                 put("BillId",BillId);
        	 }};
        	 
        	
        	
        	try(OutputStream os = httpCon.getOutputStream()) {
        	    byte[] input = dataReturn.toString().getBytes("utf-8");
        	    os.write(input, 0, input.length);			
        	}
        	httpCon.connect();
        	
        //If the connection is successful
            if(httpCon.getResponseCode() == HttpURLConnection.HTTP_ACCEPTED){
            
            //Read provider response
            	try(BufferedReader br = new BufferedReader(
          			  new InputStreamReader(httpCon.getInputStream(), "utf-8"))) {
          			    StringBuilder response = new StringBuilder();
          			    String responseLine = null;
          			    while ((responseLine = br.readLine()) != null) {
          			       response.append(responseLine.trim());
          			    }
          			    salesService =  Integer.parseInt(response.toString());  
          			} 	
            }
	
		} catch (Exception e) {
			 throw new RuntimeException(e);
		}
    	
    	
        //GraphQL connection
        URL getAccountUrl = null;
        try {
            getAccountUrl = new URL("http://10.1.0.0:3001/graphql?query=%7B%0A%20%20getAccountById%20(id%3A%22"+ request.getSavingsId() +"%22)%7B%0A%20%20%20%20id%0A%20%20%20%20balance%0A%20%20%20%20user_id%0A%20%20%7D%0A%7D%0A");
            HttpURLConnection connAccount = (HttpURLConnection) getAccountUrl.openConnection();
            connAccount.setRequestMethod("GET");

            //If the connection is successful
            if(connAccount.getResponseCode() == HttpURLConnection.HTTP_OK){
                //Obtain body information
                BufferedReader in = new BufferedReader(new InputStreamReader(connAccount.getInputStream()));
                String inputLine;
                StringBuilder responseBuff = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    responseBuff.append(inputLine);
                }
                in.close();

                //Parse to JSON the String obtained
                JSONObject jsonData = new JSONObject(responseBuff.toString());
                JSONObject jsonGetAccount = new JSONObject(jsonData.get("data").toString());
                String accountInfo = jsonGetAccount.get("getAccountById").toString();
                JSONObject jsonAccount = new JSONObject(accountInfo);

                if(salesService > Integer.parseInt(jsonAccount.get("balance").toString())){
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pago rechazado");
                } else {
                    long newBalance = (Long.parseLong(jsonAccount.get("balance").toString()) - salesService);
                    //Update balance of the account
                    URL updateAccount = new URL("http://10.1.0.0:3001/graphql?query=mutation%7B%0A%20%20createAccount%20(id%3A%22" + request.getSavingsId() + "%22%2C%20balance%3A%20" + newBalance + "%2C%20user_id%3A%20%22" + jsonAccount.get("user_id") + "%22)%7B%0A%20%20%20%20id%0A%20%20%20%20balance%0A%20%20%20%20user_id%0A%20%20%7D%0A%7D%0A");
                    HttpURLConnection connUpdate = (HttpURLConnection) updateAccount.openConnection();
                    connUpdate.setRequestMethod("POST");

                    if(connUpdate.getResponseCode() == HttpURLConnection.HTTP_OK){
                        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Pago realizado");
                    } else {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No se pudo realizar el pago. Intenta de nuevo");
                    }
                }
            }
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e);
        }
 
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Lo sentimos, el pago no pudo ser realizado");
        
    }

    static class ServiceRequest {
        private String userId;
        private String accountId;
        private String serviceType;
        private String savingsId;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public String getServiceType() {
            return serviceType;
        }

        public void setServiceType(String serviceType) {
            this.serviceType = serviceType;
        }

        public String getSavingsId() {
            return savingsId;
        }

        public void setSavingsId(String savingsId) {
            this.savingsId = savingsId;
        }
    }
}
