package de.uni_leipzig.mosquito.query;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bio_gene.wookie.connection.Connection;
import org.bio_gene.wookie.connection.ConnectionFactory;
import org.bio_gene.wookie.utils.GraphHandler;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;

import de.uni_leipzig.mosquito.benchmark.MinimizeTripleStore;
import de.uni_leipzig.mosquito.utils.RandomStringBuilder;

public class QueryHandler {
	
		
	public static void main(String args[]) throws IOException{
//		String insert = "INSERT DATA {GRAPH %%r%% {%%r1%% %%r2%% %%d%%} {%%r1%% %%r3%% %%d%%}{%%r1%% %%r2%% %%r4%%} {%%r4%% %%r2%% %%i%%} {%%r4%% %%r5%% %%s%%}}";
		String select = "select distinct ?s where {%%v1%% <http://dbpedia.org/property/einwohner> %%v2%%} LIMIT 10";
		PrintWriter pw = new PrintWriter(new File("queries.txt"));
		pw.write(select);
		pw.close();
		
		Connection con = ConnectionFactory.createImplConnection("dbpedia.org/sparql");
		QueryHandler qh = new QueryHandler(con, "queries.txt");
		qh.init();
	
	}
	
	private Connection con;
	private String path = "queryvalues"+File.separator;
	private int limit = 5000;
	private Random rand;
	private String fileForQueries;
	
	public QueryHandler(Connection con, String fileForQueries) throws IOException{
		this.con = con;
		this.fileForQueries = fileForQueries;
	}
	
	public void init() throws IOException{
		init(fileForQueries);
	}
	
	public void setLimit(int i){
		limit = i;
	}

	
	private void init(String queriesFile) throws IOException{
		rand = new Random(2);
		//Gets the Values
		List<String> queryPatterns = Files.readAllLines(Paths.get(queriesFile), Charset.forName("UTF-8")); 
		int i=0;
		for(String p : queryPatterns){
			if(!(p.toLowerCase().startsWith("insert") || p.toLowerCase().startsWith("delete"))){
				//SELECT, ASK, DESCRIBE, CONSTRUCT
//				QueryFactory.create(p);
				valuesToCSV(p, String.valueOf(i));
			}
			else{
				//UPDATE 
				updatePattern(p, String.valueOf(i));
			}
			
			i++;
		}
	}
	
	
	
	private int valuesToCSV(String pattern, String fileName) throws IOException{
		String query = String.valueOf(pattern);
		int ret = 0;
		try{
			new File(path).mkdirs();
			File f = new File(path+fileName+".txt");
			
			f.createNewFile();
			PrintWriter pw = new PrintWriter(f);
			String q = selectPattern(query);
			ResultSet res = con.execute(q);
			while(res.next()){
				ResultSetMetaData rsmd = res.getMetaData();
				int columns = rsmd.getColumnCount();
				String line ="";
				List<Object> vars = new LinkedList<Object>();
				for(int i=1 ;i<=columns;i++ ){
					Object current = res.getObject(i);
					Node cur = MinimizeTripleStore.implToNode(current);
					vars.add(GraphHandler.NodeToSPARQLString(cur));
				}
				line = patternToQuery(pattern, vars);
				pw.write(line);
				pw.println();
				ret++;
			}
			pw.close();
			return ret;
		}
		catch(Exception e){
			e.printStackTrace();
			return ret;
		}
		
	}

	private String patternToQuery(String pattern, List<Object> vars){
		String query = String.valueOf(pattern);
		Pattern regex = Pattern.compile("%%v[0-9]*%%");
		Matcher matcher = regex.matcher(pattern);
		int i=0;
		List<String> replaced = new LinkedList<String>();
		while(matcher.find()){
			String var = matcher.group();
			if(!replaced.contains(var)){
				query = query.replace(var, vars.get(i).toString());		
				i++;
				replaced.add(var);
			}
		}
		return query;
	}
	
	private String updatePattern(String pattern, String fileName) throws IOException{
		String query = String.valueOf(pattern);
		RandomStringBuilder rsb = new RandomStringBuilder(100);
		new File(path).mkdirs();
		File f = new File(path+fileName+".txt");
		f.createNewFile();
		PrintWriter pw = new PrintWriter(f);
		for(int i=0;i<limit;i++){
			Pattern regex = Pattern.compile("%%i[0-9]*%%");
			Matcher matcher = regex.matcher(pattern);
			while(matcher.find()){
				query = query.replace(matcher.group(), String.valueOf(rand.nextInt()));
			}
			regex = Pattern.compile("%%d[0-9]*%%");
			matcher = regex.matcher(pattern);
			while(matcher.find()){
				query = query.replace(matcher.group(), String.valueOf(rand.nextDouble()));
			}
			regex = Pattern.compile("%%b[0-9]*%%");
			matcher = regex.matcher(pattern);
			while(matcher.find()){
				query = query.replace(matcher.group(), String.valueOf(rand.nextBoolean()));
		
			}
			regex = Pattern.compile("%%s[0-9]*%%");
			matcher = regex.matcher(pattern);
			while(matcher.find()){
				query = query.replace(matcher.group(), "'"+rsb.buildString(15)+"'");
		
			}
			regex = Pattern.compile("%%r[0-9]*%%");
			matcher = regex.matcher(pattern);
			while(matcher.find()){
				query = query.replace(matcher.group(), "<http://example.com/"+rsb.buildString(15)+">");
		
			}
			pw.write(query);
			pw.println();
			query = String.valueOf(pattern);
		}
		pw.close();
		
		return query;
	}
	
	private String selectPattern(String query){
		Pattern regex = Pattern.compile("%%v[0-9]*%%");
		Matcher matcher = regex.matcher(query);
		Set<String> vars = new HashSet<String>();
		while(matcher.find()){
			String var = matcher.group();
			query = query.replaceAll(var, "?"+var.replace("%", ""));
			vars.add(var.replace("%", ""));
		}
		Query q = QueryFactory.create(query);
		q.setLimit(Long.valueOf(limit));
		String select = "SELECT DISTINCT ";
		for(String v : vars){
			select+="?"+v+" ";
		}
		String clause = q.serialize();
		int i = clause.indexOf('\n');
		clause = clause.substring(i);
		return (select+clause).replace("\n"," ");
	}

}