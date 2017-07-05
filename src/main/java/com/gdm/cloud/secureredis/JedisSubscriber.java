package com.gdm.cloud.secureredis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

class Subscriber extends JedisPubSub {

	public void onMessage(String channel, String message) {

		System.out.println(Thread.currentThread().getName() + " : Received Message on Channel :  " + channel + " Message : " + message);
	}


}


public class JedisSubscriber {


	public void subscribeAndListen() {

		final JedisPoolConfig poolConfig = new JedisPoolConfig();
		final JedisPool jedisPool = new JedisPool(poolConfig, "localhost", 6379, 0);
		final Jedis subscriberJedis = jedisPool.getResource();
		final Subscriber subscriber = new Subscriber();

		final String channel = "versa-oam";
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.currentThread().setName("Subscriber");
					System.out.println("Subscribing to channel. This thread will be blocked.");
					subscriberJedis.subscribe(subscriber, channel);
					
				} catch (Exception e) {
					System.out.println("Subscribing failed."+ e);
				}
			}
		}).start();


	}

}
