package com.flatironschool.javacs;


import redis.clients.jedis.Jedis;


public class JedisMaker {
	
	/**
	 * Make a Jedis object and authenticate it.
	 * 
	 * @return
	 */
	public static Jedis make() {
		String host = "dory.redistogo.com";
		int port = 10534;
		String auth = System.getenv("REDISTOGO_AUTH");

		if (auth == null) {
			System.out.println("To connect to RedisToGo, you have to create an environment");
			System.out.println("variable named REDISTOGO_AUTH that contains your authorization");
			System.out.println("code.  If you select and instance on the RedisToGo web page,");
			System.out.println("you should see a URL that contains the information you need:");
			System.out.println("redis://redistogo:AUTH_CODE@HOST:PORT");
			System.out.println("In Eclipse, select Run->Run configurations...");
			System.out.println("Open the Environment tab, and add a new variable.");
		}
		
		Jedis jedis = new Jedis(host, port);
		jedis.auth(auth);
		return jedis;
	}


	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) {
		
		Jedis jedis = make();
		
		// String
		jedis.set("mykey", "myvalue");
		String value = jedis.get("mykey");
	    System.out.println("Got value: " + value);
	    
	    // Set
	    jedis.sadd("myset", "element1", "element2", "element3");
	    System.out.println("element2 is member: " + jedis.sismember("myset", "element2"));
	    
	    // List
	    jedis.rpush("mylist", "element1", "element2", "element3");
	    System.out.println("element at index 1: " + jedis.lindex("mylist", 1));
	    
	    // Hash
	    jedis.hset("myhash", "word1", Integer.toString(2));
	    jedis.hincrBy("myhash", "word2", 1);
	    System.out.println("frequency of word1: " + jedis.hget("myhash", "word1"));
	    System.out.println("frequency of word2: " + jedis.hget("myhash", "word2"));
	    
	    jedis.close();
	}
}
