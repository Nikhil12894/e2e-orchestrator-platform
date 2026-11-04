function fn() {
  karate.configure('connectTimeout', 15000);
  karate.configure('readTimeout', 30000);
  karate.configure('ssl', true);
  karate.configure('logging', { pretty: true });
  karate.configure('retry', { count: 3, interval: 2000 });

  var env = karate.env || 'default';
  return {
    env: env
  };
}
