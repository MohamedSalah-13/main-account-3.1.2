package com.hamza.controlsfx.file.json;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;

public class ReadJSONExample {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        //JSON parser object to parse read file
        JSONParser jsonParser = new JSONParser();

       /* try (FileReader reader = new FileReader("info.json")) {
            //Read JSON file
            Object obj = jsonParser.parse(reader);

            JSONArray employeeList = (JSONArray) obj;
            System.out.println(employeeList);

            //Iterate over employee array
            employeeList.forEach(emp -> parseEmployeeObject((JSONObject) emp));

        } catch (ParseException | IOException e) {
            e.printStackTrace();
        }*/
        try {
            read();
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    private static void read() throws IOException, ParseException {
        JSONParser jsonParser = new JSONParser();
        JSONObject person = (JSONObject) jsonParser.parse(new FileReader("info.json"));

//        for (Object o : a) {
//            JSONObject person = (JSONObject) o;
        String name = (String) person.get("name");
        System.out.println(name);

        String city = (String) person.get("invoiceDate");
        System.out.println(city);

        double job = (Double) person.get("total");
        System.out.println(job);

        JSONArray cars = (JSONArray) person.get("items");

        for (Object c : cars) {
            System.out.println(c + "");
        }
//        }
    }

    private static void parseEmployeeObject(JSONObject employee) {
        //Get employee object within list
//        JSONObject employeeObject = (JSONObject) employee.get("employee");
//        JSONParser parser = new JSONParser();
//
//        Object obj = parser.parse(new FileReader("c:\\file.json"));
//        JSONObject jsonObject =  (JSONObject) obj;
        //Get employee first name
        String firstName = (String) employee.get("name");
        System.out.println(firstName);

        //Get employee last name
        double lastName = (Double) employee.get("amount");
        System.out.println(lastName);

        //Get employee website name
        String website = (String) employee.get("start_date");
        System.out.println(website);
    }
}