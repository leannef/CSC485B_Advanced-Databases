import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import scala.Tuple2;

public class MedlineAnalysis_post {

	  public static void main(String[] args) throws Exception {
		    
		    SparkConf conf = new SparkConf()
		    .setMaster("local")
		    .setAppName("Medline Simple Analysis");
		    JavaSparkContext sc = new JavaSparkContext(conf);
		    sc.setLogLevel("WARN");
		    
		    JavaRDD<String> medline_raw = sc.textFile("mesh_terms.txt");
		    
		    JavaRDD<List<String>> medline = 
		    		medline_raw
		    		.map(line->{
			    			String[] topicsArray = line.split("\\|");
			    			Arrays.sort(topicsArray);
			    			return Arrays.asList(topicsArray);
		    			});
		    
		    //medline.foreach(l->System.out.println(l));
		    
		    JavaRDD<String> topics = 
		    		medline
		    		.flatMap(topiclist -> topiclist.iterator());
		    
		    JavaPairRDD<String, Integer> topic_cnt = 
		    		topics
		    		.mapToPair( topic -> new Tuple2<>(topic,1) )
		    		.reduceByKey( (x,y)->x+y );
		    
		    JavaPairRDD<Integer, String> cnt_topic = 
		    		topic_cnt
		    		.mapToPair(tc->new Tuple2<>(tc._2,tc._1));
		    
		    JavaPairRDD<Integer, Iterable<String>> cnt_topicList = 
		    		cnt_topic
		    		.groupByKey();
		    
		    JavaPairRDD<Integer, Iterable<String>> cnt_topicList_sorted = 
		    		cnt_topicList
		    		.sortByKey(false); //ascending is set to false, so it's descending
		    
		    List<Tuple2<Integer, Iterable<String>>> top10 = 
		    		cnt_topicList_sorted
		    		.take(10);
		    
		    top10.forEach( x -> System.out.println(x._1 + ": " + x._2) );
		    
		    //Of course, we can combine all the above together in a one-liner:
		    medline_raw
		    	.map(line->{
	    			String[] topicsArray = line.split("\\|");
	    			Arrays.sort(topicsArray);
	    			return Arrays.asList(topicsArray);
    			})
		    	.flatMap(topiclist -> topiclist.iterator()) //topics
		    	.mapToPair(topic -> new Tuple2<>(topic,1))
		    	.reduceByKey((x,y)->x+y) //topics_cnt
		    	.mapToPair( tc->new Tuple2<>(tc._2,tc._1) ) //cnt_topic
		    	.groupByKey() //cnt_topicList
		    	.sortByKey(false) //cnt_topicList_sorted
		    	.take(10)
		    	.forEach( x -> System.out.println(x._1 + ": " + x._2) );
		    
		    //Let's create a frequency count.
		    //This is an RDD of integer pairs (cnt, freq), e.g. (5,10), 
		    //meaning that there are 10 topics having a count of 5.
		    JavaPairRDD<Integer,Integer> cnt_freq = 
		    		cnt_topic
		    		.aggregateByKey(0, (acc,value)->acc+1, (acc1,acc2)->acc1+acc2 )
		    		.sortByKey();
		    
		    System.out.println("topic_cnt: frequency");
		    cnt_freq.take(10).forEach( x -> System.out.println(x._1 + ": " + x._2) );
		    
		    
		    
		    //Cooccurence analysis
		    JavaRDD<Tuple2<String,String>> topicPairs = 
		    		medline
		    		.flatMap(l -> {
		    			List<Tuple2<String,String>> combinations = new ArrayList<>();
		    			
		    			for(int i=0; i<l.size(); i++)
		    				for(int j=i+1; j<l.size(); j++)
		    					combinations.add(new Tuple2<>(l.get(i),l.get(j)));
		    			
		    			return combinations.iterator();
		    		});
		    
		    JavaPairRDD<Tuple2<String,String>,Integer> cooccurs = 
		    		topicPairs
		    		.mapToPair(p -> new Tuple2<>(p, 1))
		    		.reduceByKey((x,y)->x+y);
		    
		    cooccurs.cache();
		    
		    System.out.println("The number of co-occurring topics (pairs) is " + cooccurs.count());
		    
		    //print out the top 10 most frequent pairs
		    System.out.println("The top 10 most frequent topic pairs are:");
		    
		    cooccurs.mapToPair( pc->new Tuple2<>(pc._2,pc._1) )
		    .sortByKey(false)
	    	.take(10)
	    	.forEach( x -> System.out.println(x._1 + ": " + x._2._1 + "|" + x._2._2) );


		    //Degree analysis.
		    //For each node find its degree (number of neighbors)
		    System.out.println(
		    		"*****************************************\n" + 
		    		"Now we'll do degree analysis in the topics graph.\n" + 
		    		"Each topic is a vertex.\n" +
		    		"Each pair in cooccurs is an edge." );
		    
		    //Complete the TODO parts for Assignment 4.
		    
		    //TODO Create an "edges" RDD from cooccurs. 
		    //E.g. if ((t1,t2),cnt) in cooccurs, map to (t1,t2) and (t2,t1) in edges.
		    //This is because the graph is undirected, so for each pair of connected 
		    //topics, we need to create both the forward and backward edge.
		    JavaPairRDD<String,String> edges = cooccurs.flatMapToPair(
		    	value -> {
		    		List<Tuple2<String,String>> two_edge = new ArrayList<>();
		    		String t1 = value._1._1;
		    		String t2 = value._1._2;
		    		two_edge.add(new Tuple2<>(t1,t2));
		    		two_edge.add(new Tuple2<>(t2,t1));
		    		return two_edge.iterator();
		    	}
		    ); // = ... 
		   
		    //TODO Transform "edges" to "edges_1", namely, 
		    //for each (u,v) in edges, map to (u,1)
		    JavaPairRDD<String,Integer> edges_1 = edges.flatMapToPair(
		    	value -> {
		    		String u = value._1;
		    		List<Tuple2<String,Integer>> two_edge = new ArrayList<>();
		    		two_edge.add(new Tuple2<String, Integer>(u, 1));
		    		return two_edge.iterator();
		    	}
		    ); // = ...
		    
		    //TODO Transform "edges_1" to an RDD of vertex-degree pairs.
		    //Note that we could do that using countByKey() on edges, but 
		    //this is unfortunately an "action" (not a transformation), 
		    //i.e. it does not produce an RDD for further parallel processing.
		    //Hence, we would like to find the degrees using reduceByKey().
		    JavaPairRDD<String, Integer> vert_deg_rdd = edges_1.reduceByKey(
		    	(x, y) -> (x+y)
		    ); // = ... 
		    
		    //TODO Print out the top 10 most connected vertices (of highest degree)
		    //...
		    System.out.println("The top 10 most connected vertices are:");
		    
		    vert_deg_rdd.mapToPair( pc->new Tuple2<>(pc._2,pc._1) )
		    .sortByKey(false)
	    	.take(10)
	    	.forEach( x -> System.out.println(x._1 + ": " + x._2) );
		    //TODO Do degree frequency analysis. 
		    //Namely, for each degree value d, give the number of vertices 
		    //that have a degree of d.
		    //The result should be an RDD of (degree,frequency) pairs. 
		    //Sort in ascending order by degree.
		    //Then, plot the frequency numbers in Excel. 
		    //You should observe the "long-tail" phenomenon. 
		    //...
		    System.out.println("Degree Value, number of vertices");
		    vert_deg_rdd.mapToPair( pc -> new Tuple2<>(pc._2,1) )
		    .reduceByKey((x,y) -> (x+y))
		    .sortByKey(true)
		    .foreach( x -> System.out.println(x._1 + ": " + x._2) );

		    sc.stop();
		    sc.close();
		  }
}