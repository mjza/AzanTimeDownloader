import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.sql.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class Application {

	private final String USER_AGENT = "Mozilla/5.0";
	private final String YEAR = "2019";
	private final String DB_NAME = "database-" + YEAR + ".db";

	public static void main(String[] args) throws Exception {
		Application app = new Application();
		Connection c = null;
		try {
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:" + app.DB_NAME);
			c.close();
		} catch (Exception e) {
			System.err.println(e.getClass().getName() + ": " + e.getMessage());
			System.exit(0);
		}
		System.out.println("Opened database successfully");
		String[] aCountries = app.getCountries();
		// app.insertIntoCountries();
		String sUrl1 = "http://en.izhamburg.de/WebServices/Public/PublicServices.asmx/GetNewCities";
		ArrayList<String> aTimesColumns = app.getTimesColumns();
		for (int i = 0; i < aCountries.length; i++) {
			
			String params = "country=" + aCountries[i];
			String responce = app.sendPost(sUrl1, params);
			// System.out.println(responce);
			final String regex = "<string>([^<]+)<\\/string>";
			final Pattern pattern = Pattern.compile(regex);
			final Matcher matcher = pattern.matcher(responce);
			ArrayList<String> aCities = new ArrayList<String>();
			while (matcher.find()) {
				if (matcher.groupCount() >= 1) {
					aCities.add(matcher.group(1));
				}
			}
			app.insertIntoCities(aCountries[i], aCities);
			for (int j = 0; j < aCities.size(); j++) {
				System.out.print(aCountries[i] + " : " + aCities.get(j));
				String postfix = "Country=" + URLEncoder.encode(aCountries[i],"UTF-8") + "&City=" + URLEncoder.encode(aCities.get(j),"UTF-8");
				String sUrl2 = "http://en.izhamburg.de/index.aspx?pid=1&DownloadType=html&DownloadOwghat=true&"
						+ postfix;
				String html = app.sendGet(sUrl2);
				ArrayList<Map<String, String>> times = app.parseHTML(html, aCities.get(j), aCountries[i]);
				if(times.size()>0){
					System.out.println(" : "+times.size());
					app.insetrToDB(app.DB_NAME, "Times", aTimesColumns, times);
				} else{
					System.out.println(" : error");
				}
			}
		}
	}

	public ArrayList<Map<String, String>> parseHTML(String html, String city, String country) {
		ArrayList<Map<String, String>> data = new ArrayList<Map<String, String>>();
		Document doc = Jsoup.parse(html);
		Elements tables = doc.select("body > div > div.Box.table");
		int iMonth = 0;
		for (Element table : tables) {
			Elements days = table.select("div.Data");
			for (Element day : days) {
				Elements times = day.select("div > div");
				String[] aColumns = { "Midnight", "Esha", "Maghreb", "Ghorob", "Asr", "Zohr", "Tolo", "Sobh", "Imsak",
						"dd", "Day" };
				int iCol = 0;
				Map<String, String> entry = new HashMap<String, String>();
				entry.put("yyyy", YEAR);
				entry.put("mm", "" + iMonth);
				entry.put("City", city);
				entry.put("Country", country);
				for (Element time : times) {
					entry.put(aColumns[iCol++], time.text());
				}
				data.add(entry);
			}
			iMonth++;
		}
		return data;
	}

	public ArrayList<String> getTimesColumns() {
		String[] c = { "Country", "City", "yyyy", "mm", "dd", "Day", "Imsak", "Sobh", "Tolo", "Zohr", "Asr", "Ghorob",
				"Maghreb", "Esha", "Midnight" };
		ArrayList<String> aColumns = new ArrayList<String>();
		for (int i = 0; i < c.length; i++) {
			aColumns.add(c[i]);
		}
		return aColumns;
	}

	public String[] getCountries() {
		String[] aCountries = { "Germany", "Albania", "Austria", "Belarus", "Belgium", "Bosnia and Herzegovina",
				"Bulgaria", "Croatia", "Czech Republic", "Denmark", "Estonia", "Finland", "France","Greece", "Hungary",
				"Ireland", "Italy", "Latvia", "Liechtenstein", "Lithuania", "Luxembourg",
				"Macedonia (Former Yugoslav Republic of Macedonia)", "Malta", "Montenegro", "Netherlands", "Norway",
				"Poland", "Romania", "Russia", "Serbia", "Slovakia", "Slovenia", "Spain", "Sweden", "Switzerland",
				"Ukraine" };
		return aCountries;
	}

	public void insertIntoCountries() {
		String[] aCountries = this.getCountries();
		String tableName = "Countries";
		ArrayList<String> columns = new ArrayList<String>();
		columns.add("Name");
		ArrayList<Map<String, String>> data = new ArrayList<Map<String, String>>();
		for (int i = 0; i < aCountries.length; i++) {
			Map<String, String> entry = new HashMap<String, String>();
			entry.put("Name", aCountries[i]);
			data.add(entry);
		}
		this.insetrToDB(this.DB_NAME, tableName, columns, data);
	}

	public void insertIntoCities(String country, ArrayList<String> aCities) {
		String tableName = "Cities";
		ArrayList<String> columns = new ArrayList<String>();
		columns.add("Country");
		columns.add("City");
		ArrayList<Map<String, String>> data = new ArrayList<Map<String, String>>();
		for (int i = 0; i < aCities.size(); i++) {
			Map<String, String> entry = new HashMap<String, String>();
			entry.put("Country", country);
			entry.put("City", aCities.get(i));
			data.add(entry);
		}
		this.insetrToDB(this.DB_NAME, tableName, columns, data);
	}

	public void insetrToDB(String dbName, String tableName, ArrayList<String> columns,
			ArrayList<Map<String, String>> data) {
		Connection c = null;
		Statement stmt = null;
		try {
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:" + dbName);
			c.setAutoCommit(false);
			String query = "INSERT OR REPLACE INTO " + tableName + " ";
			String columnsName = " (";
			for (int j = 0; j < columns.size(); j++) {
				if (j > 0) {
					columnsName += ", ";
				}
				columnsName += columns.get(j);
			}
			columnsName += ") ";
			query += columnsName;
			String values = "";
			for (int i = 0; i < data.size(); i++) {
				String value = "\n(";
				for (int j = 0; j < columns.size(); j++) {
					if (j > 0) {
						value += ",";
					}
					value += "\"" + data.get(i).get(columns.get(j)) + "\"";
				}

				if (i < data.size() - 1) {
					value += "),";
				} else {
					value += ")";
				}
				values += value;
			}
			query += " VALUES " + values + ";";
			// System.out.println(query);
			stmt = c.createStatement();
			stmt.executeUpdate(query);
			stmt.close();
			c.commit();
			c.close();
		} catch (Exception e) {
			System.err.println(e.getClass().getName() + ": " + e.getMessage());
			System.exit(0);
		}
	}

	// HTTP GET request
	private String sendGet(String url) throws Exception {
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		// optional default is GET
		con.setRequestMethod("GET");
		// add request header
		con.setRequestProperty("User-Agent", USER_AGENT);
		int responseCode = con.getResponseCode();
		if (responseCode != 200) {
			System.out.println("\nSending 'GET' request to URL : " + url);
			System.out.println("Response Code : " + responseCode);
		}
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		// print result
		return response.toString();
	}

	// HTTP POST request
	private String sendPost(String url, String urlParameters) throws Exception {
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		// add reuqest header
		con.setRequestMethod("POST");
		con.setRequestProperty("User-Agent", USER_AGENT);
		con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
		// Send post request
		con.setDoOutput(true);
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());
		wr.writeBytes(urlParameters);
		wr.flush();
		wr.close();
		int responseCode = con.getResponseCode();
		if (responseCode != 200) {
			System.out.println("\nSending 'POST' request to URL : " + url);
			System.out.println("Post parameters : " + urlParameters);
			System.out.println("Response Code : " + responseCode);
		}
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		return response.toString();
	}
}
