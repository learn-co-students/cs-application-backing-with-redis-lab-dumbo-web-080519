# cs-application-backing-with-redis-lab

## Learning goals

1.  Read and write data to a Redis database.
2.  Translate Java data structures to Redis data structures.
3.  Implement a Redis-backed Web search index.


## Overview

In the next few labs we will get back to the application we started in the first unit: building a web search engine.  To review, the components of a search engine are:

* Crawling: We'll need a program that can download a web page, parse it, and extract the text and any links to other pages.

* Indexing: We'll need an index that makes it possible to look up a search term and find the pages that contain it.

* Retrieval: And we'll need a way to collect results from the Index and identify pages that are most relevant to the search terms.

If you did the lab on indexing, you implemented an index using Java maps.  In this lab, we'll revisit the Indexer and make a new version that stores the results in a database.

If you did the lab about the "Getting to Philosophy" conjecture, you built a crawler that follows the first link it finds.  In the next lab, we'll make a more general version that stores every link it finds in a queue and explores them in order.

And then, finally, you will work on the retrieval problem.

In these labs, we will provide less starter code, and you will make more design decisions.  These labs are also more open-ended.  We will suggest some minimal goals you should try to reach, but there are many ways you can go farther if you want to challenge yourself.

Now, let's get started on a new version of the Indexer.


## Persistence

The previous version of the Indexer stores the index in two data structures: a `TermCounter` that maps from a search term to the number of times it appears on a web page, and an `Index` that maps from a search term to the set of pages where it appears.

These data structures are stored in the memory of a running Java program, which means that when the program stops running, the index is lost.  Data stored only in the memory of a running program is called "volatile", because it vaporizes when the program ends.

Data that persists after the program that created it ends is called "persistent".  In general, files stored in a file system are persistent, as well as data stored in databases.

A simple way to make data persistent is to store it in a file.  Before the program ends, it could translate its data structures into a format like [JSON](https://en.wikipedia.org/wiki/JSON) and then write them into a file.  When it starts again, it could read the file and rebuild the data structures.

But there are several problems with this solution:

1.   Reading and writing large data structures (like a Web index) would be slow.
2.   The entire data structure might not fit into the memory of a single running program.
3.   If a program ends unexpectedly (for example, due to a power outage), any changes made since the program last started would be lost.

A better alternative is a database that provides persistent storage and the ability to read and write parts of the database without reading and writing the whole thing.

There are many kinds of database management systems (DBMS) that provide different capabilities.  You can read an [overview on this Wikipedia page](https://en.wikipedia.org/wiki/Database).

The database we recommend for this lab is Redis, which provides persistent data structures that are similar to Java data structures.  Specifically, it provides:

 * Lists of strings, similar to Java Lists.
 * Hashes, similar to Java Maps.
 * Sets of strings, similar Java Sets.

Redis is a "key-value database", which means that the data structures it contains (the values) are identified by unique strings (the keys).  A key in Redis plays the same role as a reference in Java: it identifies an object.  We'll see some examples soon.


## Redis clients and servers

Redis is usually run as a remote service; in fact, the name stands for "REmote DIctionary Server".  To use Redis, you have to run the Redis server somewhere and then connect to it using a Redis client.  There are many ways to set up a server and many clients you could use.  For this lab, we recommend:

1.  Rather than install and run the server yourself, consider using a service like [RedisToGo](https://redistogo.com/), which runs Redis in the cloud.  They offer a free plan with enough resources for this lab.

2.  For the client we recommend Jedis, which is a Java library that provides classes and methods for working with Redis.

Here are more detailed instructions to help you get started:

*  [Create an account on RedisToGo](https://redistogo.com/signup) and select the plan you want (probably the free plan to get started).

*  Create an "instance", which is a virtual machine running the Redis server.  If you click on the "Instances" tab, you should see your new instance, identified by a host name and a port number.  For example, we have an instance named "dory-10534".

*  Click on the instance name to get the configuration page.  Make a note of the URL near the top of the page, which looks like this:

    redis://redistogo:1234567890feedfacebeefa1e1234567@dory.redistogo.com:10534

This URL contains the server's host name, `dory.redistogo.com`, the port number, `10534`, and the password you will need to connect to the server, which is the long string of letters and numbers in the middle.  You will need this information for the next step.


## Hello, Jedis

When you check out the repository for this lab, you should find a file structure similar to what you saw in previous labs.  The top level directory contains `CONTRIBUTING.md`, `LICENSE.md`, `README.md`, and the directory with the code for this lab, `javacs-lab10`.

In the subdirectory `javacs-lab10/src/com/flatironschool/javacs` you'll find the source files for this lab:

*  `JedisMaker.java` contains example code for connecting to a Redis server and running a few Jedis methods.

*  `JedisIndex.java` contains starter code for this lab.

*  `JedisIndexTest.java` contains test code for `JedisIndex`.

*  `WikiFetcher.java` contains the code we saw in previous labs to read web pages and parse them using JSoup.

You'll also find these files, which are part of our solution to previous labs.

*  `Index.java` implements an index using Java data structures.

*  `TermCounter.java` represents a map from terms to their frequencies.

*  `WikiNodeIterable.java` iterates through the nodes in a DOM tree produced by JSoup.

Also, in `javacs-lab10`, you'll find the Ant build file `build.xml`.

The first step is to use Jedis to connect to your Redis server.  `RedisMaker.java` shows how to do this.  It reads information about your Redis server from a file, connects to it and logs in using your password, then returns a `Jedis` object you can use to perform Redis operations.

If you open `JedisMaker.java`, you should see something like this (but with more error checking):

```java
public class JedisMaker {

	public static Jedis make() {
		// assemble the directory name
		String slash = File.separator;
		String filename = System.getProperty("user.dir") + slash + 
				"src" + slash + "resources" + slash + "redis_url.txt";

		// read the contents of the file into a string
		StringBuilder sb = new StringBuilder();
		BufferedReader br = new BufferedReader(new FileReader(filename));
		while (true) {
			String line = br.readLine();
			if (line == null) break;
			sb.append(line);
		}
		br.close();
		
		// extract the components from the URI
		URI uri = new URI(sb.toString());
		String host = uri.getHost();
		int port = uri.getPort();
		String[] array = uri.getAuthority().split("[:@]");
		String auth = array[1];
		
		// connect to the server and authenticate
		Jedis jedis = new Jedis(host, port);
		jedis.auth(auth);
	
		return jedis;
	}
```

`JedisMaker` is a helper class that provides one static method, `make`, which creates a `Jedis` object.  Once this object is authenticated (by invoking `auth`), you can use it to communicate with your Redis database.

`JedisMaker` reads information about your Redis server from a file named `redis_url.txt`, which you should put in the directory `javacs-lab10/src/resources`:

*   Use a text editor to create end edit `javacs-lab10/src/resources/redis_url.txt`.

*   Paste in the URL of your server.  If you are using RedisToGo, the URL will look like this:

    redis://redistogo:1234567890feedfacebeefa1e1234567@dory.redistogo.com:10534   

Because this file contains the password for your Redis server, you should not put this file in a public repository.  To help you avoid doing that by accident, we have provided a `.gitignore` file with this lab that should make it harder (but not impossible) to put this file in your repo.

Now in `javacs-lab10`, run `ant build` to compile the source files and `ant JedisMaker` to run the example code in `main`:

```java
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
		System.out.println("frequency of word1: " + jedis.hget("myhash", "word2"));
		
		jedis.close();
	}
```

This example demonstrates the data types and methods you are most likely to use for this lab.  When you run it, the output should be:

    Got value: myvalue
    element2 is member: true
    element at index 1: element2
    frequency of word1: 2
    frequency of word2: 1

In the next section, we'll explain how the code works.


## Redis data types

Redis is basically a map from keys, which are Strings, to values, which can be one of several data types.  The most basic Redis data type is a String.  To add a String to the database, use `jedis.set`, which is similar to `Map.put`; the parameters are the new key and the corresponding value.  To look up a key and get its value, use `jedis.get`:

```java
        jedis.set("mykey", "myvalue");
        String value = jedis.get("mykey");
```

In this example, the key is `"mykey"` and the value is `"myvalue"`.

A Redis `Set` is similar to a Java `Set<String>`.  To add elements to a set, you have to choose a key to identify the set and then use `jedis.sadd`:

```java
        jedis.sadd("myset", "element1", "element2", "element3");
        boolean flag = jedis.sismember("myset", "element2");
```

You don't have to create the Set as a separate step.  If it doesn't exist, Redis creates it.  In this case, it creates a Set named `myset` that contains three elements.

The method `jedis.sismember` checks whether an element is in a Set.  Adding elements and checking membership are constant time operations.

A Redis `List` is similar to a Java `List<String>`.  The method `jedis.rpush` adds elements to the end (right side) of a `List`:

```java
        jedis.rpush("mylist", "element1", "element2", "element3");
        String element = jedis.lindex("mylist", 1);
```

Again, you don't have to create the data structure before you start adding elements.  This example creates a List named "mylist" that contains three elements.

The method `jedis.lindex` takes an integer index and returns the indicated element.  Adding and accessing elements are constant time operations.

Finally, a Redis `Hash` is similar to a Java `Map<String, String>`.  The method `jedis.hset` adds a new entry to the hash:

```java
        jedis.hset("myhash", "word1", Integer.toString(2));
        String value = jedis.hget("myhash", "word1");
```

This example creates a Hash named `myhash` that contains one entry.

The value it stores is an `Integer`, so we have to convert it to a `String`.  When we look up the value using `jedis.hget`, the result is a `String`.

Working with Redis hashes can be confusing, because we use a key to identify which hash we want, and then another key to identify a value in the hash.  In the context of Redis, the second key is called a "field", which might help keep things straight.  So a "key" like `myhash` identifies a particular hash, and then a "field" like `word1` identifies a value in the hash.

For many applications, the values in a Redis hash are integers, so Redis provides a few special methods, like `hincrby`, that treat the values as integers:

```java
        jedis.hincrBy("myhash", "word2", 1);
```

This method accesses `myhash`, gets the current value associated with `word2` (or 0 if it doesn't already exist), increments it by 1, and writes the result back to the hash.

Setting, getting, and incrementing entries in a hash are constant time operations.

You can [read more about Redis data types here](http://redis.io/topics/data-types).


## Making a Jedis-backed index

At this point you have the information you need to make a Web search index that stores results in a Redis database.

In `javacs-lab10`, run `ant build` to compile the source files and `ant test` to run `JedisIndexTest`.  It should fail, because you have some work to do!

`JedisIndexTest` tests these methods:

*   `JedisIndex`, which is the constructor that takes a `Jedis` object as a parameter.

*   `indexPage`, which adds a Web page to the index; it takes a String URL and a JSoup `Elements` object that contains the elements of the page that should be indexed.

*   `getCounts`, which takes a search term and returns a `Map<String, Integer>` that maps from each URL that contains the search term to the number of time it appears on that page.

Here's an example of how these methods are used:

```java
        WikiFetcher wf = new WikiFetcher();
		String url1 = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		Elements paragraphs = wf.readWikipedia(url1);

		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis);
		index.indexPage(url1, paragraphs);
		Map<String, Integer> map = index.getCounts("the");
```

If we look up `url1` in the result, `map`, we should get 339, which is the
number of times the word "the" appears on [the Java Wikipedia page](https://en.wikipedia.org/wiki/Java_(programming_language)) (that is, the version we saved).

If we index the same page again, the new results should replace the old ones.

You might want to start with a copy of `Index.java`, which contains our solution to the previous lab where we built an index using Java data structures.

One suggestion for translating data structures from Java to Redis: remember that each object in a Redis database is identified by a unique key, which is a string.  If you have two kinds of objects in the same database, you might want to add a prefix to the keys to distinguish between them.  For example, in our solution, we have two kinds of objects:

*  We define a `URLSet` to be a Redis `Set` that contains the URLs that contain a given search term.  The key for each `URLSet` starts with `"URLSet:"`, so to get the URLs that contain the word "the", we access the `Set` with the key `"URLSet:the"`.

* We define a `TermCounter` to be a Redis `Hash` that maps from each term that appears on a page to the number of times it appears.  The key for each TermCounter starts with `"TermCounter:"` and ends with the URL of the page we're looking up.

In our implementation, we have one `URLSet` for each term and one `TermCounter` for each indexed page.  We provide two helper methods, `urlSetKey` and `termCounterKey`, to assemble these keys.


## More suggestions if you want them

At this point you have all the information you need to do the lab, so you can get started if you are ready.  But we have a few suggestions you might want to read first:

*   For this lab we provide less guidance than we did in previous labs.  You will have to make some design decisions; in particular, you will have to figure out how to divide the problem into pieces that you can test one at a time, and then assemble the pieces into a complete solution.  If you try to write the whole thing at once, without testing smaller pieces, it might take a very long time to debug.

*   One of the challenges of working with persistent data is that it is persistent.  The structures stored in the database might change every time you run the program.  If you mess something up in the database, you will have to fix it or start over before you can proceed.  To help you keep things under control, we've provided methods called `deleteURLSets`, `deleteTermCounters`, and `deleteAllKeys`, which you can use to clean out the database and start fresh.  You can also use `printIndex` to print the contents of the index.

* Each time you invoke a `Jedis` method, your client sends a message to the server, then the server performs the action you requested and sends back a message.  If you perform many small operations, it will probably take a long time.  You can improve performance by grouping a series of operations into a `Transaction`.

For example, here's a simple version of `deleteAllKeys`:

```java
	public void deleteAllKeys() {
		Set<String> keys = jedis.keys("*");
		for (String key: keys) {
			jedis.del(key);
		}
	}
```

Each time you invoke `del` requirex a round-trip from the client to the server and back.  If the index contains more than a few pages, this method would take a long time to run.  We can speed it up with a `Transaction` object:

```java
	public void deleteAllKeys() {
		Set<String> keys = jedis.keys("*");
		Transaction t = jedis.multi();
		for (String key: keys) {
			t.del(key);
		}
		t.exec();
	}
```

`jedis.multi` returns a `Transaction` object, which provides all the methods of a `Jedis` object.  But when you invoke a method on a `Transaction`, it doesn't run the operation immediately, and it doesn't communicate with the server.  It saves up a batch of operations until you invoke `exec`.  Then it sends all of the saved operations to the server at the same time, which is usually much faster.


## A few design hints

Now you *really* have all the information you need; you should start working on the lab.  But if you get stuck, or if you really don't know how to get started, you can come back for a few more hints.

**Don't read the following until you have run the test code, tried out some basic Redis commands, and written a few methods in `JedisIndex.java`**.

Ok, if you are really stuck, here are some methods you might want to work on:

```java
	/**
	 * Adds a URL to the set associated with `term`.
	 */
	public void add(String term, TermCounter tc) {}

	/**
	 * Looks up a search term and returns a set of URLs.
	 */
	public Set<String> getURLs(String term) {}

	/**
	 * Returns the number of times the given term appears at the given URL.
	 */
	public Integer getCount(String url, String term) {}

	/**
	 * Pushes the contents of the TermCounter to Redis.
	 */
	public List<Object> pushTermCounterToRedis(TermCounter tc) {}
```

These are the methods we used in our solutions, but they are certainly not the only way to divide things up.  So please take these suggestions if they help, but ignore them if they don't.

For each method, consider writing the tests first.  When you figure out how to test a method, you often get ideas about how to write it.

Good luck!


## Resources

*  [JSON](https://en.wikipedia.org/wiki/JSON): Wikipedia.
*  [Database managment systems](https://en.wikipedia.org/wiki/Database): Wikipedia.
*  [Environment variables in Windows](http://www.computerhope.com/issues/ch000549.htm): tutorial.
*  [Redis data types](http://redis.io/topics/data-types): documentation.
